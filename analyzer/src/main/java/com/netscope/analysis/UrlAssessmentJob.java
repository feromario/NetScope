package com.netscope.analysis;

public final class UrlAssessmentJob {
    private final String url;
    private final int searchPosition;
    private final int priority;
    private final long sequence;

    UrlAssessmentJob(String url, int searchPosition, int priority, long sequence) {
        this.url = url;
        this.searchPosition = searchPosition;
        this.priority = priority;
        this.sequence = sequence;
    }

    public String getUrl() {
        return url;
    }

    public int getSearchPosition() {
        return searchPosition;
    }

    public int getPriority() {
        return priority;
    }

    long getSequence() {
        return sequence;
    }
}
