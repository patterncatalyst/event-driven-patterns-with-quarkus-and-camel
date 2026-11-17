// Section: Processing and coordinating streams. Enrichment, event time, windows, sagas, decisions.
"use strict";

module.exports = function (lib) {
  const { divider, bulletSlide, figSlide, codeSlide, tableSlide } = lib;
  const EYE = "STREAMS";
  const EX = "";

  divider(
    "Processing and coordinating streams",
    "Enrich, join, window, coordinate and decide",
    `Once events flow, services need more than delivery. They need data the sender never carried, a correlation between two streams, a result for a slot of time, a way to undo a business transaction that spans databases, and a decision that stays repeatable when a model reads free text. For most of them, a Camel form and a Kafka Streams or workflow form run on the same input.`,
  );

  // ---- Enricher ----------------------------------------------------------
  bulletSlide(
    EYE,
    "Content Enricher",
    [
      ["A Content Enricher", " uses data in the incoming message to fetch the missing details from an external source and attaches them to the message."],
      ["Problem.", " shipping-service publishes the carrier and tracking number, not the carrier's tracking page. Copying the page into every event freezes an old value into each one."],
      ["Lookup.", " Ask the owner for each record. Always the current row, but the consumer depends on the owner being up."],
      ["Table join.", " Keep a copy fed by the owner's events. Works while the owner is down, but only as fresh as its last published change."],
    ],
    `Hohpe and Woolf define the enricher by its source being external to the message. Whether that source is a call or a local copy is a choice the pattern leaves open, and it decides how the enrichment behaves when the world changes. notification-service builds the message the customer reads, so it needs carrier mode and a tracking link without owning that reference data. Putting the page into every event would repeat reference data in every record. Making the receiver keep its own data would give two services one truth to maintain. So the same enrichment is written twice, as a Camel route that calls the owner and as a Kafka Streams topology that joins against a compacted topic.`,
  );

  figSlide(
    EYE,
    "Lookup against stream-table join",
    "19-enricher-vs-table-join",
    "notification-service · one enrichment, two forms",
    `The lookup form calls shipping-service for each shipment and merges the answer into the message. A failed call is retried three times and then the exchange fails; with break on first error the consumer retries the same record, so the offset stays on it, nothing is lost and the records behind it wait. The join form never calls out. A compacted topic holds one record per carrier, and every instance reads the whole topic into a local store. When the owner is down the join carries on from its copy, with data as old as the last record it read. After a reference changes, a shipment scheduled in between gets the new link from the lookup and the old one from the join, and the two agree once the record is applied, which took 7 to 13 ms once it was on the topic.`,
  );

  codeSlide(
    EYE,
    "Enrich by calling the owner",
    "JAVA",
    "19-enrich/notification-service/src/main/java/com/patterncatalyst/shipping/enrich/ShipmentEnricherRoute.java",
    25, 39,
    "    @Override",
    "notification-service · ShipmentEnricherRoute.java",
    `The route reads ShipmentScheduled, calls shipping-service with the carrier taken from the message, and hands the original message and the answer to an aggregation strategy that merges them. The error handler retries a failed call three times with a one second delay. The consumer option breakOnFirstError is the guard that matters: with it the failed record is read again and the group's offset stays put, and without it the consumer commits past the failure and the record is never enriched, silently and permanently. The Kafka key is set through the header, and a key option on the endpoint would take precedence over it. A burst of 500 shipments made 500 lookups and took 6.4 s from the producer call to the last output.`,
  );

  codeSlide(
    EYE,
    "Enrich from a local copy",
    "JAVA",
    "19-enrich/notification-service/src/main/java/com/patterncatalyst/shipping/enrich/ShipmentEnrichmentTopology.java",
    42, 52,
    "        StreamsBuilder builder = new StreamsBuilder();",
    "notification-service · ShipmentEnrichmentTopology.java",
    `The carriers topic is read into a global table, which holds every partition on every instance. The first lambda in the left join says where to look: the join key is a field of the value, not the record key, which is the order id, and that is what a global table is for. A regular table joins on the record key, so joining shipments on a table keyed by carrier would match nothing and would need a re-keying and a repartition topic for two rows. The cost is a full copy of the topic per instance, which suits a small table and not a large one. It is a left join, so a shipment whose carrier has no reference is forwarded with an empty mode and link, where an inner join would drop it silently. The same burst of 500 shipments took 2.5 s.`,
  );

  // ---- Stream-stream join, event time -----------------------------------
  bulletSlide(
    EYE,
    "Event time or arrival time",
    [
      ["A stream-stream join", " pairs two events that share a key and fall within a time window of each other, and keeps each side in state until the window closes."],
      ["Streams.", " The window is measured on occurredAt in the payload, so late and replayed records land where they happened."],
      ["Camel Aggregator.", " The timeout counts the time a group has been quiet on the service's clock, so a replay looks like a burst."],
      ["Unmatched.", " Streams emits PaymentOverdue only when stream time passes the window. Camel emits it when the timeout expires."],
    ],
    `Shipping may book a carrier only when the stock is reserved and the payment is taken. The two facts come from two services on two topics, keyed by the same order id, and either may arrive first. Hohpe and Woolf call the correlating unit an Aggregator, a stateful filter that stores related messages until a complete set has arrived. Both forms are Aggregators in that sense, and they differ in how they decide a set is complete and which clock they use. A payment within 30 minutes of the reservation is a statement about when things happened, and only event time can keep it. A payment within 10 seconds of reaching this service is a statement about the service, and the timeout is the direct form of it.`,
  );

  figSlide(
    EYE,
    "Two clocks, two answers",
    "20-event-vs-arrival-time",
    "shipping-service · Kafka Streams left join against Camel Aggregator",
    `Two crafted pairs show the clocks disagreeing in opposite directions. Order O-2003 is reserved at 09:10 and paid at 09:12 in event time, but the records are produced more than the timeout apart: Streams matches it, and the Camel group times out first and reports PaymentOverdue. Order O-2004 is reserved at 09:20 and paid at 10:50, a replayed payment, with both records produced back to back: Streams sees 90 minutes and reports PaymentOverdue, while Camel sees both facts together and reports ReadyToShip. A stream that goes quiet never closes its windows, because stream time advances only with records; Camel's timeout needs only the clock, and an unpaid reservation was reported 10.05 to 11.07 seconds after it arrived in 20 runs with a 10 second timeout. Matching 200 orders took about 2.6 s on Streams and about 3.1 s on Camel.`,
  );

  // ---- Windowed aggregation ---------------------------------------------
  bulletSlide(
    EYE,
    "Windows with grace",
    [
      ["A windowed aggregation", " groups records by key into windows of event time, keeps each open for a grace period, emits one final result when stream time passes its end plus the grace, and drops a record that arrives after that."],
      ["Problem.", " Nothing says when a pickup list is complete, and waiting for everything never ends on a stream."],
      ["Grace is latency.", " The 09:00 slot ends at 09:15 and its list leaves when stream time passes 09:20."],
      ["Late and dropped.", " A record past the grace is not an error. Only a dropped-records counter shows it, so alert on that."],
    ],
    `Each carrier collects parcels in pickup slots, and the warehouse needs one list per carrier per slot. Arrival time is the wrong clock here, because a paused consumer, a replay or a republish through the outbox would move shipments into the wrong slot, so the slot comes from the time the shipment was scheduled. Stream time is the newest event time seen and advances only when a record arrives, so the wall clock never closes a window. A longer grace waits longer for stragglers and delays every list by the difference. A shorter one sends lists sooner and drops more of them, so the right value is the largest disorder the source produces. A pickup slot is a tumbling window, because windows that advance by less than their size would put every shipment in several lists.`,
  );

  figSlide(
    EYE,
    "When a window closes",
    "21-window-close-and-grace",
    "shipping-service · pickup slots, stream time and the grace period",
    `Seven FASTLINE shipments stamped 09:01 to 09:07 arrive, then one stamped 09:16 opens the next slot and moves stream time to 09:16. A shipment stamped 09:10 arrives next. It is behind stream time, but the 09:00 window stays open until 09:20, so it joins that list. A record stamped 09:21 moves stream time past 09:20 and the 09:00 list leaves with a count of 8. A shipment stamped 09:05 arriving after that belongs to a closed window and is discarded, and the only trace is the task's dropped-records counter, which Micrometer exports. The Camel form closes a batch per carrier at 5 shipments or after 10 seconds without a new one, so a late record joins whatever batch is open.`,
  );

  codeSlide(
    EYE,
    "Slots, grace and one final list",
    "JAVA",
    "21-aggregate/shipping-service/src/main/java/com/patterncatalyst/shipping/pickups/PickupTopology.java",
    65, 75,
    "        var builder = new StreamsBuilder();",
    "shipping-service · PickupTopology.java",
    `The stream reads event time from the payload through a timestamp extractor and re-keys by carrier, which writes the records to a repartition topic whose name is fixed so it stays stable across restarts. The window is a time window of one slot size with a grace, and the aggregate adds each shipment to the list for its carrier and slot. An aggregation's output is a table, so each record that lands in a window would emit an updated list, and a warehouse acting on the first one would pick up one parcel. Suppress until window closes holds the updates and emits only the last value per window, at the cost of the grace as added latency. The buffer is unbounded and in memory, which is acceptable here because one list per carrier per slot is small. For 1,000 shipments, the last list left 1.3 s after the tick.`,
  );

  // ---- Saga choreography -------------------------------------------------
  bulletSlide(
    EYE,
    "Saga: compensation, not rollback",
    [
      ["A saga", " is a sequence of local transactions, each publishing the event that triggers the next, in which a failure is undone by compensating transactions that are new events."],
      ["Problem.", " An order touches three databases and no transaction spans them, so a declined payment leaves the stock reserved."],
      ["Choreography.", " Each service reacts to the events of the others, with no coordinator. Each publishes through an outbox so a committed step always announces itself."],
      ["No isolation.", " While it runs, readers see reserved stock for an order that may be cancelled."],
    ],
    `Garcia-Molina and Salem introduced the saga in 1987, and Richardson names the two ways to coordinate one: choreography, where each participant reacts to events, and orchestration, where a central unit gives the instructions. The outbox gives every service an atomic step, its row and its event committing together. It does not give the order a way to go back. A compensation does not restore the previous state. It records a new fact that makes the previous one harmless: a declined payment publishes PaymentFailed, order-service cancels the order and publishes OrderCancelled, and inventory releases on either event. Inventory may see both in no guaranteed order, either twice, or before the reservation exists, so the release has to survive all of it.`,
  );

  figSlide(
    EYE,
    "Choreographed saga and its compensation",
    "22-saga-choreography",
    "order-service, inventory-service, payment-service · events only, no coordinator",
    `order-service holds the saga row for each order, with a state, a deadline and a version, and every transition is an update that names the state it expects, so a payment reply and the timeout sweeper that arrive together for the same saga change exactly one row. Silence has no event to react to, so every wait has a deadline owned by one service, and the sweeper cancels a saga between 0.6 and 1.7 seconds after its deadline. The hazard is a participant that was down for the whole timeout and then reads a reservation for an order that was cancelled. A semantic lock handles it: payment-service records the cancelled order ids and checks them before it takes any money, so a late payment for a cancelled order is refused.`,
  );

  tableSlide(
    EYE,
    "What each step can undo",
    ["Step", "Action", "Compensation", "Kind"],
    [
      ["Reserve stock", "inventory-service holds the quantity", "Release the reservation, idempotent and commutative", "Compensatable"],
      ["Take payment", "payment-service takes the money", "A refund, a business decision and not an undo", "Pivot"],
      ["Schedule shipment", "Book the carrier", "None; retried until it succeeds", "Retriable"],
    ],
    [2.3, 3.2, 4.4, 2.19],
    `Steps differ in what can be done about them afterwards. The three kinds come from Richardson and the Azure Architecture Center's Saga pattern. A compensatable step has an undo, so the stock release is written to be idempotent and commutative: applied twice it releases once, and applied before the reservation it leaves a marker so the late reservation is refused. The pivot is the step after which the saga goes forward or ends, and a payment cannot simply be undone, because a refund is a business decision. Everything after the pivot needs a retry, because there is no way back, and the shipment is that retriable step. Not every failure needs a compensation: when the stock is not there, the saga ends at once, and no release exists for a reservation that was never made.`,
    { fontSize: 14, rowH: 0.7 },
  );

  // ---- Orchestrated: Camel Saga, LRA, Quarkus Flow ----------------------
  bulletSlide(
    EYE,
    "Orchestrated sagas: LRA and Quarkus Flow",
    [
      ["An orchestrated saga", " keeps the sequence and each compensation in one orchestrator that sends a command, waits for the reply, and on a failure or an expired limit tells the participants to undo."],
      ["Camel Saga EIP.", " An LRA coordinator holds the saga state, and participants register their own undo."],
      ["Quarkus Flow.", " A workflow lists the tasks, and the undo is a task you write."],
      ["Both.", " Every saga gets an explicit time limit, and participants only execute commands."],
    ],
    `Three things push a flow toward a central owner: the sequence changes and every change is a coordinated release, someone has to answer where an order is when the answer is spread over three tables, and a time limit belongs to the whole saga. Hohpe and Woolf call the central unit a Process Manager. The same flow, order then stock then payment, runs here under two orchestrators over the same participants. With the Camel Saga EIP, the coordinator holds the saga and calls each registered compensation when it is cancelled. With a Quarkus Flow workflow, the state is a row per waiting instance in PostgreSQL, and the compensation is the tasks run after a failed or timed-out wait.`,
  );

  figSlide(
    EYE,
    "Choreography, Camel Saga and Flow",
    "23-three-ways",
    "order-service · the same flow in three forms",
    `With payment-service stopped, each form has a 20 second limit. The coordinator cancels the Camel saga when the limit passes, calls the compensations registered by then, and refuses a payment service that never joined when it tries to join a cancelled saga, so no payment is taken. The workflow's wait gives up when its limit passes, then refunds, releases and cancels, so the late payment is taken and then refunded, and the money moved and came back. Both end with the order cancelled, the stock released and one OrderCancelled published. The Camel form makes 6 coordinator requests per order. A burst of 100 orders had the last one paid after 3.8 s with Camel and 2.0 s with Flow. Choreograph a short flow owned by one team, and orchestrate when the sequence changes often or has to be audited.`,
  );

  codeSlide(
    EYE,
    "Opening a saga with Camel",
    "JAVA",
    "23-orchestrated-sagas/order-service/src/main/java/com/patterncatalyst/shipping/orchestration/order/CamelOrderSagaRoute.java",
    33, 38,
    "        from(\"direct:camel-saga\").routeId(\"camel-saga-start\")",
    "order-service · CamelOrderSagaRoute.java",
    `The start route opens the saga at the coordinator with a new propagation, a time limit read from configuration, the two routes the coordinator calls on cancel or completion, and the order id as a saga option. The route then sends the first command to inventory. Completion mode manual is the important line: with the default, the saga completes when the route that started it ends, which is right after the command is sent and before any participant has answered, so a PaymentFailed that arrives afterwards would find nothing to compensate. The saga id travels as a header on the Kafka record, and participants join it. The coordinator is state you have to keep: restarting it with one saga in flight lost that saga in 6 of 7 restarts, and the order stayed PLACED past its time limit.`,
  );

  // ---- Analyse then decide ----------------------------------------------
  bulletSlide(
    EYE,
    "Analyse with a model, decide with rules",
    [
      ["Analyse then decide", " lets a model turn text into a value from a closed set, records it with the model and prompt that produced it, and lets rules decide from the record, never from the model."],
      ["Problem.", " A model that also decides can answer differently tomorrow, gives no reason but more text, and puts a refund behind a prompt."],
      ["Closed answer.", " A schema forces one of seven values, with temperature 0, a fixed seed and a versioned prompt."],
      ["Rules decide.", " Each decision names the rule that fired, and a replay makes no model call."],
    ],
    `A carrier reports a parcel it could not deliver in a sentence its driver typed, and shipping-service has to decide whether to ship again or refund. Extraction from text is fuzzy and tolerates a small error rate, and a model reads that box crushed at the depot and torn wrapping are the same kind of event without a keyword list. A decision is business policy: it has to be exact, explainable and open to change by whoever owns the policy. The recorded analysis is the boundary. Everything before it may vary and is paid for once per note. Everything after it is a function of the record, so it is repeatable, explainable and replayable. Unknown refunds, so no customer waits on a parcel nobody understood.`,
  );

  figSlide(
    EYE,
    "Analyse, record, then decide",
    "24-analyse-then-decide",
    "shipping-service · analysis and decision as two recorded events",
    `A Camel route reads the note, calls the model through an AI service that returns a typed record, and writes the analysis to its own topic. A second route runs a Drools rule unit on each analysis and writes the decision with the rule's name and version. On ten invented notes a 4B-parameter open model gave every note the same reason in each of 15 runs from fresh containers, and gave nine of ten the reason a reader would. The tenth note is injected text, and the model obeyed it in all 15 runs: the same answer every time, and a wrong one, which the rules cannot tell apart from a right one. A model call took a median of 2.4 to 3.1 s per note on a CPU with no GPU, so one analysis per note, off the request path, is affordable.`,
  );

  codeSlide(
    EYE,
    "Rules decide from the record",
    "JAVA",
    "24-decisions-on-events/shipping-service/src/main/java/com/patterncatalyst/shipping/decisions/DecisionService.java",
    44, 50,
    "    Verdict fire(Analysis analysis, int limit) {",
    "shipping-service · DecisionService.java",
    `The service creates a rule unit instance for each analysis, loads the analysis and the one parameter a rule reads, and fires the rules. The service then returns the single verdict. The six rules are written to be mutually exclusive, so exactly one fires for any reason and attempt. A rule that overlaps another, or a case no rule covers, throws instead of picking silently. The rule's name goes into the decision event, so a refund can be traced to the rule that produced it. Replay reads the recorded analyses from the first offset and runs this same method, with no model call. Replaying with the model asked again took about 13 s for eight notes instead of half a second, and a model that answers differently another day would change the decisions too.`,
  );
};
