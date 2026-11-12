"""Where the processed-id key is stored decides whether it can commit with the effect."""

from generate_diagram import emit

emit(
    "11-where-the-key-lives",
    960,
    440,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 120, "fill": "#fff8ef", "label": "Committed with the effect: no gap"},
        {"x": 20, "y": 210, "w": 920, "h": 200, "fill": "#fafafa", "label": "Committed apart from the effect: a crash between the two writes loses or repeats it"},
    ],
    nodes=[
        {"x": 40, "y": 105, "w": 290, "h": 56, "style": "accent", "lines": ["inbox table", "in the handler's transaction"]},
        {"x": 335, "y": 105, "w": 290, "h": 56, "style": "accent", "lines": ["JpaMessageIdRepository", "inside a transacted route"]},
        {"x": 630, "y": 105, "w": 290, "h": 56, "style": "box", "lines": ["natural idempotence", "an upsert keyed by the order id"]},
        {"x": 40, "y": 250, "w": 440, "h": 56, "style": "ghost", "lines": ["JpaMessageIdRepository", "route not transacted: key commits alone"]},
        {"x": 500, "y": 250, "w": 420, "h": 56, "style": "ghost", "lines": ["JdbcMessageIdRepository", "under JTA: fails on every message"]},
        {"x": 40, "y": 330, "w": 440, "h": 56, "style": "ghost", "lines": ["KafkaIdempotentRepository", "a topic, never in the database transaction"]},
        {"x": 500, "y": 330, "w": 420, "h": 56, "style": "ghost", "lines": ["a cache or a set in another store", "its own commit, its own failure"]},
    ],
    edges=[],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Where the processed-id key lives"},
    ],
)
