"""Three key strategies and where one order's events land among three partitions."""

from generate_diagram import emit

emit(
    "04-keys-to-partitions",
    960,
    470,
    bands=[
        {"x": 20, "y": 70, "w": 920, "h": 110, "fill": "#fafafa", "label": "Key: order id"},
        {"x": 20, "y": 195, "w": 920, "h": 110, "fill": "#fafafa", "label": "Key: event type"},
        {"x": 20, "y": 320, "w": 920, "h": 110, "fill": "#fafafa", "label": "Key: carrier"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 230, "h": 66, "style": "box", "lines": ["murmur2(O-1001) mod 3", "one key per order"]},
        {"x": 300, "y": 100, "w": 200, "h": 66, "style": "box", "lines": ["O-1002", "all three events"]},
        {"x": 520, "y": 100, "w": 200, "h": 66, "style": "box", "lines": ["O-1004", "all three events"]},
        {"x": 740, "y": 100, "w": 190, "h": 66, "style": "accent", "lines": ["O-1001", "all three events, in order"]},
        {"x": 40, "y": 225, "w": 230, "h": 66, "style": "box", "lines": ["murmur2(ShipmentScheduled)", "three keys, shared by all orders"]},
        {"x": 300, "y": 225, "w": 200, "h": 66, "style": "accent", "lines": ["InTransit and Delivered", "of every order"]},
        {"x": 520, "y": 225, "w": 200, "h": 66, "style": "ghost", "lines": ["no event type", "maps here"]},
        {"x": 740, "y": 225, "w": 190, "h": 66, "style": "accent", "lines": ["Scheduled", "of every order"]},
        {"x": 40, "y": 350, "w": 230, "h": 66, "style": "box", "lines": ["murmur2(AIRCO) mod 3", "two keys, shared by all orders"]},
        {"x": 300, "y": 350, "w": 200, "h": 66, "style": "box", "lines": ["FASTLINE orders", "each order in order"]},
        {"x": 520, "y": 350, "w": 200, "h": 66, "style": "ghost", "lines": ["no carrier", "maps here"]},
        {"x": 740, "y": 350, "w": 190, "h": 66, "style": "box", "lines": ["AIRCO orders", "each order in order"]},
    ],
    notes=[
        {"x": 480, "y": 24, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The key picks the partition; the partition is the only place order exists"},
        {"x": 400, "y": 56, "anchor": "middle", "size": 12, "color": "#555555", "text": "Partition 0"},
        {"x": 620, "y": 56, "anchor": "middle", "size": 12, "color": "#555555", "text": "Partition 1"},
        {"x": 835, "y": 56, "anchor": "middle", "size": 12, "color": "#555555", "text": "Partition 2"},
        {"x": 480, "y": 455, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "Order id keeps an order whole and spreads orders. Event type splits one order. Carrier keeps orders whole but leaves a partition idle."},
    ],
)
