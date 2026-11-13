"""Positions are assigned at insert, commits happen later: a checkpoint by position skips a late commit, a watermark on the transaction waits for it."""

from generate_diagram import emit

emit(
    "17-commit-order-gap",
    960,
    560,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 220, "fill": "#fafafa", "label": "Checkpoint by position"},
        {"x": 20, "y": 300, "w": 920, "h": 220, "fill": "#fff8ef", "label": "Checkpoint with the watermark"},
    ],
    nodes=[
        {"x": 50, "y": 105, "w": 190, "h": 66, "style": "box", "lines": ["A appends position 41", "transaction still open"]},
        {"x": 270, "y": 105, "w": 190, "h": 66, "style": "box", "lines": ["B appends position 42", "B commits first"]},
        {"x": 490, "y": 105, "w": 200, "h": 66, "style": "ghost", "lines": ["projector reads 42", "checkpoint = 42"]},
        {"x": 720, "y": 105, "w": 190, "h": 66, "style": "ink", "lines": ["A commits", "41 is behind the checkpoint"]},
        {"x": 490, "y": 195, "w": 420, "h": 56, "style": "accent", "lines": ["order A is never counted, and nothing reports it"]},
        {"x": 50, "y": 345, "w": 190, "h": 66, "style": "box", "lines": ["A appends position 41", "transaction still open"]},
        {"x": 270, "y": 345, "w": 190, "h": 66, "style": "box", "lines": ["B appends position 42", "B commits first"]},
        {"x": 490, "y": 345, "w": 200, "h": 66, "style": "ghost", "lines": ["oldest open transaction is A", "reads nothing past it"]},
        {"x": 720, "y": 345, "w": 190, "h": 66, "style": "ink", "lines": ["A commits", "reads 41 and 42"]},
        {"x": 490, "y": 435, "w": 420, "h": 56, "style": "accent", "lines": ["both counted, one batch later"]},
    ],
    edges=[
        {"x1": 240, "y1": 138, "x2": 270, "y2": 138},
        {"x1": 460, "y1": 138, "x2": 490, "y2": 138},
        {"x1": 690, "y1": 138, "x2": 720, "y2": 138},
        {"x1": 815, "y1": 171, "x2": 700, "y2": 195, "dashed": True},
        {"x1": 240, "y1": 378, "x2": 270, "y2": 378},
        {"x1": 460, "y1": 378, "x2": 490, "y2": 378},
        {"x1": 690, "y1": 378, "x2": 720, "y2": 378, "amber": True},
        {"x1": 815, "y1": 411, "x2": 700, "y2": 435, "amber": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A position is assigned when a row is inserted, a commit happens later, and the two orders can differ"},
    ],
)
