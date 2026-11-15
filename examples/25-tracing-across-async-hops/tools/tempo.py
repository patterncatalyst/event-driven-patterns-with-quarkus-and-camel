"""Reads Tempo's HTTP API for verify.sh. Run as `python3 -I tools/tempo.py <command> ...`.

  search <traceql> [limit]       trace ids that match, one per line
  services <trace id>            service names that have spans in the trace, sorted, one line
  ancestry <trace id> <service> <span name>
                                 span names from the root down to the first span of that service and name
  parent <trace id> <span name>  name of the parent of the first span with that name
  links <trace id>               span name, linked trace id and linked span id of every span link
  span-id <trace id> <span name> id of the first span with that name
  complete <traceql> <count>     whether the query finds <count> traces that each cross order-service, connect and
                                 inventory-service; prints "<found> <complete>" and a NOTE with the hop through Connect
"""
import base64
import json
import os
import statistics
import sys
import urllib.parse
import urllib.request

BASE = os.environ.get("TEMPO", "http://localhost:3200")
CROSSED = {"order-service", "connect", "inventory-service"}


def say(text, stream=sys.stdout):
    """The helper's output: one result per line on stdout, notes on stderr."""
    stream.write(f"{text}\n")


def get(path, **query):
    url = BASE + path + ("?" + urllib.parse.urlencode(query) if query else "")
    with urllib.request.urlopen(url, timeout=20) as r:
        return json.load(r)


def hexid(value):
    if not value:
        return ""
    if all(c in "0123456789abcdef" for c in value.lower()) and len(value) in (16, 32):
        return value.lower()
    return base64.b64decode(value).hex()


def search(q, limit=100):
    return [t["traceID"] for t in get("/api/search", q=q, limit=limit).get("traces", [])]


def spans(trace_id):
    rows = []
    for rs in get("/api/v2/traces/" + trace_id).get("trace", {}).get("resourceSpans", []):
        service = ""
        for a in rs.get("resource", {}).get("attributes", []):
            if a["key"] == "service.name":
                service = a["value"].get("stringValue", "")
        for ss in rs.get("scopeSpans", []):
            for s in ss.get("spans", []):
                rows.append({
                    "service": service,
                    "name": s["name"],
                    "id": hexid(s["spanId"]),
                    "parent": hexid(s.get("parentSpanId", "")),
                    "start": int(s["startTimeUnixNano"]),
                    "end": int(s["endTimeUnixNano"]),
                    "links": [(hexid(link["traceId"]), hexid(link["spanId"])) for link in s.get("links", [])],
                })
    rows.sort(key=lambda r: r["start"])
    return rows


def first(rows, name, service=None):
    return next((r for r in rows if r["name"] == name and (service is None or r["service"] == service)), None)


def hop(rows):
    """From the end of the outbox write to the start of the consumer's span: the time spent in the log and the relay."""
    write, receive = first(rows, "outbox-write", "order-service"), first(rows, "edp.orders.placed receive")
    return (receive["start"] - write["end"]) / 1e6 if write and receive else None


def main(argv):
    cmd, args = argv[1], argv[2:]
    if cmd == "search":
        say("\n".join(search(args[0], int(args[1]) if len(args) > 1 else 100)))
    elif cmd == "services":
        say(" ".join(sorted({r["service"] for r in spans(args[0])})))
    elif cmd == "ancestry":
        rows = spans(args[0])
        by_id = {r["id"]: r for r in rows}
        node = next((r for r in rows if r["service"] == args[1] and r["name"] == args[2]), None)
        chain = []
        while node:
            chain.append(node["name"])
            node = by_id.get(node["parent"])
        say(" > ".join(reversed(chain)))
    elif cmd == "parent":
        rows = spans(args[0])
        node = first(rows, args[1])
        parent = next((r for r in rows if node and r["id"] == node["parent"]), None)
        say(parent["name"] if parent else "")
    elif cmd == "links":
        for r in spans(args[0]):
            for trace, span in r["links"]:
                say(f'{r["name"]}\t{trace}\t{span}')
    elif cmd == "span-id":
        node = first(spans(args[0]), args[1])
        say(node["id"] if node else "")
    elif cmd == "complete":
        ids = search(args[0], int(args[1]) + 20)
        hops, whole = [], 0
        for t in ids:
            rows = spans(t)
            if CROSSED <= {r["service"] for r in rows}:
                whole += 1
                if hop(rows) is not None:
                    hops.append(hop(rows))
        say(f"{len(ids)} {whole}")
        if hops:
            say(f"NOTE: the hop through Connect, from the end of outbox-write to the consumer span: median {statistics.median(hops):.0f} ms, "
                  f"range {min(hops):.0f} to {max(hops):.0f} ms over {len(hops)} traces", stream=sys.stderr)
        return 0 if len(ids) == int(args[1]) and whole == len(ids) else 1
    else:
        say(__doc__, stream=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
