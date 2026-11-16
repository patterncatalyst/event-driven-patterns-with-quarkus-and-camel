"""One input topic, three engines: where each keeps its state and how the question is asked."""

from generate_diagram import emit

emit(
    "ac-three-ways",
    980,
    420,
    bands=[
        {"x": 250, "y": 56, "w": 710, "h": 340, "fill": "#fafafa", "label": "Same records, same one-minute windows, same lateness"},
    ],
    nodes=[
        {"x": 20, "y": 160, "w": 190, "h": 100, "style": "ink", "lines": ["edp.shipping.delivered", "one partition", "deliveredAt in the payload"]},
        {"x": 270, "y": 90, "w": 260, "h": 80, "style": "accent", "lines": ["Kafka Streams in the service", "window store deliveries-by-carrier", "state lives in the application"]},
        {"x": 270, "y": 200, "w": 260, "h": 80, "style": "box", "lines": ["Flink SQL job", "TUMBLE over a watermarked table", "state lives in the job"]},
        {"x": 270, "y": 310, "w": 260, "h": 70, "style": "box", "lines": ["RisingWave views", "tumble() over a source", "state lives in the database"]},
        {"x": 640, "y": 90, "w": 300, "h": 80, "style": "user", "lines": ["GET /counts?carrier=...", "an interactive query on the store", "answers for open and closed windows"]},
        {"x": 640, "y": 200, "w": 300, "h": 80, "style": "user", "lines": ["edp.shipping.delivery-counts-flink", "upsert-kafka, one row per window", "a row appears when the window closes"]},
        {"x": 640, "y": 310, "w": 300, "h": 70, "style": "user", "lines": ["psql: select from counts_rw", "PostgreSQL wire protocol", "EMIT ON WINDOW CLOSE variant"]},
    ],
    edges=[
        {"x1": 210, "y1": 190, "x2": 270, "y2": 130, "amber": True},
        {"x1": 210, "y1": 210, "x2": 270, "y2": 240, "amber": True},
        {"x1": 210, "y1": 235, "x2": 270, "y2": 345, "amber": True},
        {"x1": 530, "y1": 130, "x2": 640, "y2": 130},
        {"x1": 530, "y1": 240, "x2": 640, "y2": 240},
        {"x1": 530, "y1": 345, "x2": 640, "y2": 345},
    ],
    notes=[
        {"x": 490, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The question is the same; where the answer lives and how it is asked differ"},
    ],
)
