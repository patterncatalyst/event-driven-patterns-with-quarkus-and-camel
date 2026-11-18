#!/usr/bin/env python3
"""Helpers for verify.sh: the expected counts from dataset.txt, topic and Kubernetes JSON checks. Standard library only.

usage: python3 -I batchcheck.py <command> <args>...

  expect <dataset> <W>                         CARRIER|scheduled|delivered|open for the window, sorted
  records <dataset> <W>                        the window's ShipmentScheduled plus ShipmentDelivered records
  markers <dataset>                            the marker orders (they sit outside every window)
  copartition <sched.txt> <deliv.txt>          both topics hold partitions 0 to 2 and place every key alike
  inwindow <dataset> <W> <from> <to> <sched> <deliv>
                                               the records stamped inside [from, to) are exactly the window's
  partition-records <dataset> <W> <sched> <deliv>
                                               partition|records of the window per partition
  output <out.txt> <run>                       CARRIER|scheduled|delivered|open of the run's records on the output topic
  joborder <run>                               (jobs JSON on stdin) each step's Job was created after the one before completed
  jobworkloads <run> <n>                       (jobs and workloads JSON) n Jobs with the queue label, each owning a Workload Admitted then Finished
  waiting <run-a> <run-b>                      (jobs, workloads, pods JSON) prints "<waiting> <admitted>" for the two reconcile steps
  overlap <run-a> <run-b>                      (pods JSON) prints "max=<m> demand=<d>" in millicores
  preempted <job>                              (jobs and workloads JSON) the Workload was evicted and preempted, the Job is suspended with no active pod
  admitted <job>                               (jobs and workloads JSON) the Job's Workload is Admitted
  trace <trace.json> <runner> <worker> <n>     the trace holds both services and n worker spans named after reconcile
  loki <result.json> <trace-id>                the Loki result holds a line of that trace
Topic files hold `CreateTime:<ms>	Partition:<p>	<key>	<value>` lines; the output topic file `<key>	<value>`.
"""
import json
import re
import sys
from collections import defaultdict
from datetime import datetime


def emit(value) -> None:
    """One result line on stdout, read by verify.sh."""
    sys.stdout.write(f"{value}\n")


def read_dataset(path):
    rows = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.strip() and not line.startswith("#"):
                group, order, carrier, delivered = line.split()
                rows.append((group, order, carrier, delivered == "1"))
    return rows


def window_rows(rows, w):
    return [r for r in rows if r[0] == w]


def topic_records(path):
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= 3 and parts[0].startswith("CreateTime:"):
                out.append((int(parts[0].split(":", 1)[1]), int(parts[1].split(":", 1)[1]), parts[2]))
    return out


def cmd_expect(ds, w):
    t = defaultdict(lambda: [0, 0])
    for _, _, carrier, delivered in window_rows(read_dataset(ds), w):
        t[carrier][0] += 1
        t[carrier][1] += 1 if delivered else 0
    for carrier in sorted(t):
        s, d = t[carrier]
        emit(f"{carrier}|{s}|{d}|{s - d}")
    return 0


def cmd_records(ds, w):
    rows = window_rows(read_dataset(ds), w)
    emit(len(rows) + sum(1 for r in rows if r[3]))
    return 0


def cmd_markers(ds):
    emit(sum(1 for r in read_dataset(ds) if r[0].startswith("M")))
    return 0


def cmd_copartition(sched, deliv):
    a, b = topic_records(sched), topic_records(deliv)
    pa = defaultdict(set)
    pb = defaultdict(set)
    for _, p, k in a:
        pa[k].add(p)
    for _, p, k in b:
        pb[k].add(p)
    if {p for _, p, _ in a} != {0, 1, 2} or {p for _, p, _ in b} != {0, 1, 2}:
        return 1
    if any(len(v) != 1 for v in list(pa.values()) + list(pb.values())):
        return 1
    return 0 if all(pa[k] == pb[k] for k in pb) and b else 1


