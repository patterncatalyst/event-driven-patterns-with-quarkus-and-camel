"""One ShipmentScheduled enriched two ways: a lookup per record against the owner, and a join against a local copy of the owner's topic."""

from generate_diagram import emit

emit(
    "19-enricher-vs-table-join",
    980,
    560,
    bands=[
        {"x": 20, "y": 60, "w": 940, "h": 120, "fill": "#fafafa", "label": "shipping-service, the owner of carrier reference data"},
        {"x": 20, "y": 210, "w": 460, "h": 310, "fill": "#fff8ef", "label": "notification-service: Camel Content Enricher"},
        {"x": 500, "y": 210, "w": 460, "h": 310, "fill": "#fff8ef", "label": "notification-service: Kafka Streams left join"},
    ],
    nodes=[
        {"x": 400, "y": 100, "w": 180, "h": 60, "style": "ink", "lines": ["shipping.carrier table", "mode, tracking URL template"]},
        {"x": 140, "y": 100, "w": 220, "h": 60, "style": "box", "lines": ["GET /carriers/{carrier}", "the owner's current row"]},
        {"x": 620, "y": 100, "w": 220, "h": 60, "style": "box", "lines": ["edp.shipping.carriers", "compacted, one per carrier"]},
        {"x": 45, "y": 250, "w": 260, "h": 55, "style": "box", "lines": ["from kafka: edp.shipping.scheduled", "ShipmentScheduled, key = order id"]},
        {"x": 45, "y": 340, "w": 410, "h": 60, "style": "accent", "lines": ["enrich().simple(.../carriers/${body.shipment.carrier})", "one HTTP call per record, merged by CarrierEnrichment"]},
        {"x": 45, "y": 435, "w": 410, "h": 55, "style": "kernel", "lines": ["edp.shipping.scheduled-enriched-camel", "mode and link, or the record waits"]},
        {"x": 525, "y": 250, "w": 260, "h": 55, "style": "box", "lines": ["stream: edp.shipping.scheduled", "ShipmentScheduled, key = order id"]},
        {"x": 525, "y": 340, "w": 410, "h": 60, "style": "accent", "lines": ["leftJoin(GlobalKTable carriers)", "local store on every instance, no network call"]},
        {"x": 525, "y": 435, "w": 410, "h": 55, "style": "kernel", "lines": ["edp.shipping.scheduled-enriched", "mode and link, or empty when no reference"]},
    ],
    edges=[
        {"x1": 400, "y1": 130, "x2": 360, "y2": 130},
        {"x1": 580, "y1": 130, "x2": 620, "y2": 130, "dashed": True},
        {"x1": 340, "y1": 160, "x2": 340, "y2": 340, "amber": True},
        {"x1": 820, "y1": 160, "x2": 820, "y2": 340, "amber": True},
        {"x1": 170, "y1": 305, "x2": 170, "y2": 340},
        {"x1": 250, "y1": 400, "x2": 250, "y2": 435},
        {"x1": 650, "y1": 305, "x2": 650, "y2": 340},
        {"x1": 730, "y1": 400, "x2": 730, "y2": 435},
    ],
    notes=[
        {"x": 490, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The same merge, from two places: the owner's answer now, or the owner's last published record"},
        {"x": 490, "y": 545, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "A real service runs one of the two; the example runs both on the same input."},
    ],
)
