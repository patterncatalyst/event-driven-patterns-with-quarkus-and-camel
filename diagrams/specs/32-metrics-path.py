"""Four sources reach one Prometheus: the reporter in each Kafka pod, the operator, kube-state-metrics and the lag receiver."""

from generate_diagram import emit

emit(
    "32-metrics-path",
    1000,
    440,
    bands=[
        {"x": 20, "y": 60, "w": 330, "h": 330, "fill": "#fafafa", "label": "edp-kafka"},
        {"x": 400, "y": 60, "w": 580, "h": 330, "fill": "#fff8ef", "label": "edp-lgtm"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 290, "h": 55, "style": "box", "lines": ["Kafka pods", "Metrics Reporter on port 9404"]},
        {"x": 40, "y": 175, "w": 290, "h": 55, "style": "box", "lines": ["Strimzi cluster operator", "reconciliations on port 8080"]},
        {"x": 40, "y": 250, "w": 290, "h": 55, "style": "ghost", "lines": ["Kafka, KafkaNodePool, KafkaTopic", "status conditions"]},
        {"x": 40, "y": 325, "w": 290, "h": 55, "style": "box", "lines": ["Kafka bootstrap", "group lag, asked from the broker"]},
        {"x": 420, "y": 250, "w": 250, "h": 55, "style": "box", "lines": ["kube-state-metrics", "CustomResourceStateMetrics config"]},
        {"x": 420, "y": 130, "w": 250, "h": 70, "style": "accent", "lines": ["otel-lgtm collector", "prometheus receiver", "kafka_metrics receiver"]},
        {"x": 740, "y": 130, "w": 220, "h": 70, "style": "ink", "lines": ["Prometheus", "one query language"]},
        {"x": 740, "y": 290, "w": 220, "h": 55, "style": "ghost", "lines": ["Grafana", "dashboard edp-strimzi"]},
    ],
    edges=[
        {"x1": 330, "y1": 127, "x2": 420, "y2": 150, "amber": True, "label": "scrape", "ly": -8},
        {"x1": 330, "y1": 202, "x2": 420, "y2": 170, "amber": True},
        {"x1": 330, "y1": 352, "x2": 420, "y2": 190, "amber": True},
        {"x1": 330, "y1": 277, "x2": 420, "y2": 277, "dashed": True, "label": "watch", "ly": -8},
        {"x1": 545, "y1": 250, "x2": 545, "y2": 200, "amber": True, "label": "scrape", "lx": 30},
        {"x1": 670, "y1": 165, "x2": 740, "y2": 165, "amber": True, "label": "OTLP", "ly": -8},
        {"x1": 850, "y1": 200, "x2": 850, "y2": 290},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Pods, the operator and the custom resources report separately and land in one store"},
        {"x": 500, "y": 425, "anchor": "middle", "size": 13, "color": "#555555",
         "text": "Without the reporter the pod series go empty and the resource series stay"},
    ],
)
