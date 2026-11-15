"""Runs one instant query against Prometheus and prints a single token.

  python3 -I promq.py <prometheus-url> <sum|count|none-if-empty> <promql>

  sum    the sum of the values of the result vector, or "none" when the vector is empty
  count  the number of series in the result vector
"""
import json
import sys
import urllib.parse
import urllib.request

base, mode, query = sys.argv[1:4]
url = f"{base}/api/v1/query?" + urllib.parse.urlencode({"query": query})
with urllib.request.urlopen(url, timeout=10) as resp:
    result = json.load(resp)["data"]["result"]
if mode == "count":
    sys.stdout.write(f"{len(result)}\n")
elif not result:
    sys.stdout.write("none\n")
else:
    sys.stdout.write(f"{sum(float(r['value'][1]) for r in result):g}\n")
