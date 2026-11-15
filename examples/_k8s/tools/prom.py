"""prom.py <prometheus-url> <promql>: print the value of a one-series result, or the number of series when there are
several. Exit 1 when the query returns no series. Standard library only; run as `python3 -I`."""
import json
import sys
import urllib.parse
import urllib.request


def main() -> int:
    base, query = sys.argv[1], sys.argv[2]
    url = f"{base}/api/v1/query?{urllib.parse.urlencode({'query': query})}"
    try:
        with urllib.request.urlopen(url, timeout=10) as r:
            data = json.load(r)
    except OSError:
        return 3
    if data.get("status") != "success":
        return 2
    result = data["data"]["result"]
    if not result:
        return 1
    sys.stdout.write(f"{result[0]['value'][1] if len(result) == 1 else len(result)}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
