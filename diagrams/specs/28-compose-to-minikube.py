"""The three addresses that change between compose and Kubernetes; the variable names stay."""

from generate_diagram import emit

emit(
    "28-compose-to-minikube",
    1000,
    360,
    nodes=[
        {"x": 20, "y": 70, "w": 300, "h": 60, "style": "box", "lines": ["CAMEL_COMPONENT_KAFKA_BROKERS", "kafka:9094"]},
        {"x": 20, "y": 160, "w": 300, "h": 60, "style": "box", "lines": ["QUARKUS_DATASOURCE_JDBC_URL", "postgres:5432"]},
        {"x": 20, "y": 250, "w": 300, "h": 60, "style": "box", "lines": ["QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT", "lgtm:4318"]},
        {"x": 620, "y": 70, "w": 360, "h": 60, "style": "accent", "lines": ["edp-kafka-bootstrap.edp-kafka.svc:9092", "Service made by Strimzi"]},
        {"x": 620, "y": 160, "w": 360, "h": 60, "style": "accent", "lines": ["edp-pg-rw.edp.svc:5432", "Service made by CloudNativePG"]},
        {"x": 620, "y": 250, "w": 360, "h": 60, "style": "accent", "lines": ["lgtm.edp-lgtm.svc:4318", "Service of the LGTM Deployment"]},
    ],
    edges=[
        {"x1": 320, "y1": 100, "x2": 620, "y2": 100, "amber": True, "label": "compose to Kubernetes", "ly": -10},
        {"x1": 320, "y1": 190, "x2": 620, "y2": 190, "amber": True},
        {"x1": 320, "y1": 280, "x2": 620, "y2": 280, "amber": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The same variables, three new values; no image changes"},
    ],
)
