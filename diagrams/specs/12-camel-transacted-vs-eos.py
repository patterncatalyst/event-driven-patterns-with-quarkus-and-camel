"""A transaction per exchange makes each output atomic; only a transaction that also holds the offsets makes a batch exactly once."""

from generate_diagram import emit

emit(
    "12-camel-transacted-vs-eos",
    960,
    470,
    bands=[
        {"x": 20, "y": 50, "w": 920, "h": 170, "fill": "#fafafa", "label": "SmallRye: one transaction per batch, offsets inside"},
        {"x": 20, "y": 250, "w": 920, "h": 190, "fill": "#fafafa", "label": "Camel transacted=true: one transaction per exchange, offsets outside"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 200, "h": 80, "style": "box", "lines": ["Poll a batch", "O-5101 to O-5110"]},
        {"x": 270, "y": 100, "w": 200, "h": 80, "style": "box", "lines": ["Outputs and offsets", "in one transaction"]},
        {"x": 500, "y": 100, "w": 200, "h": 80, "style": "ghost", "lines": ["Crash at O-5108", "transaction aborted"]},
        {"x": 730, "y": 100, "w": 190, "h": 80, "style": "accent", "lines": ["Restart", "10 outputs, each once"]},
        {"x": 40, "y": 310, "w": 200, "h": 80, "style": "box", "lines": ["Poll records", "O-6001 to O-6010"]},
        {"x": 270, "y": 310, "w": 200, "h": 80, "style": "box", "lines": ["Each output commits", "O-6001 to O-6007"]},
        {"x": 500, "y": 310, "w": 200, "h": 80, "style": "ghost", "lines": ["Crash at O-6008", "offset not yet committed"]},
        {"x": 730, "y": 310, "w": 190, "h": 80, "style": "accent", "lines": ["Restart", "O-6001 to O-6007 twice"]},
    ],
    edges=[
        {"x1": 240, "y1": 140, "x2": 270, "y2": 140},
        {"x1": 470, "y1": 140, "x2": 500, "y2": 140, "dashed": True},
        {"x1": 700, "y1": 140, "x2": 730, "y2": 140, "amber": True},
        {"x1": 240, "y1": 350, "x2": 270, "y2": 350},
        {"x1": 470, "y1": 350, "x2": 500, "y2": 350, "dashed": True},
        {"x1": 700, "y1": 350, "x2": 730, "y2": 350, "amber": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The same crash, with and without the offsets in the transaction"},
    ],
)
