// Section: close. Choosing a pattern, credits. The QR slide is added by deck.js.
"use strict";

module.exports = function (lib) {
  const { tableSlide } = lib;

  tableSlide("CHOOSING A PATTERN", "Choosing a pattern",
    ["Problem", "Pattern"],
    [
      ["A consumer must react, and can ask for details", "Event notification"],
      ["A consumer needs state and cannot call back", "Event-carried state transfer"],
      ["Related events must stay in order", "Keys to partitions; a resequencer across keys"],
      ["A commit and a publish can disagree", "Transactional outbox, read by log tailing"],
      ["The same message arrives twice", "Idempotent consumer with an inbox"],
      ["Read, process and write must happen once", "Kafka transactions"],
      ["One bad message blocks a partition", "Retry and dead letter"],
      ["The history is the record", "Event store with snapshots"],
      ["Reads and writes need different shapes", "CQRS projections"],
      ["Stored events must stay readable", "Upcasting on read"],
      ["Data arrives late or out of order", "Event-time windows with grace"],
      ["A business process spans services", "Saga: choreography or orchestration"],
      ["A stuck flow shows no error", "Trace context, flow metrics, correlation ids"],
    ],
    [6.0, 6.09],
    `Each row starts from the problem, because a pattern is a response to a force and not a feature to adopt. If a consumer can ask the source for details, a notification is enough and the payload stays small. If it cannot call back, the event carries the state and the consumer keeps its own copy. If a service must change a database and publish, the outbox makes both one commit and a log reader publishes. Duplicate delivery is met with an idempotent consumer, and a failure that will not clear is moved aside with a dead letter. Every pattern here has a cost in operations or latency, and each has a case where it is the wrong choice, so the cheaper answer is the correct one whenever the problem is absent.`,
    { fontSize: 13, rowH: 0.36 },
  );

  tableSlide("CREDITS", "Works these patterns draw on",
    ["Author", "Work"],
    [
      ["Gregor Hohpe, Bobby Woolf", "Enterprise Integration Patterns"],
      ["Chris Richardson", "Microservices Patterns; transactional outbox and saga pattern pages"],
      ["Adam Bellemare", "Building Event-Driven Microservices, Second Edition"],
      ["Martin Kleppmann, Chris Riccomini", "Designing Data-Intensive Applications, Second Edition"],
      ["Vaughn Vernon", "Implementing Domain-Driven Design"],
      ["Alex Lawrence", "Message- and Event-Driven Systems with CQRS and Event Sourcing"],
      ["Martin Fowler", "Event Sourcing; CQRS; What do you mean by \"Event-Driven\"?"],
      ["Greg Young", "CQRS Documents; Versioning in an Event Sourced System"],
      ["Danilo Sato", "Parallel Change"],
      ["R. A. DeMillo, R. J. Lipton, F. G. Sayward", "Hints on Test Data Selection: Help for the Practicing Programmer"],
      ["Hector Garcia-Molina, Kenneth Salem", "Sagas"],
      ["W3C", "Trace Context"],
    ],
    [4.6, 7.49],
    `These are the outside works whose ideas shaped what is covered here and how carefully it treats each pattern. The pattern names come mostly from Hohpe and Woolf, and the outbox, log tailing and saga descriptions from Richardson. Fowler names event notification, event-carried state transfer and event sourcing, and Young supplies the event store operations and versioning topics. Parallel Change gives the expand and contract sequence used for schema changes. DeMillo, Lipton and Sayward proposed mutation testing, the idea behind the bite cycle. Garcia-Molina and Salem introduced sagas, and the W3C specification defines the trace context. None of the text or code of these works is reproduced here. Each slide's code comes from tested examples written for these patterns.`,
    { fontSize: 13, rowH: 0.36 },
  );
};
