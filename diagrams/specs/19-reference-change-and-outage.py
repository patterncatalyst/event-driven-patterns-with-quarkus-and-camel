"""A timeline: the template changes at the owner, then on the topic; later the owner stops and returns."""

from generate_diagram import emit

emit(
    "19-reference-change-and-outage",
    980,
    520,
    bands=[
        {"x": 20, "y": 60, "w": 940, "h": 80, "fill": "#fafafa", "label": "Owner and topic"},
        {"x": 20, "y": 170, "w": 940, "h": 150, "fill": "#fff8ef", "label": "Camel Content Enricher"},
        {"x": 20, "y": 350, "w": 940, "h": 150, "fill": "#fff8ef", "label": "Kafka Streams left join"},
    ],
    nodes=[
        {"x": 40, "y": 92, "w": 170, "h": 40, "style": "ink", "lines": ["template changed at the owner"]},
        {"x": 250, "y": 92, "w": 190, "h": 40, "style": "box", "lines": ["new template on the topic"]},
        {"x": 560, "y": 92, "w": 170, "h": 40, "style": "ghost", "lines": ["owner stops"]},
        {"x": 770, "y": 92, "w": 170, "h": 40, "style": "box", "lines": ["owner returns"]},
        {"x": 40, "y": 205, "w": 170, "h": 55, "style": "accent", "lines": ["O-1903: new link", "the lookup sees it at once"]},
        {"x": 250, "y": 205, "w": 190, "h": 55, "style": "box", "lines": ["O-1904: new link"]},
        {"x": 560, "y": 205, "w": 170, "h": 55, "style": "ghost", "lines": ["O-1905 waits", "lag 1, nothing committed"]},
        {"x": 770, "y": 205, "w": 170, "h": 55, "style": "accent", "lines": ["O-1905 enriched", "lag 0, nothing lost"]},
        {"x": 40, "y": 385, "w": 170, "h": 55, "style": "accent", "lines": ["O-1903: old link", "the copy has not changed yet"]},
        {"x": 250, "y": 385, "w": 190, "h": 55, "style": "box", "lines": ["O-1904: new link", "O-1903 is not revised"]},
        {"x": 560, "y": 385, "w": 170, "h": 55, "style": "kernel", "lines": ["O-1905 enriched", "from the local copy"]},
        {"x": 770, "y": 385, "w": 170, "h": 55, "style": "box", "lines": ["carries on"]},
    ],
    edges=[
        {"x1": 210, "y1": 112, "x2": 250, "y2": 112},
        {"x1": 730, "y1": 112, "x2": 770, "y2": 112},
        {"x1": 125, "y1": 132, "x2": 125, "y2": 205, "amber": True},
        {"x1": 345, "y1": 132, "x2": 345, "y2": 205},
        {"x1": 645, "y1": 132, "x2": 645, "y2": 205, "dashed": True},
        {"x1": 855, "y1": 132, "x2": 855, "y2": 205},
        {"x1": 125, "y1": 320, "x2": 125, "y2": 385, "dashed": True},
        {"x1": 345, "y1": 320, "x2": 345, "y2": 385, "dashed": True},
        {"x1": 645, "y1": 320, "x2": 645, "y2": 385, "dashed": True},
    ],
    notes=[
        {"x": 490, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The lookup is current and depends on the owner; the join is as fresh as the topic and does not"},
    ],
)
