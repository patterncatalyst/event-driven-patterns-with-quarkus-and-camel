"""Capture, filter and compare the effective settings of the compose and the Kubernetes targets.

Run as `python3 -I configs.py <command> ...`. The key lists, the Kafka defaults and the allowlist of
expected differences live here and nowhere else.
"""

import json
import re
import sys
from pathlib import Path

# Broker keys: 13, from `kafka-configs.sh --describe --all` on one broker.
KAFKA_KEYS = [
    "auto.create.topics.enable",
    "default.replication.factor",
    "group.coordinator.rebalance.protocols",
    "log.retention.hours",
    "min.insync.replicas",
    "num.partitions",
    "offsets.topic.replication.factor",
    "share.coordinator.state.topic.min.isr",
    "share.coordinator.state.topic.num.partitions",
    "share.coordinator.state.topic.replication.factor",
    "transaction.state.log.min.isr",
    "transaction.state.log.num.partitions",
    "transaction.state.log.replication.factor",
]
# What a broker reports for a key nobody set (Apache Kafka 4.3 broker configs page).
KAFKA_DEFAULTS = {
    "auto.create.topics.enable": "true",
    "default.replication.factor": "1",
    "group.coordinator.rebalance.protocols": "classic,consumer,streams",
    "log.retention.hours": "168",
    "min.insync.replicas": "1",
    "num.partitions": "1",
    "offsets.topic.replication.factor": "3",
    "share.coordinator.state.topic.min.isr": "2",
    "share.coordinator.state.topic.num.partitions": "50",
    "share.coordinator.state.topic.replication.factor": "3",
    "transaction.state.log.min.isr": "2",
    "transaction.state.log.num.partitions": "50",
    "transaction.state.log.replication.factor": "3",
}
PG_KEYS = ["max_connections", "max_replication_slots", "max_wal_senders", "server_version", "wal_level"]
# What an instance reports for a parameter nobody set: CloudNativePG's global defaults (wal_level, max_replication_slots)
# and PostgreSQL's own (max_wal_senders, max_connections).
PG_DEFAULTS = {"max_connections": "100", "max_replication_slots": "32", "max_wal_senders": "10", "wal_level": "logical"}

# Expected differences, compose value then Kubernetes value. Each one is explained in the appendix.
ALLOWED = {
    "group.coordinator.rebalance.protocols": ("classic,consumer,streams,share", "classic,consumer,streams"),
    "share.coordinator.state.topic.min.isr": ("1", "2"),
    "share.coordinator.state.topic.replication.factor": ("1", "3"),
    "max_replication_slots": ("10", "32"),
}
# server_version carries the distribution's build suffix, so only major.minor is compared.
MAJOR_MINOR_KEYS = {"server_version"}


