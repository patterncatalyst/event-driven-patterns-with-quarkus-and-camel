"""Open correlation state lives in a local store with a changelog topic on the Kafka Streams side, and in a PostgreSQL table on the Camel side; a recreated container finds both."""

from generate_diagram import emit

emit(
    "20-state-where",
    960,
    410,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 150, "fill": "#fff8ef", "label": "Kafka Streams"},
        {"x": 20, "y": 240, "w": 920, "h": 150, "fill": "#fafafa", "label": "Camel aggregator"},
    ],
    nodes=[
        {"x": 40, "y": 105, "w": 190, "h": 80, "style": "box", "lines": ["Join store", "RocksDB files in the", "container"]},
        {"x": 260, "y": 105, "w": 200, "h": 80, "style": "accent", "lines": ["Changelog topic", "every put is also", "written to Kafka"]},
        {"x": 490, "y": 105, "w": 200, "h": 80, "style": "ghost", "lines": ["Container recreated", "the local files are gone"]},
        {"x": 720, "y": 105, "w": 200, "h": 80, "style": "ink", "lines": ["Store restored by", "reading the changelog", "then the join resumes"]},
        {"x": 40, "y": 285, "w": 190, "h": 80, "style": "box", "lines": ["Open group", "one exchange per order", "in memory"]},
        {"x": 260, "y": 285, "w": 200, "h": 80, "style": "accent", "lines": ["shipping.readiness", "the group is a row, saved", "with the aggregation step"]},
        {"x": 490, "y": 285, "w": 200, "h": 80, "style": "ghost", "lines": ["Container recreated", "the JVM is gone"]},
        {"x": 720, "y": 285, "w": 200, "h": 80, "style": "ink", "lines": ["Group read back from", "the table; the timeout", "starts again from the restart"]},
    ],
    edges=[
        {"x1": 230, "y1": 145, "x2": 260, "y2": 145},
        {"x1": 460, "y1": 145, "x2": 490, "y2": 145, "dashed": True},
        {"x1": 690, "y1": 145, "x2": 720, "y2": 145},
        {"x1": 230, "y1": 325, "x2": 260, "y2": 325},
        {"x1": 460, "y1": 325, "x2": 490, "y2": 325, "dashed": True},
        {"x1": 690, "y1": 325, "x2": 720, "y2": 325},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Open state has to be somewhere a recreated container can find it"},
    ],
)
