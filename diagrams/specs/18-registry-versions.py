"""The registry checks every new schema version against the rule: v3 is refused under BACKWARD_TRANSITIVE and accepted under BACKWARD."""

from generate_diagram import emit

emit(
    "18-registry-versions",
    960,
    560,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 130, "fill": "#fafafa", "label": "Artifact edp.orders.placed-value in Apicurio Registry"},
        {"x": 20, "y": 215, "w": 450, "h": 270, "fill": "#fff8ef", "label": "Rule BACKWARD_TRANSITIVE"},
        {"x": 490, "y": 215, "w": 450, "h": 270, "fill": "#fafafa", "label": "Rule BACKWARD"},
    ],
    nodes=[
        {"x": 45, "y": 100, "w": 250, "h": 70, "style": "ink", "lines": ["version 1", "no shippingPriority"]},
        {"x": 355, "y": 100, "w": 250, "h": 70, "style": "ink", "lines": ["version 2", "shippingPriority, default STANDARD"]},
        {"x": 665, "y": 100, "w": 250, "h": 70, "style": "ghost", "lines": ["v3 candidate", "shippingPriority, no default"]},
        {"x": 45, "y": 255, "w": 400, "h": 70, "style": "box", "lines": ["v3 against version 2", "compatible: the field is present"]},
        {"x": 45, "y": 345, "w": 400, "h": 70, "style": "accent", "lines": ["v3 against version 1", "incompatible: nothing fills the field"]},
        {"x": 45, "y": 425, "w": 400, "h": 50, "style": "ghost", "lines": ["refused, HTTP 400, no new version"]},
        {"x": 515, "y": 255, "w": 400, "h": 70, "style": "box", "lines": ["v3 against version 2 only", "compatible"]},
        {"x": 515, "y": 345, "w": 400, "h": 70, "style": "accent", "lines": ["accepted as version 3", "version 1 is never consulted"]},
        {"x": 515, "y": 425, "w": 400, "h": 50, "style": "ghost", "lines": ["a v3 reader cannot read the v1 records on the topic"]},
    ],
    edges=[
        {"x1": 295, "y1": 135, "x2": 355, "y2": 135},
        {"x1": 605, "y1": 135, "x2": 665, "y2": 135, "dashed": True},
        {"x1": 245, "y1": 325, "x2": 245, "y2": 345},
        {"x1": 245, "y1": 415, "x2": 245, "y2": 425, "amber": True},
        {"x1": 715, "y1": 325, "x2": 715, "y2": 345},
        {"x1": 715, "y1": 415, "x2": 715, "y2": 425, "amber": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The rule belongs to the registry, and the rule decides which versions can exist"},
        {"x": 480, "y": 530, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "The v2 reader resolves v1 records with the default; a reader without the default cannot."},
    ],
)
