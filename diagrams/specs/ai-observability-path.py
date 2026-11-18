"""Short-lived pods push traces and logs when they exit, the runner owns the metrics, and the collector scrapes Kueue with a token."""

from generate_diagram import emit

emit(
    "ai-observability-path",
    1000,
    450,
    bands=[
        {"x": 660, "y": 60, "w": 320, "h": 350, "fill": "#fafafa", "label": "otel-lgtm"},
    ],
    nodes=[
        {"x": 40, "y": 90, "w": 240, "h": 80, "style": "box", "lines": ["batch-worker pods", "TRACEPARENT in the environment", "OTLP push at exit"]},
        {"x": 40, "y": 200, "w": 240, "h": 80, "style": "accent", "lines": ["batch-runner", "run and route spans", "step metrics"]},
        {"x": 40, "y": 310, "w": 240, "h": 80, "style": "kernel", "lines": ["Kueue controller", "metrics over HTTPS :8443", "bearer token required"]},
        {"x": 360, "y": 170, "w": 240, "h": 120, "style": "user", "lines": ["OpenTelemetry Collector", "OTLP receiver", "scrape of Kueue with the", "token, certificate not checked"]},
        {"x": 680, "y": 100, "w": 280, "h": 55, "style": "box", "lines": ["Tempo", "one trace per run"]},
        {"x": 680, "y": 175, "w": 280, "h": 55, "style": "box", "lines": ["Loki", "logs with trace ids"]},
        {"x": 680, "y": 250, "w": 280, "h": 55, "style": "box", "lines": ["Prometheus metrics", "runner and Kueue series"]},
        {"x": 680, "y": 330, "w": 280, "h": 60, "style": "ink", "lines": ["Grafana", "dashboard edp-batch"]},
    ],
    edges=[
        {"x1": 280, "y1": 130, "x2": 360, "y2": 200, "amber": True},
        {"x1": 280, "y1": 240, "x2": 360, "y2": 235, "amber": True},
        {"x1": 360, "y1": 265, "x2": 280, "y2": 340, "dashed": True, "label": "scrape", "lx": 0, "ly": 10},
        {"x1": 600, "y1": 195, "x2": 680, "y2": 127},
        {"x1": 600, "y1": 230, "x2": 680, "y2": 202},
        {"x1": 600, "y1": 265, "x2": 680, "y2": 277},
        {"x1": 820, "y1": 305, "x2": 820, "y2": 330},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Pods that live seconds cannot be scraped, so the runner reports for them"},
    ],
)
