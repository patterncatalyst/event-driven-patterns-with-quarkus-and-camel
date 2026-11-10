"""The compose stack: services, host ports, the app's Kafka round trip and its telemetry path."""

from generate_diagram import emit

emit(
    "00-compose-stack",
    910,
    440,
    bands=[
        {"x": 15, "y": 56, "w": 880, "h": 330, "fill": "#fafafa", "label": "Docker network edp"},
    ],
    nodes=[
        {"x": 25, "y": 100, "w": 130, "h": 64, "style": "user", "lines": ["curl", "verify.sh"]},
        {"x": 215, "y": 100, "w": 180, "h": 64, "style": "accent",
         "lines": ["stack-check", "Quarkus + Camel", "8080"]},
        {"x": 540, "y": 100, "w": 170, "h": 64, "style": "box", "lines": ["Kafka (KRaft)", "9092 host, kafka:9094"]},
        {"x": 25, "y": 220, "w": 130, "h": 64, "style": "sub", "lines": ["Apicurio Registry", "8081, health 19000"]},
        {"x": 540, "y": 220, "w": 170, "h": 64, "style": "box", "lines": ["Kafka Connect", "Debezium, 8083"]},
        {"x": 750, "y": 220, "w": 130, "h": 64, "style": "box", "lines": ["PostgreSQL", "5432, logical WAL"]},
        {"x": 25, "y": 312, "w": 855, "h": 62, "style": "ink",
         "lines": ["Grafana LGTM (otel-lgtm)",
                   "OTLP 4318 and 4317, Grafana 3000, Tempo 3200, Prometheus 19090, Loki 3100"]},
    ],
    edges=[
        {"x1": 155, "y1": 132, "x2": 215, "y2": 132, "label": "POST", "ly": -10},
        {"x1": 395, "y1": 132, "x2": 540, "y2": 132, "bidir": True, "amber": True,
         "label": "consignments", "ly": -10},
        {"x1": 625, "y1": 220, "x2": 625, "y2": 164},
        {"x1": 750, "y1": 252, "x2": 710, "y2": 252},
        {"x1": 305, "y1": 164, "x2": 305, "y2": 312, "amber": True, "label": "traces, metrics, logs (OTLP)",
         "lx": 100, "ly": 0},
    ],
    notes=[
        {"x": 455, "y": 26, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "One compose project runs every service the book needs"},
        {"x": 455, "y": 412, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "The app publishes to Kafka and consumes the same topic; everything it emits lands in LGTM."},
    ],
)
