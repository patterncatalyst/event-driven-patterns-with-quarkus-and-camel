"""One ShipmentScheduled enriched two ways: a Camel Content Enricher doing a lookup per record against the owner, and a Kafka Streams table join against a local copy of the owner's topic."""

from generate_diagram import emit

emit(
    "19-enricher-vs-table-join",
    980,
    560,
    bands=[
        {"x": 20, "y": 60, "w": 940, "h": 120, "fill": "#fafafa", "label": "shipping-service, the owner of carrier reference data"},
        {"x": 20, "y": 210, "w": 460, "h": 310, "fill": "#fff8ef", "label": "notification-service: Camel Content Enricher"},
        {"x": 500, "y": 210, "w": 460, "h": 310, "fill": "#fafafa", "label": "notification-service: Kafka Streams table join"},
    ],
    nodes=[
        {"x": 400, "y": 100, "w": 180, "h": 60, "style": "ink", "lines": ["shipping.carrier table", "mode, tracking URL template"]},
        {"x": 150, "y": 100, "w": 200, "h": 60, "style": "box", "lines": ["GET /carriers/{carrier}", "the owner's current row"]},
        {"x": 630, "y": 100, "w": 200, "h": 60, "style": "box", "lines": ["edp.shipping.carriers", "compacted, one per carrier"]},
        {"x": 630, "y": 325, "w": 200, "h": 95, "style": "accent", "lines": ["leftJoin(GlobalKTable carriers)", "local store on every instance, no network call"]},
    ],
    icons=[
        {"x": 150, "y": 230, "w": 200, "h": 85, "kind": "message", "label": "ShipmentScheduled, key = order id"},
        {"x": 150, "y": 325, "w": 200, "h": 95, "kind": "enricher", "label": "enrich: one HTTP call per record"},
        {"x": 150, "y": 430, "w": 200, "h": 85, "kind": "message", "label": "edp.shipping.scheduled-enriched-camel"},
        {"x": 630, "y": 230, "w": 200, "h": 85, "kind": "message", "label": "ShipmentScheduled, key = order id"},
        {"x": 630, "y": 430, "w": 200, "h": 85, "kind": "message", "label": "edp.shipping.scheduled-enriched"},
    ],
    edges=[
        {"x1": 400, "y1": 130, "x2": 350, "y2": 130, "dashed": True},
        {"x1": 580, "y1": 130, "x2": 630, "y2": 130, "dashed": True},
        {"x1": 250, "y1": 160, "x2": 250, "y2": 230, "amber": True},
        {"x1": 730, "y1": 160, "x2": 730, "y2": 230, "amber": True},
        {"x1": 250, "y1": 315, "x2": 250, "y2": 325},
        {"x1": 250, "y1": 420, "x2": 250, "y2": 430},
        {"x1": 730, "y1": 315, "x2": 730, "y2": 325},
        {"x1": 730, "y1": 420, "x2": 730, "y2": 430},
    ],
    notes=[
        {"x": 490, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The same merge, from two places: the owner's answer now, or the owner's last published record"},
        {"x": 490, "y": 545, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "One service needs only one of the two forms."},
    ],
)
