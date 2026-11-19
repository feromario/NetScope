package com.netscope.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

public final class UrlAssessmentQueue {
    public static final int MAX_SEARCH_RESULTS = 10;

    private final PriorityQueue<UrlAssessmentJob> jobs = new PriorityQueue<>(
            Comparator.comparingInt(UrlAssessmentJob::getPriority).reversed()
                    .thenComparingInt(UrlAssessmentJob::getSearchPosition)
                    .thenComparingLong(UrlAssessmentJob::getSequence));
    private final Set<String> seenUrls = new HashSet<>();
    private final Set<Integer> occupiedPositions = new HashSet<>();
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
        if (seenUrls.contains(key)) {
            return false;
        }
        if (occupiedPositions.contains(searchPosition)) {
            throw new IllegalArgumentException("Search position is already occupied: " + searchPosition);
        }
        return enqueueValidated(url, key, searchPosition, priority);
    }

    private boolean enqueueValidated(String url, String key, int searchPosition, int priority) {
        if (seenUrls.contains(key)) {
            return false;
        }
        if (occupiedPositions.contains(searchPosition)) {
            throw new IllegalArgumentException("Search position is already occupied: " + searchPosition);
        }

        jobs.add(new UrlAssessmentJob(url, searchPosition, priority, nextSequence++));
        seenUrls.add(key);
        occupiedPositions.add(searchPosition);
        return true;
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
