"""One Kafka transaction holds the output records and the input offsets; the commit marker makes both take effect."""

from generate_diagram import emit

emit(
    "12-read-process-write",
    960,
    440,
    bands=[
        {"x": 530, "y": 70, "w": 410, "h": 250, "fill": "#fff8ef", "label": "One Kafka transaction (transactional.id payment-service-tx)"},
    ],
    nodes=[
        {"x": 30, "y": 150, "w": 200, "h": 80, "style": "box", "lines": ["edp.inventory.reserved", "a batch of 5 records"]},
        {"x": 290, "y": 150, "w": 190, "h": 80, "style": "box", "lines": ["PaymentProcessor", "poll, map, send"]},
        {"x": 560, "y": 110, "w": 350, "h": 70, "style": "box", "lines": ["edp.payments.processed", "5 PaymentProcessed records"]},
        {"x": 560, "y": 210, "w": 350, "h": 70, "style": "box", "lines": ["__consumer_offsets", "group payment-service moves to offset 5"]},
        {"x": 530, "y": 350, "w": 410, "h": 56, "style": "accent", "lines": ["commit marker on both partitions", "the records become visible and the offset moves"]},
    ],
    edges=[
        {"x1": 230, "y1": 190, "x2": 290, "y2": 190},
        {"x1": 480, "y1": 175, "x2": 560, "y2": 145, "amber": True},
        {"x1": 480, "y1": 205, "x2": 560, "y2": 245, "amber": True},
        {"x1": 735, "y1": 320, "x2": 735, "y2": 350},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The offsets are written in the producer's transaction, not by the consumer"},
    ],
)