def cmd_inwindow(ds, w, lo, hi, sched, deliv):
    rows = window_rows(read_dataset(ds), w)
    want_s = {r[1] for r in rows}
    want_d = {r[1] for r in rows if r[3]}
    lo, hi = int(lo), int(hi)
    got_s = [k for ts, _, k in topic_records(sched) if lo <= ts < hi]
    got_d = [k for ts, _, k in topic_records(deliv) if lo <= ts < hi]
    ok = sorted(got_s) == sorted(want_s) and sorted(got_d) == sorted(want_d)
    return 0 if ok else 1


def cmd_partition_records(ds, w, sched, deliv):
    rows = window_rows(read_dataset(ds), w)
    part = {k: p for _, p, k in topic_records(sched)}
    count = defaultdict(int)
    for _, order, _, delivered in rows:
        count[part[order]] += 2 if delivered else 1
    for p in range(3):
        emit(f"{p}|{count[p]}")
    return 0


def cmd_output(path, run):
    lines = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            key, _, value = line.rstrip("\n").partition("\t")
            if not key.startswith(f"{run}:"):
                continue
            v = json.loads(value)
            lines.append(f"{key.split(':', 1)[1]}|{v['scheduled']}|{v['delivered']}|{v['open']}")
    for line in sorted(lines):
        emit(line)
    return 0


def ts(s):
    return datetime.fromisoformat(s.replace("Z", "+00:00")) if s else None


def cond(obj, type_):
    for c in obj.get("status", {}).get("conditions", []):
        if c.get("type") == type_:
            return c
    return {}


def items(data, kind):
    return [i for i in data["items"] if i["kind"] == kind]


def workload_of(data, job):
    uid = job["metadata"]["uid"]
    for w in items(data, "Workload"):
        if any(o.get("uid") == uid for o in w["metadata"].get("ownerReferences", [])):
            return w
    return None


def step_job(data, run, step, attempt=1):
    name = f"rec-{run}-{step}-a{attempt}"
    for j in items(data, "Job"):
        if j["metadata"]["name"] == name:
            return j
    return None


def cmd_joborder(run):
    data = json.load(sys.stdin)
    jobs = {j["metadata"]["labels"].get("edp.batch/step"): j for j in data["items"]}
    try:
        plan, rec, agg = jobs["plan"], jobs["reconcile"], jobs["aggregate"]
    except KeyError:
        return 1
    done = lambda j: ts(j["status"].get("completionTime"))
    made = lambda j: ts(j["metadata"]["creationTimestamp"])
    if None in (done(plan), done(rec)):
        return 1
    return 0 if made(rec) >= done(plan) and made(agg) >= done(rec) else 1


def cmd_jobworkloads(run, n):
    data = json.load(sys.stdin)
    jobs = [j for j in items(data, "Job") if j["metadata"].get("labels", {}).get("edp.batch/run") == run]
    if len(jobs) != int(n):
        return 1
    for j in jobs:
        if j["metadata"]["labels"].get("kueue.x-k8s.io/queue-name") != "reconciliation":
            return 1
        w = workload_of(data, j)
        if w is None:
            return 1
        adm, fin = cond(w, "Admitted"), cond(w, "Finished")
        if adm.get("status") != "True" or fin.get("status") != "True":
            return 1
        if ts(adm["lastTransitionTime"]) > ts(fin["lastTransitionTime"]):
            return 1
    return 0


def pods_of(data, job_name):
    return [p for p in items(data, "Pod") if p["metadata"].get("labels", {}).get("job-name") == job_name]


def cmd_waiting(a, b):
    data = json.load(sys.stdin)
    waiting, admitted = [], []
    for run in (a, b):
        job = step_job(data, run, "reconcile")
        w = workload_of(data, job) if job else None
        if w is None:
            continue
        qr, adm = cond(w, "QuotaReserved"), cond(w, "Admitted")
        if (qr.get("status") == "False" and qr.get("reason") == "WaitingForQuota"
                and "insufficient unused quota for cpu in flavor default-flavor" in qr.get("message", "")
                and adm.get("status") == "False" and adm.get("reason") == "NoReservation"
                and job["spec"].get("suspend") is True and not pods_of(data, job["metadata"]["name"])):
            waiting.append(run)
        elif adm.get("status") == "True":
            admitted.append(run)
    if len(waiting) == 1 and len(admitted) == 1:
        emit(f"{waiting[0]} {admitted[0]}")
        return 0
    return 1


