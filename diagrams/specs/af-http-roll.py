"""A rolling update of a service under requests: a request in flight on the removed pod is reset, the client retries, and each accepted request publishes one event."""

from generate_diagram import emit

emit(
    "af-http-roll",
    1000,
    360,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 250, "fill": "#fafafa", "label": "order-service, two replicas, readiness probe, maxUnavailable 0"},
    ],
    nodes=[
        {"x": 40, "y": 120, "w": 150, "h": 80, "style": "user", "lines": ["HTTP client", "POST /orders", "about 80 per second"]},
        {"x": 250, "y": 120, "w": 170, "h": 80, "style": "kernel", "lines": ["Service", "NodePort 30080", "ready pods only"]},
        {"x": 500, "y": 90, "w": 200, "h": 60, "style": "box", "lines": ["pod A, ready", "answers 201"]},
        {"x": 500, "y": 190, "w": 200, "h": 60, "style": "ghost", "lines": ["pod B, being removed", "a request in flight is reset"]},
        {"x": 780, "y": 90, "w": 180, "h": 60, "style": "accent", "lines": ["edp.orders.placed", "one event per 201"]},
        {"x": 780, "y": 190, "w": 180, "h": 60, "style": "ghost", "lines": ["one event or none;", "the retry answers 201 or 409"]},
    ],
    edges=[
        {"x1": 190, "y1": 160, "x2": 250, "y2": 160},
        {"x1": 420, "y1": 150, "x2": 500, "y2": 120},
        {"x1": 420, "y1": 170, "x2": 500, "y2": 220, "dashed": True},
        {"x1": 700, "y1": 120, "x2": 780, "y2": 120, "amber": True},
        {"x1": 700, "y1": 220, "x2": 780, "y2": 220, "dashed": True},
    ],
    notes=[
        {"x": 40, "y": 225, "size": 12, "color": "#b8650a", "text": "A failed request is"},
        {"x": 40, "y": 241, "size": 12, "color": "#b8650a", "text": "retried by the client"},
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A removed pod can drop requests in flight; the client retries"},
        {"x": 500, "y": 335, "anchor": "middle", "size": 12, "color": "#555555",
         "text": "quarkus.shutdown.timeout and quarkus.shutdown.delay drain running requests and do not remove every failure"},
    ],
)
