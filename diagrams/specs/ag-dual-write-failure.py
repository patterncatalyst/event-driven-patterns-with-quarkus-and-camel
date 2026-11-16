"""A request that writes Redis and then fails to commit leaves an order in the cache that the database never held; the write-forward request leaves nothing behind."""

from generate_diagram import emit

emit(
    "ag-dual-write-failure",
    1000,
    500,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 190, "fill": "#fafafa", "label": "Dual write: Redis first, then the commit (demo)"},
        {"x": 20, "y": 280, "w": 960, "h": 190, "fill": "#fff8ef", "label": "Write-forward: the commit, then the event"},
    ],
    nodes=[
        {"x": 40, "y": 110, "w": 190, "h": 70, "style": "box", "lines": ["POST /orders", "O-4290"]},
        {"x": 290, "y": 110, "w": 190, "h": 70, "style": "box", "lines": ["1. write Redis", "order O-4290 cached"]},
        {"x": 540, "y": 110, "w": 190, "h": 70, "style": "ghost", "lines": ["2. commit PostgreSQL", "fails: 500, rolled back"]},
        {"x": 290, "y": 190, "w": 640, "h": 46, "style": "accent", "lines": ["GET /orders/O-4290 answers 200 from Redis; PostgreSQL has no such order"]},
        {"x": 40, "y": 330, "w": 190, "h": 70, "style": "box", "lines": ["POST /orders", "O-4290"]},
        {"x": 290, "y": 330, "w": 190, "h": 70, "style": "box", "lines": ["1. commit", "order row and outbox row"]},
        {"x": 540, "y": 330, "w": 190, "h": 70, "style": "ghost", "lines": ["fails: 500, rolled back", "both rows gone"]},
        {"x": 290, "y": 410, "w": 640, "h": 46, "style": "accent", "lines": ["no event is published, Redis is untouched, GET /orders/O-4290 answers 404"]},
    ],
    edges=[
        {"x1": 230, "y1": 145, "x2": 290, "y2": 145},
        {"x1": 480, "y1": 145, "x2": 540, "y2": 145, "dashed": True},
        {"x1": 230, "y1": 365, "x2": 290, "y2": 365},
        {"x1": 480, "y1": 365, "x2": 540, "y2": 365, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "No transaction spans PostgreSQL and Redis"},
    ],
)
