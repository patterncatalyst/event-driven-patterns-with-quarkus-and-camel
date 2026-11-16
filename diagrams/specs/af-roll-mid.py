"""Mid rollout: a fourth pod joins, the group rebalances, an old pod leaves, and records after the last commit move with their partition."""

from generate_diagram import emit

emit(
    "af-roll-mid",
    1000,
    360,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 250, "fill": "#fff8ef", "label": "During: each start and each stop is a membership change"},
    ],
    nodes=[
        {"x": 40, "y": 130, "w": 170, "h": 100, "style": "kernel", "lines": ["edp.orders.placed", "group notify-roll", "committed offsets"]},
        {"x": 290, "y": 90, "w": 220, "h": 46, "style": "box", "lines": ["revision 1 pod", "keeps its partition"]},
        {"x": 290, "y": 148, "w": 220, "h": 46, "style": "box", "lines": ["revision 1 pod", "keeps its partition"]},
        {"x": 290, "y": 206, "w": 220, "h": 46, "style": "ghost", "lines": ["revision 1 pod", "leaves after the new pod is ready"]},
        {"x": 290, "y": 258, "w": 220, "h": 46, "style": "user", "lines": ["revision 2 pod", "joins the group"]},
        {"x": 640, "y": 130, "w": 320, "h": 100, "style": "accent", "lines": ["Rebalance", "a partition moves to another member", "records read but not committed are delivered again"]},
    ],
    edges=[
        {"x1": 210, "y1": 170, "x2": 290, "y2": 113},
        {"x1": 210, "y1": 180, "x2": 290, "y2": 171},
        {"x1": 210, "y1": 190, "x2": 290, "y2": 229, "dashed": True},
        {"x1": 210, "y1": 200, "x2": 290, "y2": 281},
        {"x1": 510, "y1": 281, "x2": 640, "y2": 205, "amber": True},
        {"x1": 510, "y1": 229, "x2": 640, "y2": 185, "amber": True, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Every membership change moves partitions"},
        {"x": 500, "y": 338, "anchor": "middle", "size": 12, "color": "#555555",
         "text": "The consumer protocol lets the group coordinator drive the move (KIP-848); neither protocol guarantees there is no repeat"},
    ],
)
