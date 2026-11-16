"""Where each part of the compose services lives in the Kubernetes resources."""

from generate_diagram import emit

emit(
    "ab-same-config-two-targets",
    1040,
    470,
    bands=[
        {"x": 20, "y": 55, "w": 380, "h": 375, "fill": "#fafafa", "label": "Compose, examples/_infra"},
        {"x": 560, "y": 55, "w": 460, "h": 375, "fill": "#fff8ef", "label": "Kubernetes, examples/_k8s"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 340, "h": 50, "style": "box", "lines": ["kafka: one container", "KAFKA_PROCESS_ROLES=broker,controller"]},
        {"x": 40, "y": 170, "w": 340, "h": 50, "style": "box", "lines": ["KAFKA_* environment", "num.partitions, replication, min ISR"]},
        {"x": 40, "y": 240, "w": 340, "h": 50, "style": "box", "lines": ["postgres: one container", "image run by start.sh, initdb on first start"]},
        {"x": 40, "y": 310, "w": 340, "h": 50, "style": "box", "lines": ["postgres -c wal_level, slots, senders", "command line of start.sh"]},
        {"x": 40, "y": 370, "w": 340, "h": 45, "style": "ghost", "lines": ["named volumes, mem_limit 1g"]},
        {"x": 580, "y": 100, "w": 420, "h": 50, "style": "accent", "lines": ["Kafka edp + KafkaNodePool controller and broker", "roles split into two pools"]},
        {"x": 580, "y": 170, "w": 420, "h": 50, "style": "accent", "lines": ["Kafka spec.kafka.config", "the same keys and values as the compose environment"]},
        {"x": 580, "y": 240, "w": 420, "h": 50, "style": "accent", "lines": ["Cluster edp-pg, instances: 1", "same operand image, bootstrap.initdb"]},
        {"x": 580, "y": 310, "w": 420, "h": 50, "style": "accent", "lines": ["no postgresql.parameters", "CloudNativePG defaults: logical WAL, 32 slots"]},
        {"x": 580, "y": 370, "w": 420, "h": 45, "style": "ghost", "lines": ["persistent-claim storage, resource limits"]},
    ],
    edges=[
        {"x1": 380, "y1": 125, "x2": 580, "y2": 125, "amber": True, "label": "becomes", "ly": -10},
        {"x1": 380, "y1": 195, "x2": 580, "y2": 195, "amber": True},
        {"x1": 380, "y1": 265, "x2": 580, "y2": 265, "amber": True},
        {"x1": 380, "y1": 335, "x2": 580, "y2": 335, "amber": True},
        {"x1": 380, "y1": 392, "x2": 580, "y2": 392, "dashed": True},
    ],
    notes=[
        {"x": 520, "y": 28, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Two files describe the same two systems in two vocabularies"},
    ],
)
