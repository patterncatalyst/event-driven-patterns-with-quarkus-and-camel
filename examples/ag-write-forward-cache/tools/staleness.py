"""Places orders one by one and measures, for each, the time from the answer to the first read that finds it in the cache.

Usage: staleness.py <base-url> <first-number> <count> <timeout-seconds>
Prints one line: n, min, p50, max (milliseconds) and how many orders were not yet readable at the first read.
Exits 1 when an order is still missing after the timeout.
"""
import json
import sys
import time
import urllib.error
import urllib.request


def request(url, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method="POST" if body is not None else "GET",
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code


def main(base, first, count, timeout):
    waits, missed = [], 0
    for number in range(first, first + count):
        order_id = f"O-{number}"
        body = {"id": order_id, "customerId": "CU-77", "itemSku": "SKU-42", "quantity": 1, "amount": 10.50}
        if request(f"{base}/orders", body) != 201:
            print(f"order {order_id} was not accepted")
            return 1
        started = time.monotonic()                      # the commit has been answered
        first_read = True
        while True:
            if request(f"{base}/orders/{order_id}") == 200:
                break
            if first_read:
                missed += 1
                first_read = False
            if time.monotonic() - started > timeout:
                print(f"order {order_id} is not in the cache after {timeout} s")
                return 1
            time.sleep(0.002)
        waits.append((time.monotonic() - started) * 1000)
    waits.sort()
    print(f"n={len(waits)} min={waits[0]:.0f} p50={waits[len(waits) // 2]:.0f} max={waits[-1]:.0f} ms, not yet readable at the first read: {missed}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), float(sys.argv[4])))
