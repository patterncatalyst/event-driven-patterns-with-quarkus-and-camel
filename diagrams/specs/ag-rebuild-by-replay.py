"""Redis is emptied, a new consumer group reads both topics from the earliest offset, and the version guard turns the replay into the newest state of every order whatever the arrival order."""

from generate_diagram import emit

emit(
    "ag-rebuild-by-replay",
    1000,
    470,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 150, "fill": "#fafafa", "label": "1. The cache is lost"},
        {"x": 20, "y": 230, "w": 960, "h": 210, "fill": "#fff8ef", "label": "2. A new group replays both topics from the start"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 250, "h": 70, "style": "kernel", "lines": ["Redis restarts", "no persistence: 0 keys"]},
        {"x": 370, "y": 100, "w": 250, "h": 70, "style": "box", "lines": ["order-service", "keeps accepting orders"]},
        {"x": 700, "y": 100, "w": 250, "h": 70, "style": "ghost", "lines": ["events during the outage", "are not applied"]},
        {"x": 40, "y": 280, "w": 230, "h": 70, "style": "ink", "lines": ["edp.orders.cancelled", "O-1 CANCELLED v2"]},
        {"x": 40, "y": 360, "w": 230, "h": 60, "style": "ink", "lines": ["edp.orders.placed", "O-1 PLACED v1, O-2 PLACED v1"]},
        {"x": 350, "y": 300, "w": 250, "h": 90, "style": "box", "lines": ["Camel route, group order-cache-2", "autoOffsetReset=earliest", "any arrival order"]},
        {"x": 680, "y": 300, "w": 270, "h": 90, "style": "accent", "lines": ["version guard", "v1 after v2 is ignored", "O-1 CANCELLED v2, O-2 PLACED v1"]},
    ],
    edges=[
        {"x1": 290, "y1": 135, "x2": 370, "y2": 135, "dashed": True},
        {"x1": 620, "y1": 135, "x2": 700, "y2": 135, "dashed": True},
        {"x1": 270, "y1": 315, "x2": 350, "y2": 335, "amber": True},
        {"x1": 270, "y1": 390, "x2": 350, "y2": 365, "amber": True},
        {"x1": 600, "y1": 345, "x2": 680, "y2": 345, "amber": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The topics hold the full state, so the cache is a rebuildable copy"},
    ],
)
