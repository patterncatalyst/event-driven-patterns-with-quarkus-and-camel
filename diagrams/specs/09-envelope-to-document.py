"""A Debezium change envelope becomes an InventoryItem document; a delete becomes one tombstone."""

from generate_diagram import emit

emit(
    "09-envelope-to-document",
    960,
    480,
    nodes=[
        {"x": 30, "y": 60, "w": 240, "h": 110, "style": "box", "lines": ["update envelope", "op u", "before: key only or whole row", "after: item_sku, qty_on_hand"]},
        {"x": 30, "y": 200, "w": 240, "h": 90, "style": "box", "lines": ["delete envelope", "op d", "before: the old row", "after: null"]},
        {"x": 30, "y": 320, "w": 240, "h": 70, "style": "ghost", "lines": ["Debezium tombstone", "key, no value"]},
        {"x": 340, "y": 140, "w": 260, "h": 150, "style": "accent", "lines": ["InventoryItemDocumentRoute", "drop records with no value", "unmarshal to ChangeEnvelope", "key from after, or before on d", "map the legacy row"]},
        {"x": 670, "y": 60, "w": 270, "h": 80, "style": "box", "lines": ["document, key SKU-1", "sku, quantityOnHand"]},
        {"x": 670, "y": 200, "w": 270, "h": 80, "style": "box", "lines": ["tombstone, key SKU-5", "one per delete"]},
        {"x": 670, "y": 320, "w": 270, "h": 70, "style": "ink", "lines": ["edp.inventory.items", "compacted: latest per key"]},
    ],
    edges=[
        {"x1": 270, "y1": 115, "x2": 340, "y2": 180, "amber": True},
        {"x1": 270, "y1": 245, "x2": 340, "y2": 230, "amber": True},
        {"x1": 270, "y1": 355, "x2": 340, "y2": 270, "dashed": True, "label": "dropped", "lx": -26, "ly": 22},
        {"x1": 600, "y1": 180, "x2": 670, "y2": 100, "amber": True},
        {"x1": 600, "y1": 240, "x2": 670, "y2": 240, "amber": True},
        {"x1": 805, "y1": 140, "x2": 805, "y2": 200, "dashed": True},
        {"x1": 805, "y1": 280, "x2": 805, "y2": 320, "dashed": True},
    ],
    notes=[
        {"x": 480, "y": 24, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Camel maps the envelope; the delete is published once, not twice"},
        {"x": 480, "y": 450, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "Without the drop, Debezium's tombstone would be read as a change and published as a second record for the key."},
    ],
)
