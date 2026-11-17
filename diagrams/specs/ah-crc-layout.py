"""Two operators installed from catalogs into one namespace through a Manual Subscription each, watching the project where Kafka, PostgreSQL, Connect and order-service run."""

from generate_diagram import emit

emit(
    "ah-crc-layout",
    1000,
    470,
    bands=[
        {"x": 20, "y": 60, "w": 220, "h": 380, "fill": "#fafafa", "label": "On the host"},
        {"x": 260, "y": 60, "w": 220, "h": 380, "fill": "#fff8ef", "label": "Namespace edp-operators"},
        {"x": 500, "y": 60, "w": 480, "h": 380, "fill": "#fafafa", "label": "Namespace edp"},
    ],
    nodes=[
        {"x": 35, "y": 100, "w": 190, "h": 60, "style": "box", "lines": ["install.sh, deploy.sh", "context crc-admin"]},
        {"x": 35, "y": 190, "w": 190, "h": 60, "style": "box", "lines": ["buildah, skopeo", "rootless"]},
        {"x": 35, "y": 380, "w": 190, "h": 50, "style": "user", "lines": ["curl --cacert", "ingress CA"]},
        {"x": 275, "y": 100, "w": 190, "h": 50, "style": "box", "lines": ["OperatorGroup", "targets edp"]},
        {"x": 275, "y": 170, "w": 190, "h": 90, "style": "accent", "lines": ["Subscription Strimzi", "community-operators", "strimzi-1.x, Manual", "startingCSV pinned"]},
        {"x": 275, "y": 280, "w": 190, "h": 90, "style": "accent", "lines": ["Subscription CNPG", "certified-operators", "stable-v1, Manual", "startingCSV pinned"]},
        {"x": 520, "y": 100, "w": 210, "h": 60, "style": "box", "lines": ["Kafka edp", "KRaft, one broker"]},
        {"x": 520, "y": 190, "w": 210, "h": 60, "style": "box", "lines": ["Cluster edp-pg", "role edp with REPLICATION"]},
        {"x": 750, "y": 100, "w": 210, "h": 70, "style": "box", "lines": ["KafkaConnect", "image built into the", "ImageStream edp-connect"]},
        {"x": 750, "y": 190, "w": 210, "h": 60, "style": "ink", "lines": ["KafkaConnector", "order-outbox"]},
        {"x": 520, "y": 370, "w": 440, "h": 60, "style": "accent", "lines": ["order-service, image by digest", "Service and edge-TLS Route, restricted-v2"]},
    ],
    edges=[
        {"x1": 465, "y1": 215, "x2": 520, "y2": 130, "amber": True},
        {"x1": 465, "y1": 325, "x2": 520, "y2": 220, "amber": True},
        {"x1": 225, "y1": 405, "x2": 520, "y2": 400, "label": "HTTPS", "ly": -10},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Operators come from the catalogs at pinned versions and watch only the project"},
    ],
)
