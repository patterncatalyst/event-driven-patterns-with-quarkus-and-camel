#!/usr/bin/env python3
"""Bench input and its oracle for the pickup lists.

    bench.py gen        one ShipmentScheduled per line as "key|json" for the console producer
    bench.py oracle IN  the expected count per carrier and slot, computed from the input alone

1,000 shipments over four 15-minute slots from 11:00 UTC of the day in BENCH_DAY (an ISO date, yesterday in
verify.sh), both carriers, 60 percent FASTLINE. Event times are shuffled inside runs of 20 shipments, about 72 s of
event time, so the disorder stays well inside the 5-minute grace. The seed is fixed: the input is the same every run.
"""
import collections
import datetime as dt
import json
import os
import random
import sys

DAY = os.environ["BENCH_DAY"]
BASE = dt.datetime.fromisoformat(DAY + "T11:00:00+00:00")
SLOT_SECONDS = 15 * 60
SHIPMENTS = 1000
SEED = 21


def stamp(t):
    return t.strftime("%Y-%m-%dT%H:%M:%SZ")


def gen():
    rnd = random.Random(SEED)
    rows = []
    for i in range(SHIPMENTS):
        t = BASE + dt.timedelta(seconds=rnd.uniform(0, 3600 - 1))
        rows.append((t, "B%04d" % i, "FASTLINE" if rnd.random() < 0.6 else "AIRCO"))
    rows.sort()
    for s in range(0, len(rows), 20):
        chunk = rows[s:s + 20]
        rnd.shuffle(chunk)
        rows[s:s + 20] = chunk
    for t, sid, carrier in rows:
        shipment = {"id": sid, "orderId": "O-" + sid, "carrier": carrier, "trackingNumber": "TRK-" + sid,
                    "status": "SCHEDULED"}
        event = {"shipment": shipment, "occurredAt": stamp(t)}
        sys.stdout.write("O-%s|%s\n" % (sid, json.dumps(event, separators=(",", ":"))))


def oracle(path):
    counts = collections.Counter()
    for line in open(path):
        event = json.loads(line.split("|", 1)[1])
        t = dt.datetime.strptime(event["occurredAt"], "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=dt.timezone.utc)
        slot = BASE + dt.timedelta(seconds=SLOT_SECONDS * int((t - BASE).total_seconds() // SLOT_SECONDS))
        counts[(event["shipment"]["carrier"], stamp(slot))] += 1
    for (carrier, slot), n in sorted(counts.items()):
        sys.stdout.write("%s %s %d\n" % (carrier, slot, n))


if __name__ == "__main__":
    gen() if sys.argv[1] == "gen" else oracle(sys.argv[2])