def read_pairs(path):
    out = {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if "=" in line:
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip()
    return out


def load(directory):
    pairs = {}
    for name in ("kafka.txt", "pg.txt", "version.txt"):
        p = Path(directory) / name
        if p.is_file():
            pairs.update(read_pairs(p))
    return pairs


def norm(key, value):
    if key in MAJOR_MINOR_KEYS:
        m = re.match(r"\d+\.\d+", value)
        return m.group(0) if m else value
    return value


def cmd_kafka():
    """stdin: kafka-configs.sh --describe --all. stdout: key=value for the key list, sorted."""
    found = {}
    for line in sys.stdin:
        m = re.match(r"\s+([\w.]+)=(.*?)(?: sensitive=.*)?$", line.rstrip("\n"))
        if m and m.group(1) in KAFKA_KEYS:
            found[m.group(1)] = m.group(2)
    for k in sorted(found):
        print(f"{k}={found[k]}")


def cmd_sql():
    keys = ", ".join(f"'{k}'" for k in PG_KEYS)
    print(f"SELECT format('%s=%s', k, current_setting(k)) FROM unnest(ARRAY[{keys}]) AS k ORDER BY k;")


def cmd_complete(kind, path):
    """Exit 0 only when the capture holds exactly the expected keys. An empty capture fails."""
    expected = set(KAFKA_KEYS if kind == "kafka" else PG_KEYS)
    got = read_pairs(path) if Path(path).is_file() else {}
    return 0 if set(got) == expected and all(got.values()) else 1


def cmd_get(path, key):
    got = read_pairs(path)
    if key not in got:
        return 1
    print(got[key])
    return 0


def env_pairs(compose_file):
    """KAFKA_X_Y=value lines of the compose file, as broker keys: x.y."""
    out = {}
    for m in re.finditer(r"^\s*-\s*KAFKA_([A-Z0-9_]+)=(.*)$", Path(compose_file).read_text(encoding="utf-8"), re.M):
        out[m.group(1).lower().replace("_", ".")] = m.group(2).strip()
    return out


def start_pairs(start_sh):
    """`-c name=value` arguments of the postgres command line."""
    return {m.group(1): m.group(2).strip("'\"") for m in re.finditer(r"-c\s+(\w+)=(\S+)", Path(start_sh).read_text(encoding="utf-8"))}


def resource_kafka(json_file):
    cfg = json.loads(Path(json_file).read_text(encoding="utf-8"))["spec"]["kafka"].get("config", {})
    return {k: (str(v).lower() if isinstance(v, bool) else str(v)) for k, v in cfg.items()}


def cmd_explicit(source, src_file, part, capture):
    """part=set: every key the source sets reports that value. part=unset: every other key reports its default."""
    got = read_pairs(capture)
    if source == "compose-kafka":
        explicit, keys, defaults = env_pairs(src_file), KAFKA_KEYS, KAFKA_DEFAULTS
    elif source == "kafka-resource":
        explicit, keys, defaults = resource_kafka(src_file), KAFKA_KEYS, KAFKA_DEFAULTS
    elif source == "start-sh":
        explicit, keys, defaults = start_pairs(src_file), [k for k in PG_KEYS if k != "server_version"], PG_DEFAULTS
    elif source == "cluster":
        params = json.loads(Path(src_file).read_text(encoding="utf-8"))["spec"].get("postgresql", {}).get("parameters", {})
        explicit, keys, defaults = {k: str(v) for k, v in params.items()}, [k for k in PG_KEYS if k != "server_version"], PG_DEFAULTS
    else:
        return 2
    chosen = [k for k in keys if k in explicit]
    others = [k for k in keys if k not in explicit]
    if source == "cluster" and part == "set":
        return 0 if not chosen else 1  # the Cluster sets none of the parameters the capture covers
    pairs = [(k, explicit[k]) for k in chosen] if part == "set" else [(k, defaults[k]) for k in others]
    if part == "set" and not pairs:
        return 1
    bad = [(k, v) for k, v in pairs if got.get(k) != v]
    for k, v in bad:
        print(f"{k}: reports {got.get(k)!r}, expected {v!r}", file=sys.stderr)
    return 0 if got and not bad else 1


def cmd_version(capture, image):
    """server_version major.minor equals the image tag's."""
    got = read_pairs(capture).get("server_version", "")
    m = re.search(r":(\d+\.\d+)-", image)
    return 0 if m and (got.startswith(m.group(1) + " ") or got == m.group(1)) else 1


def cmd_equal(key, compose_dir, k8s_dir):
    a, b = load(compose_dir), load(k8s_dir)
    if key not in a or key not in b or not a[key] or not b[key]:
        return 1
    return 0 if norm(key, a[key]) == norm(key, b[key]) else 1


def cmd_differs(key, compose_dir, k8s_dir):
    a, b = load(compose_dir), load(k8s_dir)
    want = ALLOWED[key]
    return 0 if (a.get(key), b.get(key)) == want else 1


def cmd_others(compose_dir, k8s_dir):
    a, b = load(compose_dir), load(k8s_dir)
    keys = set(KAFKA_KEYS) | set(PG_KEYS)
    if not (keys <= set(a) and keys <= set(b)):
        return 1
    diff = {k for k in keys if norm(k, a[k]) != norm(k, b[k])}
    for k in sorted(diff - set(ALLOWED)):
        print(f"unlisted difference: {k}: {a[k]!r} vs {b[k]!r}", file=sys.stderr)
    return 0 if diff == set(ALLOWED) else 1


def cmd_documented(doc):
    text = Path(doc).read_text(encoding="utf-8")
    return 0 if all(f"`{k}`" in text for k in ALLOWED) else 1


def cmd_keys(name):
    """kafka, pg, allowed (the allowlist) or equal (every other key, which must be equal on both targets)."""
    if name == "equal":
        keys = [k for k in KAFKA_KEYS + PG_KEYS if k not in ALLOWED] + ["kafka_version"]
    else:
        keys = {"kafka": KAFKA_KEYS, "pg": PG_KEYS, "allowed": sorted(ALLOWED)}[name]
    print("\n".join(keys))
    return 0


def main(argv):
    cmd, args = argv[1], argv[2:]
    if cmd == "kafka":
        cmd_kafka()
        return 0
    if cmd == "sql":
        cmd_sql()
        return 0
    table = {"complete": cmd_complete, "get": cmd_get, "explicit": cmd_explicit, "version": cmd_version,
             "equal": cmd_equal, "differs": cmd_differs, "others": cmd_others, "documented": cmd_documented,
             "keys": cmd_keys}
    return table[cmd](*args)


if __name__ == "__main__":
    sys.exit(main(sys.argv))
