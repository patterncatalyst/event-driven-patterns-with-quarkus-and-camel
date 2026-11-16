"""HTTP load against order-service and what it left on the topic.

  http.py load <first-id-number> <seconds> <out-file>   4 threads, about 80 requests/s in all, a new connection each
  http.py report <out-file>                              requests, failed, 5xx and the time of the first and last request
  http.py once <out-file>...                             reads kafka-console-consumer output (key, tab, value) on stdin
                                                         and checks one event per 201 and none for a failed request
The out file has one line per request: <time> <order id> <status or error name>.
"""

import collections
import http.client
import json
import re
import sys
import threading
import time

PORT = 30080  # the NodePort published on 127.0.0.1


def load(first, secs, out):
    lock = threading.Lock()
    counter = [first]
    results = []
    end = time.time() + secs

    def worker():
        while time.time() < end:
            with lock:
                n = counter[0]
                counter[0] += 1
            oid = f"O-{n}"
            body = json.dumps({"id": oid, "customerId": "C-1", "itemSku": "SKU-1", "quantity": 1, "amount": 9.99})
            t = time.time()
            try:
                c = http.client.HTTPConnection("127.0.0.1", PORT, timeout=10)
                c.request("POST", "/orders", body, {"Content-Type": "application/json", "Connection": "close"})
                code = str(c.getresponse().status)
                c.close()
            except Exception as e:  # a reset, a refused connection or a timeout is a failed request
                code = type(e).__name__
            with lock:
                results.append((t, oid, code))
            time.sleep(max(0, 0.05 - (time.time() - t)))

    threads = [threading.Thread(target=worker) for _ in range(4)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    with open(out, "w", encoding="utf-8") as f:
        for t, oid, code in results:
            f.write(f"{t:.3f} {oid} {code}\n")


def out(line):
    sys.stdout.write(f"{line}\n")


def read(path):
    with open(path, encoding="utf-8") as f:
        return [line.split() for line in f if line.strip()]


def report(path):
    rows = read(path)
    failed = sum(1 for r in rows if r[2] != "201")
    five = sum(1 for r in rows if re.fullmatch(r"5\d\d", r[2]))
    times = [float(r[0]) for r in rows]
    out(f"requests={len(rows)} failed={failed} status5xx={five} first={min(times):.3f} last={max(times):.3f}")


def once(paths, text):
    published = collections.Counter(line.split("\t")[0] for line in text.splitlines() if line.startswith("O-"))
    missing = duplicate = for_failed = 0
    for path in paths:
        for _, oid, code in read(path):
            n = published.get(oid, 0)
            if code == "201":
                missing += n == 0
                duplicate += n > 1
            else:
                for_failed += n > 0
    out(f"missing={missing} duplicate={duplicate} published_for_failed={for_failed}")


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "load":
        load(int(sys.argv[2]), float(sys.argv[3]), sys.argv[4])
    elif cmd == "report":
        report(sys.argv[2])
    elif cmd == "once":
        once(sys.argv[2:], sys.stdin.read())
    else:
        sys.exit(f"unknown command {cmd}")
