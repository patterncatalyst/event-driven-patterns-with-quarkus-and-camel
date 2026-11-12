"""Median load times from the verify.sh measurement: a full replay grows with the stream, a snapshot load does not."""

from generate_diagram import emit

# Medians in milliseconds of three runs (median of the three run medians), from the verify.sh measurement.
FULL_MS = {"1,000 events": 1.4, "10,000 events": 12.2, "100,000 events": 126.1}
SNAPSHOT_MS = 0.63

SCALE = 560 / max(FULL_MS.values())  # pixels per millisecond
X0 = 220


def bar_width(ms):
    return max(6, round(ms * SCALE))


nodes = []
notes = []
y = 90
for label, ms in FULL_MS.items():
    nodes.append({"x": 20, "y": y, "w": 180, "h": 44, "style": "box", "lines": [label]})
    nodes.append({"x": X0, "y": y, "w": bar_width(ms), "h": 44, "style": "accent", "lines": [""]})
    notes.append({"x": X0 + bar_width(ms) + 10, "y": y + 28, "color": "#444444", "size": 13, "text": f"{ms} ms full load"})
    y += 70
nodes.append({"x": 20, "y": y, "w": 180, "h": 44, "style": "box", "lines": ["with a snapshot"]})
nodes.append({"x": X0, "y": y, "w": bar_width(SNAPSHOT_MS), "h": 44, "style": "ink", "lines": [""]})
notes.append({"x": X0 + bar_width(SNAPSHOT_MS) + 10, "y": y + 28, "color": "#444444", "size": 13, "text": f"{SNAPSHOT_MS} ms snapshot load"})
notes.append({"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
              "text": "Median load time inside the service, 21 loads per bar"})
notes.append({"x": 480, "y": y + 90, "anchor": "middle", "size": 11, "color": "#555555",
              "text": "Bar length is proportional to time. 8-core laptop CPU, PostgreSQL on the compose stack."})

emit("15-rebuild-cost", 960, y + 110, nodes=nodes, notes=notes)