def cpu(s):
    return int(s[:-1]) if s.endswith("m") else int(float(s) * 1000)


def cmd_overlap(a, b):
    data = json.load(sys.stdin)
    events, demand = [], 0
    for p in items(data, "Pod"):
        name = p["metadata"].get("labels", {}).get("job-name", "")
        if not (name.startswith(f"rec-{a}-") or name.startswith(f"rec-{b}-")):
            continue
        m = sum(cpu(c.get("resources", {}).get("requests", {}).get("cpu", "0")) for c in p["spec"]["containers"])
        st = p["status"].get("containerStatuses", [{}])[0].get("state", {}).get("terminated")
        if not st:
            continue
        if "-reconcile-" in name:
            demand += m
        events.append((ts(st["startedAt"]), 1, m))
        events.append((ts(st["finishedAt"]), 0, m))
    events.sort(key=lambda e: (e[0], e[1]))   # at one instant, ends sort before starts
    cur = top = 0
    for _, start, m in events:
        cur += m if start else -m
        top = max(top, cur)
    emit(f"max={top} demand={demand}")
    return 0


def cmd_preempted(job):
    data = json.load(sys.stdin)
    j = next((x for x in items(data, "Job") if x["metadata"]["name"] == job), None)
    w = workload_of(data, j) if j else None
    if w is None:
        return 1
    ev, pr = cond(w, "Evicted"), cond(w, "Preempted")
    ok = (ev.get("status") == "True" and ev.get("reason") == "Preempted"
          and pr.get("status") == "True" and pr.get("reason") == "InClusterQueue"
          and j["spec"].get("suspend") is True and not j["status"].get("active"))
    return 0 if ok else 1


def cmd_admitted(job):
    data = json.load(sys.stdin)
    j = next((x for x in items(data, "Job") if x["metadata"]["name"] == job), None)
    w = workload_of(data, j) if j else None
    return 0 if w is not None and cond(w, "Admitted").get("status") == "True" else 1


def cmd_trace(path, runner, worker, n):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    batches = data.get("batches") or data.get("resourceSpans") or data.get("trace", {}).get("resourceSpans") or []
    services, reconcile = set(), 0
    for b in batches:
        svc = next((a["value"].get("stringValue") for a in b.get("resource", {}).get("attributes", [])
                    if a["key"] == "service.name"), None)
        services.add(svc)
        for scope in b.get("scopeSpans") or b.get("instrumentationLibrarySpans") or []:
            for s in scope.get("spans", []):
                if svc == worker and "reconcile" in s.get("name", "").lower():
                    reconcile += 1
    return 0 if {runner, worker} <= services and reconcile >= int(n) else 1


def cmd_loki(path, tid):
    with open(path, encoding="utf-8") as f:
        text = f.read()
    data = json.loads(text)
    return 0 if data.get("data", {}).get("result") and re.search(re.escape(tid), text) else 1


COMMANDS = {
    "expect": cmd_expect, "records": cmd_records, "markers": cmd_markers, "copartition": cmd_copartition,
    "inwindow": cmd_inwindow, "partition-records": cmd_partition_records, "output": cmd_output,
    "joborder": cmd_joborder, "jobworkloads": cmd_jobworkloads, "waiting": cmd_waiting, "overlap": cmd_overlap,
    "preempted": cmd_preempted, "admitted": cmd_admitted, "trace": cmd_trace, "loki": cmd_loki,
}

if __name__ == "__main__":
    if len(sys.argv) < 2 or sys.argv[1] not in COMMANDS:
        sys.stderr.write(__doc__)
        sys.exit(2)
    try:
        sys.exit(COMMANDS[sys.argv[1]](*sys.argv[2:]))
    except (KeyError, ValueError, TypeError, OSError, json.JSONDecodeError):
        sys.exit(1)
