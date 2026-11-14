"""Reads Tempo, Loki and Grafana over their HTTP APIs for verify.sh. Run as: python3 -I tools/observe.py <command> <args>.

Exit status 0 means the condition holds. Commands that print, print one fact per line.
  trace-ids <order>            hex trace ids whose spans carry the attribute order.id = <order>
  order-in-service <order> <svc>   a span of that service carries order.id = <order>
  services <trace>             service names with at least one span in the trace
  has-span <trace> <svc> <name-prefix>      the trace has such a span
  links <trace>                 "<span name> -> <linked trace id>" for every span that links to another trace
  root-span <trace>            the name of the root span of the trace (no parent)
  log-lines <order>            "<service> <trace id>" for every Loki line with orderId = <order>
  log-services <trace>         service names of the Loki lines with trace_id = <trace>
  log-trace <service> <order>  the trace ids of that service's lines for the order
  grafana-links                the Loki trace_id derived field and the Tempo trace-to-logs link are provisioned
"""
import base64
import json
import sys
import urllib.parse
import urllib.request

TEMPO = "http://localhost:3200"
LOKI = "http://localhost:3100"
GRAFANA = "http://localhost:3000"


def out(text):
    """One fact per line on standard output; nothing at all for an empty answer, so a line count is a fact count."""
    if text:
        sys.stdout.write(f"{text}\n")


def get(url, headers=None):
    request = urllib.request.Request(url, headers=headers or {})
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.load(response)


def value(v):
    for key in ("stringValue", "intValue", "doubleValue", "boolValue"):
        if key in v:
            return v[key]
    return v


def hexid(raw):
    """Tempo returns ids as base64 in trace JSON and as hex in search results and headers."""
    if len(raw) in (16, 32) and all(c in "0123456789abcdef" for c in raw):
        return raw
    return base64.b64decode(raw).hex()


def spans(trace):
    out = []
    for rs in get(f"{TEMPO}/api/v2/traces/{trace}").get("trace", {}).get("resourceSpans", []):
        service = next((a["value"]["stringValue"] for a in rs["resource"].get("attributes", [])
                        if a["key"] == "service.name"), "?")
        for scope in rs.get("scopeSpans", []):
            for s in scope["spans"]:
                out.append({"service": service, "name": s["name"], "parent": s.get("parentSpanId", ""),
                            "links": [hexid(l["traceId"]) for l in s.get("links", [])]})
    return out


def trace_ids(order):
    query = '{ span.order.id = "%s" }' % order
    found = get(f"{TEMPO}/api/search?" + urllib.parse.urlencode({"q": query, "limit": 20}))
    return sorted({t["traceID"] for t in found.get("traces", [])})


def loki(query):
    url = f"{LOKI}/loki/api/v1/query_range?" + urllib.parse.urlencode({"query": query, "limit": 1000})
    rows = []
    for stream in get(url, {"X-Loki-Response-Encoding-Flags": "categorize-labels"})["data"]["result"]:
        service = stream["stream"].get("service_name", "?")
        for entry in stream["values"]:
            meta = entry[2].get("structuredMetadata", {}) if len(entry) > 2 else {}
            rows.append((service, meta.get("trace_id", ""), entry[1]))
    return rows


def main(argv):
    command, args = argv[0], argv[1:]
    if command == "trace-ids":
        out("\n".join(trace_ids(args[0])))
    elif command == "order-in-service":
        query = '{ resource.service.name = "%s" && span.order.id = "%s" }' % (args[1], args[0])
        found = get(f"{TEMPO}/api/search?" + urllib.parse.urlencode({"q": query, "limit": 5}))
        return 0 if found.get("traces") else 1
    elif command == "services":
        out("\n".join(sorted({s["service"] for s in spans(args[0])})))
    elif command == "has-span":
        return 0 if any(s["service"] == args[1] and s["name"].startswith(args[2]) for s in spans(args[0])) else 1
    elif command == "links":
        for s in spans(args[0]):
            for linked in s["links"]:
                out(f'{s["name"]} -> {linked}')
    elif command == "root-span":
        out("\n".join(s["name"] for s in spans(args[0]) if not s["parent"]))
    elif command == "log-lines":
        for service, trace, _ in loki('{service_name=~".+"} | orderId="%s"' % args[0]):
            out(f"{service} {trace}")
    elif command == "log-services":
        out("\n".join(sorted({r[0] for r in loki('{service_name=~".+"} | trace_id="%s"' % args[0])})))
    elif command == "log-trace":
        out("\n".join(sorted({r[1] for r in loki('{service_name="%s"} | orderId="%s"' % (args[0], args[1]))})))
    elif command == "grafana-links":
        derived = get(f"{GRAFANA}/api/datasources/uid/loki")["jsonData"].get("derivedFields", [])
        to_logs = get(f"{GRAFANA}/api/datasources/uid/tempo")["jsonData"].get("tracesToLogsV2", {})
        ok = any(d.get("name") == "trace_id" and d.get("datasourceUid") == "tempo" for d in derived) \
            and to_logs.get("datasourceUid") == "loki"
        return 0 if ok else 1
    else:
        out(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except Exception as error:  # a service that does not answer yet is a condition that does not hold yet
        sys.stderr.write(f"{error}\n")
        sys.exit(1)
