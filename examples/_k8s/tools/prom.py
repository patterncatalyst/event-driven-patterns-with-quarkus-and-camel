"""prom.py <prometheus-url> <promql> [value|sum|count]: one instant query against Prometheus, one token on stdout.

  value  (default) the value of a one-series result, or the number of series when there are several
  sum    the sum of the values of the result vector
  count  the number of series in the result vector (0 for an empty vector)

Exit 1 when value or sum finds no series, 2 when Prometheus reports an error, 3 when it cannot be reached.
Standard library only; run as `python3 -I`."""
import json
import sys
import urllib.parse
import urllib.request


def main() -> int:
    base, query = sys.argv[1], sys.argv[2]
    mode = sys.argv[3] if len(sys.argv) > 3 else "value"
    url = f"{base}/api/v1/query?{urllib.parse.urlencode({'query': query})}"
    try:
        with urllib.request.urlopen(url, timeout=10) as r:
            data = json.load(r)
    except OSError:
        return 3
    if data.get("status") != "success":
        return 2
    result = data["data"]["result"]
    if mode == "count":
        sys.stdout.write(f"{len(result)}\n")
        return 0
    if not result:
        return 1
    if mode == "sum":
        sys.stdout.write(f"{sum(float(s['value'][1]) for s in result):g}\n")
    else:
        sys.stdout.write(f"{result[0]['value'][1] if len(result) == 1 else len(result)}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
