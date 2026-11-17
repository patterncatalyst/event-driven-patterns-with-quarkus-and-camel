// Opening: agenda, audience and stack, running example, lifecycle, compose
// stack, telemetry path, events versus row updates. No divider.

"use strict";

module.exports = function (lib) {
  lib.tableSlide(
    "AGENDA",
    "Eight sections and a decision table",
    ["Section", "What it covers"],
    [
      ["Opening", "The stack, the running example, events against row updates"],
      ["Events between services", "Notification, state transfer, claim check, keys, resequencing, tuning, share groups"],
      ["Getting events out reliably", "Outbox, log tailing, offsets, inbox, exactly-once, dead letters"],
      ["Event sourcing", "Event store, snapshots, CQRS, replay, schema evolution"],
      ["Processing and coordinating streams", "Enrich, join, aggregate, sagas, decisions on events"],
      ["Observing event flows", "Traces, metrics and logs across asynchronous hops"],
      ["Running on Kubernetes with Strimzi", "KRaft, topics as resources, admission, monitoring, scaling, mirroring, Connect"],
      ["Testing, querying and deploying", "Tests, stream queries, resequencing across partitions, rolling deploys, write-forward cache"],
      ["Choosing a pattern", "One table from problem to pattern"],
    ],
    [3.9, 8.19],
    `The sections follow one order-to-delivery system from the first event to a running Kubernetes cluster. For a 90-minute path, take the opening, events between services, getting events out reliably and event sourcing in full. Compress processing and coordinating streams to its headline slides on enrichment, windows and sagas. Compress running on Kubernetes with Strimzi to the figures, and testing, querying and deploying to the two deployment figures. The first five sections run on Docker Compose and the Kubernetes section on minikube with the Strimzi operator. Finish on the decision table.`,
    { fontSize: 13, rowH: 0.42 },
  );

  lib.tableSlide(
    "AUDIENCE AND STACK",
    "Who this is for, and what it runs on",
    ["Component", "Version", "Role"],
    [
      ["Quarkus platform", "3.40.1.2 (LTS)", "Services; includes Camel Quarkus 3.40.0"],
      ["Apache Camel", "4.22.2 (LTS)", "Routes and integration patterns"],
      ["Apache Kafka", "4.3.2, KRaft", "The log every pattern publishes to and reads from"],
      ["PostgreSQL", "18.6", "State, outbox tables, event store, on the CloudNativePG image"],
      ["Apicurio Registry", "3.3.4", "Schemas for the evolution examples"],
      ["Debezium Connect", "3.7.1.Final", "PostgreSQL connector and the outbox router"],
      ["Grafana LGTM", "0.41.0", "Loki, Grafana, Tempo and Prometheus behind one OTLP endpoint"],
      ["Strimzi", "1.3.1", "Kafka operator for the Kubernetes runs"],
    ],
    [3.0, 2.6, 6.49],
    `The audience is Java and Quarkus developers, Camel integrators, platform engineers and architects who already know what a topic and a consumer group are. The stack is Quarkus services with Camel routes on Kafka, run first on Docker Compose and then on Kubernetes with Strimzi. Every version is an exact pin, the newest stable release that works with the rest, and nothing needs a licence or a subscription. Kafka is 4.3.2 in both run targets because that is the newest version the latest Strimzi supports. Kafka runs in KRaft mode, so there is no ZooKeeper anywhere.`,
    { fontSize: 13, rowH: 0.42 },
  );

  lib.figSlide(
    "RUNNING EXAMPLE",
    "One shop, five services",
    "00-running-example",
    "Order, inventory, payment and shipping joined by topics; notification reads every topic",
    `Every example is a piece of one business: an online shop that sells and ships physical goods. Five services each own one step. Order owns the order and its lifecycle, inventory owns stock, payment owns payments, shipping owns shipments and the carrier choice, and notification tells the customer. They share no tables and react to events on Kafka. Each knows only the topics it reads and the topics it writes. Nine events carry the flow, and topic names follow edp, a domain and an event, with dots and hyphens only. Two patterns call the order service: event notification fetches the current order, and a claim check fetches a stored attachment.`,
  );

  lib.figSlide(
    "RUNNING EXAMPLE",
    "The order and shipment lifecycles",
    "00-order-lifecycle",
    "An order moves one step at a time; a shipment follows its own path",
    `An order moves one step at a time: PLACED, PAID, SHIPPED, DELIVERED. It can become CANCELLED only from PLACED or PAID, and DELIVERED and CANCELLED are final. A shipment moves PENDING, SCHEDULED, IN_TRANSIT, DELIVERED. Two fictional carriers deliver the parcels, FASTLINE by road and AIRCO by air. The failure paths are part of the model. Out of stock cancels the order and tells the customer. A declined payment releases the reserved stock and cancels the order. A late delivery has to raise an alert. The same event delivered twice must not be applied twice, and a service that is down finds its events waiting in Kafka when it returns.`,
  );

  lib.figSlide(
    "THE STACK",
    "One stack for every example",
    "00-compose-stack",
    "curl posts to the service; Kafka, PostgreSQL, Connect and LGTM around it",
    `Every example runs on the same local stack: a Kafka broker, PostgreSQL with logical decoding enabled, Apicurio Registry, Kafka Connect with Debezium, and a Grafana LGTM container that receives traces, metrics and logs. An example adds its own Quarkus services on a shared network. Kafka is published on 9092 for the host and reached as kafka:9094 from containers, PostgreSQL on 5432, Apicurio on 8081, Connect on 8083 and Grafana on 3000. Prometheus is on 19090 because Cockpit, often enabled on Fedora and RHEL, holds 9090. Run one stack at a time: the services publish fixed host ports, so a second copy fails to bind them.`,
  );

  lib.figSlide(
    "THE STACK",
    "One endpoint, three backends",
    "00-telemetry-path",
    "Traces, metrics and logs leave the service over OTLP and land in Tempo, Prometheus and Loki",
    `Traces, metrics and logs reach the LGTM container through OpenTelemetry. A service exports all three signals over OTLP with http/protobuf to one endpoint, and the container routes them: traces to Tempo, metrics to Prometheus and logs to Loki. Camel's OpenTelemetry extension creates a span per route and processor. The Quarkus Micrometer and OpenTelemetry extension sends the meters over the same path, so no Prometheus scrape is configured, and the log records reach Loki with no extra setting. Grafana at port 3000 needs no login. In Explore, pick Tempo for traces, Prometheus for metrics or Loki for logs and filter on the service name.`,
  );

  lib.figSlide(
    "EVENTS, COMMANDS, DOCUMENTS",
    "One command, two storage outcomes",
    "01-row-update-vs-event-append",
    "ChangeOrderStatus applied to an orders row and to the order_event table",
    `Three message types and one storage decision. An Event Message announces that something happened, in the past tense, and the sender neither knows nor cares who reads it. A Command Message tells one receiver to perform a named action, and the receiver may refuse it. A Document Message carries a complete piece of data and prescribes nothing. The storage decision is separate: the same command either overwrites the status in an orders row, which keeps the current state and loses the old one, or appends a new version to the order_event table, which keeps every status and can rebuild the state by replay. Use a row when the current state is the only question, and events when the past is part of the requirement.`,
  );
};
