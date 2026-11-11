"""Fetch, buffer, poll and process in a Kafka consumer, and where max.poll.interval.ms removes a slow member."""

from generate_diagram import emit

emit(
    "06-fetch-poll-process",
    960,
    420,
    bands=[
        {"x": 490, "y": 195, "w": 450, "h": 190, "fill": "#fafafa", "label": "max.poll.interval.ms: the longest gap allowed between two poll() calls"},
    ],
    nodes=[
        {"x": 30, "y": 90, "w": 190, "h": 80, "style": "box", "lines": ["Broker", "3 topics, 9 partitions"]},
        {"x": 270, "y": 90, "w": 190, "h": 80, "style": "box", "lines": ["Fetch request", "returns at fetch.min.bytes", "or after fetch.max.wait.ms"]},
        {"x": 510, "y": 90, "w": 190, "h": 80, "style": "box", "lines": ["Client buffer", "fetched data that poll()", "has not returned yet"]},
        {"x": 750, "y": 90, "w": 190, "h": 80, "style": "accent", "lines": ["poll() and process", "at most max.poll.records", "each record costs work"]},
        {"x": 510, "y": 245, "w": 190, "h": 80, "style": "ink", "lines": ["A slow batch", "500 records x 50 ms", "is 25 s without a poll"]},
        {"x": 730, "y": 245, "w": 190, "h": 80, "style": "ghost", "lines": ["Member removed", "partitions reassigned,", "batch delivered again"]},
    ],
    edges=[
        {"x1": 220, "y1": 130, "x2": 270, "y2": 130, "amber": True},
        {"x1": 460, "y1": 130, "x2": 510, "y2": 130, "amber": True},
        {"x1": 700, "y1": 130, "x2": 750, "y2": 130, "amber": True},
        {"x1": 800, "y1": 170, "x2": 640, "y2": 245, "dashed": True},
        {"x1": 700, "y1": 285, "x2": 730, "y2": 285, "dashed": True},
    ],
    notes=[
        {"x": 480, "y": 24, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A fetch fills a buffer; poll() hands out a bounded slice of it"},
        {"x": 30, "y": 230, "size": 12, "color": "#555555", "text": "max.poll.records limits what poll() returns."},
        {"x": 30, "y": 252, "size": 12, "color": "#555555", "text": "It does not limit what a fetch brings back."},
        {"x": 30, "y": 290, "size": 12, "color": "#555555", "text": "fetch.min.bytes and fetch.max.wait.ms"},
        {"x": 30, "y": 312, "size": 12, "color": "#555555", "text": "decide how often a trickle of records is fetched."},
        {"x": 30, "y": 350, "size": 12, "color": "#555555", "text": "Size max.poll.records so one poll's work"},
        {"x": 30, "y": 372, "size": 12, "color": "#555555", "text": "finishes well inside max.poll.interval.ms."},
    ],
)
