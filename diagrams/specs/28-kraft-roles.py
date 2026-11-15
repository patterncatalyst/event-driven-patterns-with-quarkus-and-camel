"""The metadata quorum: one controller leads and votes, one broker observes, clients reach the broker."""

from generate_diagram import emit

emit(
    "28-kraft-roles",
    1000,
    420,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 300, "fill": "#fafafa", "label": "Namespace edp-kafka"},
    ],
    nodes=[
        {"x": 60, "y": 130, "w": 260, "h": 110, "style": "accent", "lines": ["edp-controller-<id>", "role: controller", "LeaderId, the only voter"]},
        {"x": 380, "y": 130, "w": 260, "h": 110, "style": "box", "lines": ["edp-broker-<id>", "role: broker", "the only observer"]},
        {"x": 720, "y": 130, "w": 230, "h": 110, "style": "ink", "lines": ["Service", "edp-kafka-bootstrap", "port 9092"]},
        {"x": 60, "y": 280, "w": 260, "h": 50, "style": "ghost", "lines": ["metadata log (cluster state)"]},
        {"x": 380, "y": 280, "w": 260, "h": 50, "style": "ghost", "lines": ["partitions on its own volume"]},
        {"x": 720, "y": 280, "w": 230, "h": 50, "style": "box", "lines": ["order-service, notification-service"]},
    ],
    edges=[
        {"x1": 380, "y1": 170, "x2": 320, "y2": 170, "amber": True, "label": "fetches metadata", "ly": -10},
        {"x1": 720, "y1": 185, "x2": 640, "y2": 185},
        {"x1": 190, "y1": 240, "x2": 190, "y2": 280, "dashed": True},
        {"x1": 510, "y1": 240, "x2": 510, "y2": 280, "dashed": True},
        {"x1": 835, "y1": 280, "x2": 835, "y2": 240},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A controller keeps the metadata, a broker keeps the partitions, clients reach only the broker"},
        {"x": 500, "y": 395, "anchor": "middle", "size": 13, "color": "#555555",
         "text": "One controller is a quorum of one: it tolerates no loss"},
    ],
)
