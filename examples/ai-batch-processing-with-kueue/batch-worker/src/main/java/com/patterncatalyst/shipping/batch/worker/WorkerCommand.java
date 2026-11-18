package com.patterncatalyst.shipping.batch.worker;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine.Command;

/** The three steps of a reconciliation run: plan, reconcile, aggregate. */
@TopCommand
@Command(name = "worker", mixinStandardHelpOptions = true,
        subcommands = { PlanCommand.class, ReconcileCommand.class, AggregateCommand.class })
public class WorkerCommand {
}
