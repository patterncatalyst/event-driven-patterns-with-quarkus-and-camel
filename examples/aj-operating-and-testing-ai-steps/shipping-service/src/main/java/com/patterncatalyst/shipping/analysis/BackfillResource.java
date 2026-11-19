package com.patterncatalyst.shipping.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Topics;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Re-analysis and backfill. The analysis table is append-only, so bringing an exception "up to date" means writing
 * a new row at the target prompt version, never editing the old one. The model is called only for exceptions whose
 * latest row is not already at the target version; every exception's snapshot is rebuilt regardless, so {@link
 * ReplayResource} always has current data after a backfill run, even one that called the model for nothing.
 */
@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
public class BackfillResource {

    public record BackfillResult(String promptVersion, int reanalysed, int upToDate, List<String> reanalysedIds) {
    }

    @Inject
    BackfillService backfill;

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "camel.component.kafka.brokers")
    String brokers;

    @POST
    @Path("/reanalyse")
    public BackfillResult reanalyse(@QueryParam("promptVersion") String promptVersion) {
        List<AnalysisRecord> latest = backfill.latestAnalyses();
        Map<String, DeliveryExceptionReported> reportedById = null;
        List<String> reanalysedIds = new ArrayList<>();
        for (AnalysisRecord current : latest) {
            if (current.promptVersion.equals(promptVersion)) {
                backfill.rebuildSnapshot(current.toEvent());
                continue;
            }
            if (reportedById == null) {
                reportedById = RecordedTopic.read(brokers, Topics.SHIPPING_DELIVERY_EXCEPTIONS, DeliveryExceptionReported.class, mapper)
                        .stream().collect(Collectors.toMap(DeliveryExceptionReported::exceptionId, r -> r, (a, b) -> b));
            }
            DeliveryExceptionReported reported = reportedById.get(current.exceptionId);
            if (reported == null) {
                continue; // defensive: no source note on the topic to re-analyse from
            }
            backfill.reanalyseOne(reported, promptVersion);
            reanalysedIds.add(current.exceptionId);
        }
        return new BackfillResult(promptVersion, reanalysedIds.size(), latest.size() - reanalysedIds.size(), reanalysedIds);
    }
}
