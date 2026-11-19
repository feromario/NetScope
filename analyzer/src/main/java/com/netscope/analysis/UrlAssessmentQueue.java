package com.netscope.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

public final class UrlAssessmentQueue {
    public static final int MAX_SEARCH_RESULTS = 10;

    private final PriorityQueue<UrlAssessmentJob> jobs = new PriorityQueue<>(
            Comparator.comparingInt(UrlAssessmentJob::getPriority).reversed()
                    .thenComparingInt(UrlAssessmentJob::getSearchPosition)
                    .thenComparingLong(UrlAssessmentJob::getSequence));
    private final List<String> seenUrls = new ArrayList<>();
    private final boolean[] occupiedPositions = new boolean[MAX_SEARCH_RESULTS];
    private long nextSequence;

    public synchronized int enqueueTopResults(List<String> resultUrls) {
        if (resultUrls == null) {
            throw new IllegalArgumentException("Search results must not be null");
        }

        int count = Math.min(resultUrls.size(), MAX_SEARCH_RESULTS);
        List<String> canonicalKeys = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            canonicalKeys.add(WebUrl.canonicalKey(resultUrls.get(index)));
        }

        int enqueued = 0;
        for (int index = 0; index < count; index++) {
            if (enqueueValidated(resultUrls.get(index), canonicalKeys.get(index), index, 0)) {
                enqueued++;
            }
        }
        return enqueued;
    }

    public synchronized boolean enqueue(String url, int searchPosition, int priority) {
        if (searchPosition < 0 || searchPosition >= MAX_SEARCH_RESULTS) {
            throw new IllegalArgumentException("Search position must be between 0 and 9");
        }
        String key = WebUrl.canonicalKey(url);
        if (hasSeenUrl(key)) {
            return false;
        }
        if (occupiedPositions[searchPosition]) {
            throw new IllegalArgumentException("Search position is already occupied: " + searchPosition);
        }
        return enqueueValidated(url, key, searchPosition, priority);
    }

    private boolean enqueueValidated(String url, String key, int searchPosition, int priority) {
        if (hasSeenUrl(key)) {
            return false;
        }
        if (occupiedPositions[searchPosition]) {
            throw new IllegalArgumentException("Search position is already occupied: " + searchPosition);
        }

        jobs.add(new UrlAssessmentJob(url, searchPosition, priority, nextSequence++));
        seenUrls.add(key);
        occupiedPositions[searchPosition] = true;
        return true;
    }

    private boolean hasSeenUrl(String key) {
        return seenUrls.contains(key);
    }

    public synchronized Optional<UrlAssessmentJob> dequeue() {
        return Optional.ofNullable(jobs.poll());
    }

    public synchronized int size() {
        return jobs.size();
    }

    public synchronized boolean isEmpty() {
        return jobs.isEmpty();
    }
}
