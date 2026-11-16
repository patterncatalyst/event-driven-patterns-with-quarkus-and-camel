#!/usr/bin/env python3
"""Input and answer normalizer for verify.sh. Run as `python3 -I counts.py <command> ...`.

  input <base>                  the 14 ShipmentDelivered records of the scenario, one JSON per line
  late <base>                   one record for window 1, sent after window 4 opened
  tick <base>                   one record that advances event time past the end of window 4
  norm <kind> <lo> <hi>         read one engine's answer on stdin, print "carrier|window offset|deliveries" per window
                                that starts in [lo, hi) seconds after the base; kind is ks, flink or rw; base is $BASE

Event times are seconds after <base>, an epoch second on a minute boundary, so window 1 starts at offset 0.
"""
import json
import sys
from datetime import datetime, timezone

# (seconds after the base, carrier), in the order the records are produced
SCENARIO = [
    (5, "FASTLINE"), (20, "FASTLINE"), (30, "AIRCO"), (40, "FASTLINE"), (50, "AIRCO"),  # window 1
    (62, "AIRCO"),                                                                      # window 2 opens
    (55, "FASTLINE"),                                                                   # 7 s behind the newest event
    (70, "FASTLINE"), (80, "AIRCO"), (100, "AIRCO"), (110, "FASTLINE"),                 # window 2
    (125, "FASTLINE"), (140, "AIRCO"),                                                  # window 3
    (195, "FASTLINE"),                                                                  # window 4 opens
]


def iso(base: int, seconds: int) -> str:
    return datetime.fromtimestamp(base + seconds, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def record(order: int, carrier: str, base: int, seconds: int) -> str:
    return json.dumps({"orderId": f"O-{order}", "carrier": carrier, "deliveredAt": iso(base, seconds)},
                      separators=(",", ":"))


def epoch(text: str) -> int:
    return int(datetime.fromisoformat(text.strip().replace("Z", "+00:00")).timestamp())


def windows(kind: str, lines):
    """Yield (carrier, window start epoch, deliveries) from one engine's raw answer."""
    if kind == "ks":
        for line in lines:
            line = line.strip()
            if line:
                for w in json.loads(line):
                    yield w["carrier"], epoch(w["windowStart"]), int(w["count"])
    elif kind == "flink":  # key TAB value of an upsert-kafka topic; the last record of a key is its answer
        latest = {}
        for line in lines:
            if "\t" not in line:
                continue
            _, value = line.rstrip("\n").split("\t", 1)
            if value in ("", "null"):
                continue
            v = json.loads(value)
            latest[(v["carrier"], epoch(v["window_start"]))] = int(v["deliveries"])
        for (carrier, start), n in latest.items():
            yield carrier, start, n
    elif kind == "rw":  # psql -At -F'|': carrier|window_start|deliveries
        for line in lines:
            parts = line.strip().split("|")
            if len(parts) == 3:
                yield parts[0], epoch(parts[1]), int(parts[2])
    else:
        raise SystemExit(f"unknown kind {kind}")


def main(argv):
    cmd = argv[1]
    if cmd == "input":
        base = int(argv[2])
        for i, (seconds, carrier) in enumerate(SCENARIO, 1):
            print(record(3800 + i, carrier, base, seconds))
    elif cmd == "late":
        print(record(3899, "FASTLINE", int(argv[2]), 58))
    elif cmd == "tick":
        print(record(3815, "AIRCO", int(argv[2]), 265))
    elif cmd == "norm":
        kind, base, lo, hi = argv[2], int(argv[3]), int(argv[4]), int(argv[5])
        rows = sorted((c, s - base, n) for c, s, n in windows(kind, sys.stdin) if lo <= s - base < hi)
        for c, s, n in rows:
            print(f"{c}|{s}|{n}")
    else:
        raise SystemExit(f"unknown command {cmd}")


if __name__ == "__main__":
    main(sys.argv)
