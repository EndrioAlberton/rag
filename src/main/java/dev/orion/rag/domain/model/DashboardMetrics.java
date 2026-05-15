package dev.orion.rag.domain.model;

public class DashboardMetrics {
    private long totalRequests;
    private long totalConversations;
    private long handoffRequired;
    private long likes;
    private long dislikes;
    private long urgencyLow;
    private long urgencyMedium;
    private long urgencyHigh;

    public long getTotalRequests() { return totalRequests; }
    public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }
    public long getTotalConversations() { return totalConversations; }
    public void setTotalConversations(long totalConversations) { this.totalConversations = totalConversations; }
    public long getHandoffRequired() { return handoffRequired; }
    public void setHandoffRequired(long handoffRequired) { this.handoffRequired = handoffRequired; }
    public long getLikes() { return likes; }
    public void setLikes(long likes) { this.likes = likes; }
    public long getDislikes() { return dislikes; }
    public void setDislikes(long dislikes) { this.dislikes = dislikes; }
    public long getUrgencyLow() { return urgencyLow; }
    public void setUrgencyLow(long urgencyLow) { this.urgencyLow = urgencyLow; }
    public long getUrgencyMedium() { return urgencyMedium; }
    public void setUrgencyMedium(long urgencyMedium) { this.urgencyMedium = urgencyMedium; }
    public long getUrgencyHigh() { return urgencyHigh; }
    public void setUrgencyHigh(long urgencyHigh) { this.urgencyHigh = urgencyHigh; }
}

