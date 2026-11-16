"""Five signals, the question each answers, and the failure it shows that a flow dashboard does not."""

from generate_diagram import emit

rows = [
    ("Broker", "kafka_server_brokertopicmetrics_*", "Is this broker taking the traffic?"),
    ("Controller", "kafka_controller_kafkacontroller_*", "Is there one active controller?"),
    ("Operator", "strimzi_reconciliations_*", "Is the operator reconciling?"),
    ("Resource", "edp_kafkatopic_condition", "Can the operator reconcile this topic?"),
    ("Flow", "kafka_consumer_group_lag_ratio", "Is a consumer keeping up?"),
]
nodes = []
edges = []
for i, (who, metric, question) in enumerate(rows):
    y = 90 + i * 70
    nodes.append({"x": 40, "y": y, "w": 150, "h": 50, "style": "accent" if who != "Flow" else "ink", "lines": [who]})
    nodes.append({"x": 260, "y": y, "w": 330, "h": 50, "style": "box", "lines": [metric]})
    nodes.append({"x": 660, "y": y, "w": 300, "h": 50, "style": "ghost", "lines": [question]})
    edges.append({"x1": 190, "y1": y + 25, "x2": 260, "y2": y + 25, "amber": True})
    edges.append({"x1": 590, "y1": y + 25, "x2": 660, "y2": y + 25})

emit(
    "32-what-each-signal-answers",
    1000,
    470,
    bands=[],
    nodes=nodes,
    edges=edges,
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Each signal answers one question that the others cannot"},
        {"x": 150, "y": 70, "anchor": "middle", "size": 13, "color": "#555555", "text": "Source"},
        {"x": 425, "y": 70, "anchor": "middle", "size": 13, "color": "#555555", "text": "Metric family"},
        {"x": 810, "y": 70, "anchor": "middle", "size": 13, "color": "#555555", "text": "Question"},
        {"x": 500, "y": 455, "anchor": "middle", "size": 13, "color": "#555555",
         "text": "Lag comes from chapter 26's receiver; the rest from the reporter, the operator and kube-state-metrics"},
    ],
)
