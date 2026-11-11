"""Classic eager rebalance revokes every partition when a member joins; the consumer protocol moves only the ones that change owner."""

from generate_diagram import emit

emit(
    "06-eager-vs-incremental",
    960,
    380,
    bands=[
        {"x": 20, "y": 50, "w": 920, "h": 125, "fill": "#fafafa", "label": "Classic protocol, RangeAssignor"},
        {"x": 20, "y": 205, "w": 920, "h": 125, "fill": "#fafafa", "label": "Consumer protocol (KIP-848)"},
    ],
    nodes=[
        {"x": 40, "y": 85, "w": 270, "h": 70, "style": "box", "lines": ["replica 1 owns all 9", "partitions and processes"]},
        {"x": 345, "y": 85, "w": 270, "h": 70, "style": "ghost", "lines": ["replica 2 joins: replica 1", "revokes all 9 and stops"]},
        {"x": 650, "y": 85, "w": 270, "h": 70, "style": "box", "lines": ["both rejoin and receive", "an assignment, then resume"]},
        {"x": 40, "y": 240, "w": 270, "h": 70, "style": "box", "lines": ["replica 1 owns all 9", "partitions and processes"]},
        {"x": 345, "y": 240, "w": 270, "h": 70, "style": "accent", "lines": ["replica 2 joins: replica 1 gives", "up only the partitions that move"]},
        {"x": 650, "y": 240, "w": 270, "h": 70, "style": "box", "lines": ["replica 1 keeps processing", "the partitions it still owns"]},
    ],
    edges=[
        {"x1": 310, "y1": 120, "x2": 345, "y2": 120, "amber": True},
        {"x1": 615, "y1": 120, "x2": 650, "y2": 120, "amber": True},
        {"x1": 310, "y1": 275, "x2": 345, "y2": 275, "amber": True},
        {"x1": 615, "y1": 275, "x2": 650, "y2": 275, "amber": True},
    ],
    notes=[
        {"x": 480, "y": 24, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A joining member stops the whole group under the classic protocol, and only the moved partitions under KIP-848"},
        {"x": 480, "y": 358, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "Classic with CooperativeStickyAssignor also revokes only the partitions that move."},
    ],
)
