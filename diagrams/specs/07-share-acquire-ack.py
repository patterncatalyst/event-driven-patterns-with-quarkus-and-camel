"""The life of one record in a share group: acquire, lock, then accept, release, reject or let the lock expire."""

from generate_diagram import emit

emit(
    "07-share-acquire-ack",
    960,
    500,
    nodes=[
        {"x": 40, "y": 180, "w": 190, "h": 80, "style": "box", "lines": ["available", "in the share partition"]},
        {"x": 290, "y": 180, "w": 210, "h": 80, "style": "accent", "lines": ["acquired, locked", "delivery count + 1"]},
        {"x": 590, "y": 50, "w": 330, "h": 60, "style": "box", "lines": ["accept", "ack(): done, never delivered again"]},
        {"x": 590, "y": 140, "w": 330, "h": 60, "style": "box", "lines": ["release", "nack(release): available again, count kept"]},
        {"x": 590, "y": 230, "w": 330, "h": 60, "style": "box", "lines": ["reject", "nack(reject): archived at once"]},
        {"x": 590, "y": 320, "w": 330, "h": 60, "style": "ghost", "lines": ["lock expires", "no answer in time: available again"]},
        {"x": 290, "y": 410, "w": 450, "h": 56, "style": "ink", "lines": ["delivery count reaches the limit: archived", "a record can be delivered more than once"]},
    ],
    edges=[
        {"x1": 230, "y1": 220, "x2": 290, "y2": 220, "amber": True},
        {"x1": 500, "y1": 200, "x2": 590, "y2": 80},
        {"x1": 500, "y1": 210, "x2": 590, "y2": 170},
        {"x1": 500, "y1": 230, "x2": 590, "y2": 260},
        {"x1": 500, "y1": 250, "x2": 590, "y2": 350, "dashed": True},
        {"x1": 590, "y1": 170, "x2": 135, "y2": 180, "dashed": True},
        {"x1": 590, "y1": 375, "x2": 135, "y2": 260, "dashed": True},
        {"x1": 135, "y1": 260, "x2": 290, "y2": 430, "dashed": True},
    ],
    notes=[
        {"x": 480, "y": 24, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Acknowledgement is per record, and the lock bounds how long a worker may hold it"},
    ],
)
