// Section: Observing event flows (trace propagation, flow metrics, correlation).
"use strict";

module.exports = function (lib) {
  const { divider, bulletSlide, figSlide } = lib;

  divider(
    "Observing event flows",
    "Traces, metrics and logs across a commit, a log reader and a topic",
    `An event flow has places that belong to no service: the outbox between a commit and a topic, the topic between a producer and a group, and the dead letter topic. When one of them stops moving, the services on both sides stay healthy, because each is waiting for the other. Three habits make those places visible. The trace context travels inside the message, so one trace crosses the relay. The metrics are read where the work waits, not inside each service. And one business identifier ties spans and log lines together, so a phone call about an order leads to its trace and its logs.`,
  );

  bulletSlide("TRACE CONTEXT", "Trace context in the message", [
    ["Trace context propagation", " writes the trace context into the message, copies it at every relay, and lets the consumer continue the trace as a child or start a new one with a link."],
    ["Problem:", " an HTTP call carries its trace in a header. A message has no request to ride on, and the outbox adds a table row and a log reader between the commit and the topic."],
    ["Carriers:", " the outbox row keeps the context in a column, Debezium puts a traceparent header on the record, and the consumer reads it."],
    ["Without it:", " the consumer starts a trace of its own and the picture ends at the commit."],
    ["Parent or link:", " a single prompt record fits a child span. A batch, or work that runs after a delay, fits a link."],
  ], `An HTTP call carries its trace in a request header, and the server reads it before it starts work. A message has no request to ride on. The producer finishes, the transaction commits, and a relay publishes the event from another process. With the outbox there are two extra hops: the event is first a row in a table, then an entry in the database log that Connect reads. Each relay has to receive the trace context from the one before and pass it on. The W3C traceparent is the carrier: a version, a trace id, the parent span id and flags. Without it, the consumer starts a new trace and nothing connects it to the order.`);

  figSlide("TRACE CONTEXT", "One order, one trace, three services", "25-trace-waterfall",
    "order-service to inventory-service through the outbox and Debezium: spans in one trace",
    `The request span has an outbox write as a child, Debezium adds a span for the time the change sat in the database log and one for reading it, and the consumer span in inventory-service hangs from the log span. Everything between the end of the outbox write and the start of the consumer span is time the services did not spend: the log, the relay, the topic and the consumer's poll. In the runs measured that gap stayed under a second. A trace of only the two services would show a quiet hole there, and a trace through Connect shows how much of it is Debezium. The trace does not say why a gap is long. A stopped connector, a slow broker and a lagging consumer look the same, so metrics at the relay and the broker are what explain it.`);

  bulletSlide("FLOW METRICS", "Measure the flow, not the service", [
    ["Flow metrics", " measure each stage where its work waits: backlog at the relay, lag at the broker, event age on arrival, dead letters as a rate. Alert on the signal that grows while the flow is stuck."],
    ["Problem:", " a service's own counters see only the work that reaches it. A paused connector leaves every service dashboard green."],
    ["Backlog:", " the distance in bytes between the replication slot's write position and the position the connector confirmed. The outbox table itself holds no rows to count."],
    ["Lag:", " read from the broker, because a stopped consumer's own metric simply stops."],
    ["Age:", " recorded on arrival, so it is a graph and the number a customer feels, not the alert."],
  ], `A service's metrics describe the service: requests handled, errors, latency. An event flow also has places that belong to no service. The outbox sits between a commit and a topic, and a stall there leaves both sides healthy. The obvious readings fail in specific ways. The outbox table is emptied in the transaction that fills it, so counting rows always reads zero. A consumer's lag metric disappears with the consumer. And event age is measured when something arrives, so it says nothing until the stall ends. The backlog gauge works because it reads the replication slot. A heartbeat query keeps the confirmed position moving, and the alert threshold sits at 16 KiB, between the idle reading and a paused connector.`);

  figSlide("FLOW METRICS", "Where each signal lives", "26-where-each-signal-lives",
    "order-service, Debezium Connect, Kafka and inventory-service: what each signal reads",
    `Each signal is read in a different place and misses something. Outbox backlog comes from the replication slot and misses anything after the topic. Group lag comes from the broker, so it survives a stopped consumer, but a stalled relay never lets records reach the topic, so lag stays flat. Event age and record age come from the consumer, only when an event arrives. Behind a Debezium outbox the two agree, and they part when a record is written again: a redriven dead letter reads young by record age and old by event age. Dead letters are read as the end offset of the dead letter topic. In the run measured, with the connector paused, the alert on backlog reached Alerting a median of 29 seconds after the orders were placed. An alert on event age did not fire during the pause.`);

  bulletSlide("CORRELATION", "Correlation identifier across signals", [
    ["A correlation identifier", " puts the business identifier on every span and in every handler's log context, puts the trace identifier on every log line, and links any trace that a timer starts to the one it continues."],
    ["Problem:", " a trace id means nothing to a customer, and an order id means nothing to a tracing library. Support starts from the order id."],
    ["Three keys:", " the trace id finds one request tree and its lines, the order id finds every trace and line for the order, the link joins a timer's trace to its origin."],
    ["Loki:", " one label per service. The order id and trace id are structured metadata, so the index does not grow with the orders."],
    ["Risk:", " a business id that is personal data, or not unique, spreads or misleads."],
  ], `Someone phones to say an order never got its payment. They have an order id. Tempo is indexed by trace id and Loki holds lines of text, and neither knows what an order is. Three things break the link: a handler that never records which order its trace is about, a timer that starts work with no trace behind it, and a log line written without a trace id. Hohpe and Woolf describe the Correlation Identifier for matching a reply to a request. Here the same idea ties traces and logs together. The order id as a Loki label would open a stream per order, so it travels as structured metadata instead, which costs a filter at query time and keeps the index the size of the service list.`);

  figSlide("CORRELATION", "From a log line to its trace and back", "27-log-trace-roundtrip",
    "order-service, payment route and sweeper: log lines, spans and links in Loki and Tempo",
    `Both directions are provisioned in Grafana. The Loki data source has a derived field that turns the trace id of a line into a link to the trace in Tempo. The Tempo data source has a trace-to-logs link that runs a Loki query on the trace id and the span's service. From a ticket, search Tempo for the order id, open the trace and jump to the lines of any service. Or start in Loki with the order id, read the lines of both traces in time order and open either. An order that times out produces two traces, because the sweeper runs on a timer. The sweeper links to the trace that placed the order, so the original trace stays short and the link says one continues the other. Not every line carries the order id: a line written before the handler set it has only the trace id, and a query by trace id returns both.`);
};
