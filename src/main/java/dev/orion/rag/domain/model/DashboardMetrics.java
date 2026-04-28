package dev.orion.rag.domain.model;

public class DashboardMetrics {
    private long totalRequests;
    private long totalConversations;
    private long handoffRequired;
    private long likes;
    private long dislikes;

    public long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public long getTotalConversations() {
        return totalConversations;
    }

    public void setTotalConversations(long totalConversations) {
        this.totalConversations = totalConversations;
    }

    public long getHandoffRequired() {
        return handoffRequired;
    }

    public void setHandoffRequired(long handoffRequired) {
        this.handoffRequired = handoffRequired;
    }

    public long getLikes() {
        return likes;
    }

    public void setLikes(long likes) {
        this.likes = likes;
    }

    public long getDislikes() {
        return dislikes;
    }

    public void setDislikes(long dislikes) {
        this.dislikes = dislikes;
    }
}

