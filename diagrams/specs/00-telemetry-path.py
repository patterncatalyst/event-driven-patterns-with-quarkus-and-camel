"""Where each signal from the app lands in LGTM and how to reach it in Grafana."""

from generate_diagram import emit

emit(
    "00-telemetry-path",
    900,
    380,
    bands=[
        {"x": 330, "y": 56, "w": 550, "h": 270, "fill": "#fafafa", "label": "otel-lgtm container"},
    ],
    nodes=[
        {"x": 25, "y": 150, "w": 210, "h": 90, "style": "accent",
         "lines": ["stack-check", "OpenTelemetry SDK", "service.name = stack-check"]},
        {"x": 355, "y": 150, "w": 150, "h": 90, "style": "box", "lines": ["OTLP receiver", "4318 http/protobuf"]},
        {"x": 700, "y": 80, "w": 160, "h": 56, "style": "box", "lines": ["Tempo", "traces"]},
        {"x": 700, "y": 172, "w": 160, "h": 56, "style": "box", "lines": ["Prometheus", "metrics"]},
        {"x": 700, "y": 264, "w": 160, "h": 46, "style": "box", "lines": ["Loki", "logs"]},
    ],
    edges=[
        {"x1": 235, "y1": 195, "x2": 355, "y2": 195, "amber": True, "label": "OTLP", "ly": -10},
        {"x1": 505, "y1": 175, "x2": 700, "y2": 108},
        {"x1": 505, "y1": 195, "x2": 700, "y2": 200},
        {"x1": 505, "y1": 215, "x2": 700, "y2": 287},
    ],
    notes=[
        {"x": 450, "y": 26, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Three signals, one endpoint"},
        {"x": 450, "y": 350, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "Grafana on port 3000 queries all three with no login: Explore, then Tempo, Prometheus or Loki."},
    ],
)
