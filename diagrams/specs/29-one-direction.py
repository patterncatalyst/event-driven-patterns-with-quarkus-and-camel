"""Topic changes travel one way, from the KafkaTopic resource to Kafka; a change made in Kafka is put back."""

from generate_diagram import emit

emit(
    "29-one-direction",
    960,
    360,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 120, "fill": "#fff8ef", "label": "Kubernetes"},
    ],
    nodes=[
        {"x": 40, "y": 95, "w": 190, "h": 70, "style": "box", "lines": ["KafkaTopic", "partitions: 3", "retention.ms: 3600000"]},
        {"x": 385, "y": 95, "w": 190, "h": 70, "style": "accent", "lines": ["Topic Operator", "in the entity operator", "full pass every 15 s"]},
        {"x": 385, "y": 235, "w": 190, "h": 70, "style": "ink", "lines": ["Kafka topic", "edp.orders.placed"]},
        {"x": 730, "y": 235, "w": 190, "h": 70, "style": "ghost", "lines": ["kafka-configs.sh", "an edit made in Kafka"]},
        {"x": 730, "y": 95, "w": 190, "h": 70, "style": "ghost", "lines": ["status", "Ready, or Ready=False", "NotSupported"]},
    ],
    edges=[
        {"x1": 230, "y1": 130, "x2": 385, "y2": 130, "amber": True, "label": "reads", "ly": -10},
        {"x1": 480, "y1": 165, "x2": 480, "y2": 235, "amber": True, "label": "creates, alters, deletes", "ly": -8},
        {"x1": 730, "y1": 270, "x2": 575, "y2": 270, "dashed": True, "label": "changes the topic", "ly": -10},
        {"x1": 575, "y1": 245, "x2": 575, "y2": 165, "dashed": True},
        {"x1": 575, "y1": 130, "x2": 730, "y2": 130, "dashed": True, "label": "writes", "ly": -10},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The resource is the source of truth: an edit in Kafka is reverted on the next pass"},
        {"x": 480, "y": 340, "anchor": "middle", "size": 13, "color": "#555555",
         "text": "A topic created in Kafka gets no KafkaTopic: the operator never reads that way"},
    ],
)
