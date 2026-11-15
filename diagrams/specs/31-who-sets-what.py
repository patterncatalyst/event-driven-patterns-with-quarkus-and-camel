"""Who sets the security context of each workload, and how each one meets the restricted level."""

from generate_diagram import emit

emit(
    "31-who-sets-what",
    1000,
    420,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 300, "fill": "#fafafa", "label": "Workload, the one that sets its security context, and the outcome"},
    ],
    nodes=[
        {"x": 40, "y": 95, "w": 170, "h": 55, "style": "user", "lines": ["order-service", "notification-service"]},
        {"x": 40, "y": 165, "w": 170, "h": 55, "style": "user", "lines": ["Kafka broker", "and controller"]},
        {"x": 40, "y": 235, "w": 170, "h": 55, "style": "user", "lines": ["PostgreSQL", "edp-pg-1"]},
        {"x": 40, "y": 305, "w": 170, "h": 40, "style": "user", "lines": ["otel-lgtm"]},
        {"x": 330, "y": 95, "w": 280, "h": 55, "style": "box", "lines": ["Our Deployment", "four fields, read-only root, /tmp emptyDir"]},
        {"x": 330, "y": 165, "w": 280, "h": 55, "style": "box", "lines": ["Strimzi operator", "restricted pod security provider"]},
        {"x": 330, "y": 235, "w": 280, "h": 55, "style": "box", "lines": ["CloudNativePG operator", "context as published, uid 26"]},
        {"x": 330, "y": 305, "w": 280, "h": 40, "style": "ghost", "lines": ["Image as published, no fields set"]},
        {"x": 730, "y": 95, "w": 230, "h": 55, "style": "accent", "lines": ["edp: admitted"]},
        {"x": 730, "y": 165, "w": 230, "h": 55, "style": "accent", "lines": ["edp-kafka: admitted"]},
        {"x": 730, "y": 235, "w": 230, "h": 55, "style": "accent", "lines": ["edp: admitted"]},
        {"x": 730, "y": 305, "w": 230, "h": 40, "style": "ghost", "lines": ["edp-lgtm: unlabelled"]},
    ],
    edges=[
        {"x1": 210, "y1": 122, "x2": 330, "y2": 122},
        {"x1": 210, "y1": 192, "x2": 330, "y2": 192},
        {"x1": 210, "y1": 262, "x2": 330, "y2": 262},
        {"x1": 210, "y1": 325, "x2": 330, "y2": 325, "dashed": True},
        {"x1": 610, "y1": 122, "x2": 730, "y2": 122, "amber": True},
        {"x1": 610, "y1": 192, "x2": 730, "y2": 192, "amber": True},
        {"x1": 610, "y1": 262, "x2": 730, "y2": 262, "amber": True},
        {"x1": 610, "y1": 325, "x2": 730, "y2": 325, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Each workload gets its security context from exactly one place"},
        {"x": 500, "y": 395, "anchor": "middle", "size": 12, "color": "#555555",
         "text": "A dry-run label on edp-lgtm lists the fields its pod lacks; the namespace stays unlabelled"},
    ],
)
