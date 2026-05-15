package dev.orion.rag.application.rest;

import dev.orion.rag.application.rest.dto.DashboardMetricsResponse;
import dev.orion.rag.application.rest.dto.FeedbackRequest;
import dev.orion.rag.domain.model.InteractionSummary;
import dev.orion.rag.domain.port.out.DashboardPort;
import dev.orion.rag.domain.port.out.FeedbackPort;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/ai")
public class DashboardController {

    private final FeedbackPort feedbackPort;
    private final DashboardPort dashboardPort;

    @Inject
    public DashboardController(
            FeedbackPort feedbackPort,
            DashboardPort dashboardPort) {
        this.feedbackPort = feedbackPort;
        this.dashboardPort = dashboardPort;
    }

    @POST
    @Path("/feedback")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> submitFeedback(@Valid FeedbackRequest req) {
        return Uni.createFrom().completionStage(() ->
                        feedbackPort.submit(req.userId, req.conversationId, req.userMessage, req.value))
                .replaceWith(Response.ok("{\"ok\":true}").build())
                .onFailure(IllegalArgumentException.class).recoverWithItem(t ->
                        Response.status(400).entity("{\"message\":\"" + t.getMessage() + "\"}").build())
                .onFailure().invoke(t -> Log.error("Erro ao salvar feedback", t))
                .onFailure().recoverWithItem(Response.serverError().entity("{\"message\":\"erro ao salvar feedback\"}").build());
    }

    @GET
    @Path("/dashboard/metrics")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<DashboardMetricsResponse> metrics() {
        return Uni.createFrom().completionStage(() -> dashboardPort.metrics())
                .map(m -> {
                    DashboardMetricsResponse r = new DashboardMetricsResponse();
                    r.totalRequests = m.getTotalRequests();
                    r.totalConversations = m.getTotalConversations();
                    r.handoffRequired = m.getHandoffRequired();
                    r.likes = m.getLikes();
                    r.dislikes = m.getDislikes();
                    r.urgencyLow = m.getUrgencyLow();
                    r.urgencyMedium = m.getUrgencyMedium();
                    r.urgencyHigh = m.getUrgencyHigh();
                    return r;
                });
    }

    /**
     * Returns interactions filtered by urgency (BAIXA / MEDIA / ALTA) or by
     * feedback (LIKE / DISLIKE). Use ?urgency=ALTA or ?feedback=LIKE.
     */
    @GET
    @Path("/dashboard/interactions")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> interactions(
            @QueryParam("urgency") String urgency,
            @QueryParam("feedback") String feedback) {
        if (urgency != null && !urgency.isBlank()) {
            return Uni.createFrom()
                    .completionStage(() -> dashboardPort.interactionsByUrgency(urgency))
                    .map(list -> Response.ok(list).build())
                    .onFailure().recoverWithItem(Response.serverError().build());
        }
        if (feedback != null && !feedback.isBlank()) {
            boolean liked = "LIKE".equalsIgnoreCase(feedback);
            return Uni.createFrom()
                    .completionStage(() -> dashboardPort.interactionsByFeedback(liked))
                    .map(list -> Response.ok(list).build())
                    .onFailure().recoverWithItem(Response.serverError().build());
        }
        return Uni.createFrom().item(
            Response.status(400).entity("{\"message\":\"Use ?urgency=ALTA ou ?feedback=LIKE\"}").build());
    }
}

