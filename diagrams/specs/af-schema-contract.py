"""Three steps: expand, move every consumer, then contract; the last step waits for evidence from every consumer."""

from generate_diagram import emit

emit(
    "af-schema-contract",
    1000,
    300,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 170, "fill": "#fafafa", "label": "Expand, migrate, contract"},
    ],
    nodes=[
        {"x": 40, "y": 105, "w": 270, "h": 90, "style": "box", "lines": ["1 Expand", "the producer sends orderId", "and orderRef"]},
        {"x": 365, "y": 105, "w": 270, "h": 90, "style": "box", "lines": ["2 Migrate", "each consumer is deployed to", "read orderRef"]},
        {"x": 690, "y": 105, "w": 270, "h": 90, "style": "accent", "lines": ["3 Contract", "the producer stops sending", "orderId"]},
    ],
    edges=[
        {"x1": 310, "y1": 150, "x2": 365, "y2": 150},
        {"x1": 635, "y1": 150, "x2": 690, "y2": 150, "amber": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The change that removes a field comes last"},
        {"x": 500, "y": 262, "anchor": "middle", "size": 12, "color": "#555555",
         "text": "Contract needs evidence that no consumer still reads orderId"},
    ],
)
