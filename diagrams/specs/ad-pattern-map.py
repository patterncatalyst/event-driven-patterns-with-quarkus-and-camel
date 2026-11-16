"""The patterns of the book as boxes, one column per part, each box numbered with its chapter."""

from generate_diagram import emit

COLUMNS = [
    ("Getting started", [("Event Message", 1), ("Command Message", 1), ("Document Message", 1)]),
    ("Events between services", [
        ("Event Notification", 2), ("Event-carried state", 3), ("Claim Check", 3),
        ("Partitioned ordering", 4), ("Resequencer", 4), ("Competing Consumers", 7)]),
    ("Events out reliably", [
        ("Transactional Outbox", 8), ("Transaction Log Tailing", 9), ("Idempotent Consumer", 11),
        ("Transactional Client", 12), ("Dead Letter Channel", 13)]),
    ("Event sourcing", [
        ("Aggregate", 14), ("Snapshot", 15), ("CQRS", 16), ("Projection from history", 17),
        ("Upcaster", 18), ("Compatible evolution", 18)]),
    ("Processing streams", [
        ("Content Enricher", 19), ("Stream-stream join", 20), ("Windowed aggregation", 21),
        ("Saga", 22), ("Orchestrated saga", 23), ("Analyse, then decide", 24)]),
    ("Observing event flows", [
        ("Trace context in message", 25), ("Measure the flow", 26), ("Correlation identifier", 27)]),
    ("Kubernetes with Strimzi", [
        ("Operator-run Kafka", 28), ("Topics as resources", 29), ("Field ownership", 30),
        ("Restricted pods", 31), ("Metrics from resources", 32), ("Rebalance by proposal", 33),
        ("Replicated cluster", 34), ("Connectors as resources", 35)]),
]

STEP, W, X0, Y0, H, GAP = 190, 178, 15, 60, 40, 8
nodes = []
for i, (part, items) in enumerate(COLUMNS):
    x = X0 + i * STEP
    nodes.append({"x": x, "y": Y0, "w": W, "h": 48, "style": "ink", "lines": [part]})
    for j, (name, ch) in enumerate(items):
        nodes.append({"x": x, "y": Y0 + 48 + GAP + j * (H + GAP), "w": W, "h": H,
                      "style": "box",
                      "lines": [name, f"chapter {ch}"]})

emit(
    "ad-pattern-map",
    X0 * 2 + STEP * 7 - (STEP - W),
    Y0 + 48 + GAP + 8 * (H + GAP) + 10,
    nodes=nodes,
    edges=[],
    notes=[
        {"x": 660, "y": 26, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Each pattern sits in the part that teaches it; parts 0 to 5 run on compose, part 6 on minikube"},
    ],
)
