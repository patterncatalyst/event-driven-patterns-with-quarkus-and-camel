#!/usr/bin/env python3
"""Polls Tempo until one trace holds every named span or service.

usage: python3 -I tempo.py <tempo-base-url> <seconds> <name>...

A name matches a span (`"name":"POST /orders"`) or a service or other string attribute
(`"stringValue":"notification-service"`) in the trace JSON. Prints the trace id and exits 0
when a trace holds all of them; exits 1 when the time runs out.
"""
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request


def get(url):
    try:
        with urllib.request.urlopen(url, timeout=10) as r:
            return r.read().decode()
    except (urllib.error.URLError, OSError):
        return ""


def trace_ids(base, names):
    queries = [{"limit": "50"}]
    queries += [{"tags": f"service.name={n}", "limit": "20"} for n in names if re.fullmatch(r"[a-z0-9-]+", n)]
    seen = []
    for q in queries:
        body = get(f"{base}/api/search?{urllib.parse.urlencode(q)}")
        try:
            traces = json.loads(body).get("traces", [])
        except ValueError:
            continue
        for t in traces:
            tid = t.get("traceID", "").zfill(32)
            if tid.strip("0") and tid not in seen:
                seen.append(tid)
    return seen


def holds(trace, name):
    return f'"name":"{name}"' in trace or f'"stringValue":"{name}"' in trace


def main():
    base, seconds, names = sys.argv[1].rstrip("/"), float(sys.argv[2]), sys.argv[3:]
    deadline = time.monotonic() + seconds
    while True:
        for tid in trace_ids(base, names):
            trace = get(f"{base}/api/traces/{tid}")
            compact = re.sub(r'"\s*:\s*"', '":"', trace)
            if trace and all(holds(compact, n) for n in names):
                print(tid)
                return 0
        if time.monotonic() >= deadline:
            return 1
        time.sleep(3)


if __name__ == "__main__":
    sys.exit(main())
