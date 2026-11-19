package com.netscope.analysis;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class NetScopeAnalyzer {
    private final UrlAssessmentQueue queue = new UrlAssessmentQueue();
    private final RiskScorer scorer = new RiskScorer();
    private final EvidenceGraph graph = new EvidenceGraph();

    public int enqueueTopResults(List<String> resultUrls) {
        return queue.enqueueTopResults(resultUrls);
    }

    public boolean enqueue(String url, int searchPosition, int initialPriority) {
        return queue.enqueue(url, searchPosition, initialPriority);
    }

    public Optional<UrlAssessmentJob> dequeueNext() {
        return queue.dequeue();
    }

    public RiskAssessment assess(UrlAssessmentJob job, Collection<RiskConcern> concerns) {
        Objects.requireNonNull(job, "job");
        RiskAssessment assessment = scorer.assess(job.getUrl(), concerns);
        graph.recordAssessment(assessment);
        return assessment;
    }

    public EvidenceGraph getEvidenceGraph() {
        return graph;
    }
}
