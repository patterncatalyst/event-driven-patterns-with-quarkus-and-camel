"""One partition, one consumer: while O-7004 is retried, the record behind it waits."""

from generate_diagram import emit

emit(
    "13-retry-blocks-partition",
    960,
    460,
    bands=[
        {"x": 20, "y": 60, "w": 450, "h": 360, "fill": "#fafafa", "label": "edp.orders.placed, partition 0"},
        {"x": 500, "y": 60, "w": 440, "h": 360, "fill": "#fff8ef", "label": "inventory-service, one consumer"},
    ],
    nodes=[
        {"x": 50, "y": 100, "w": 390, "h": 50, "style": "box", "lines": ["offset 3: O-7003, quantity 0 (dead-lettered)"]},
        {"x": 50, "y": 170, "w": 390, "h": 70, "style": "accent", "lines": ["offset 4: O-7004 on SKU-14", "row held by another transaction"]},
        {"x": 50, "y": 260, "w": 390, "h": 70, "style": "ghost", "lines": ["offset 5: O-7005 on SKU-15", "nothing wrong with it, not read yet"]},
        {"x": 530, "y": 100, "w": 380, "h": 70, "style": "box", "lines": ["attempt 1, 2, 3 ... of O-7004", "each waits 2 s for the lock, then 1 s delay"]},
        {"x": 530, "y": 200, "w": 380, "h": 56, "style": "accent", "lines": ["lock released: O-7004 reserved", "offset 4 committed"]},
        {"x": 530, "y": 290, "w": 380, "h": 56, "style": "ink", "lines": ["O-7005 reserved", "waited for the whole retry"]},
    ],
    edges=[
        {"x1": 440, "y1": 205, "x2": 530, "y2": 135, "amber": True},
        {"x1": 720, "y1": 170, "x2": 720, "y2": 200},
        {"x1": 720, "y1": 256, "x2": 720, "y2": 290},
        {"x1": 440, "y1": 295, "x2": 530, "y2": 318, "dashed": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A retry in place blocks the partition behind it"},
    ],
)
