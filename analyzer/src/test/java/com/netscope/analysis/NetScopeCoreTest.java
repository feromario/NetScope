package com.netscope.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class NetScopeCoreTest {
    public static void main(String[] args) {
        queueUsesPriorityAndSearchOrder();
        queueLimitsAndDeduplicatesTopResults();
        scorerCapsAreasAndMapsThresholds();
        graphRecordsEvidenceAndRedirects();
        analyzerConnectsQueueScoringAndGraph();
        invalidUrlsAreRejected();
        System.out.println("NetScope core tests passed");
    }

    private static void queueUsesPriorityAndSearchOrder() {
        UrlAssessmentQueue queue = new UrlAssessmentQueue();
        queue.enqueue("https://first.example", 0, 0);
        queue.enqueue("https://second.example", 1, 5);
        queue.enqueue("https://third.example", 2, 5);

        assertEquals("https://second.example", queue.dequeue().get().getUrl());
        assertEquals("https://third.example", queue.dequeue().get().getUrl());
        assertEquals("https://first.example", queue.dequeue().get().getUrl());
        assertTrue(queue.dequeue().isEmpty());
    }

    private static void queueLimitsAndDeduplicatesTopResults() {
        UrlAssessmentQueue queue = new UrlAssessmentQueue();
        List<String> urls = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            urls.add("https://site" + index + ".example");
        }
        urls.set(1, "https://SITE0.example/#duplicate");

        assertEquals(9, queue.enqueueTopResults(urls));
        assertEquals(9, queue.size());
        assertEquals(0, queue.dequeue().get().getSearchPosition());
    }

    private static void scorerCapsAreasAndMapsThresholds() {
        RiskScorer scorer = new RiskScorer();
        RiskAssessment assessment = scorer.assess("https://risk.example", Arrays.asList(
                concern("rep-a", RiskArea.REPUTATION, 40),
                concern("rep-b", RiskArea.REPUTATION, 40),
                concern("redirect", RiskArea.REDIRECTS, 15)));

        assertEquals(65, assessment.getScore());
        assertEquals(RiskLevel.RISKY, assessment.getLevel());
        assertEquals(RiskLevel.LOW_RISK, RiskLevel.fromScore(19));
        assertEquals(RiskLevel.SOME_RISK, RiskLevel.fromScore(20));
        assertEquals(RiskLevel.SOME_RISK, RiskLevel.fromScore(49));
        assertEquals(RiskLevel.RISKY, RiskLevel.fromScore(50));
        assertEquals("#00c853", RiskLevel.LOW_RISK.getColor());
    }

    private static void graphRecordsEvidenceAndRedirects() {
        RiskScorer scorer = new RiskScorer();
        RiskAssessment assessment = scorer.assess("https://source.example", Arrays.asList(
                new RiskConcern("redirect-chain", RiskArea.REDIRECTS,
                        "Unexpected redirect destination", "redirect check", 10)));
        EvidenceGraph graph = new EvidenceGraph();
        graph.recordAssessment(assessment);
        graph.recordRedirect("https://source.example", "https://destination.example");

        assertTrue(hasEdge(graph, EvidenceGraph.EdgeType.HAS_CONCERN));
        assertTrue(hasEdge(graph, EvidenceGraph.EdgeType.SUPPORTED_BY));
        assertTrue(hasEdge(graph, EvidenceGraph.EdgeType.CONTRIBUTES_TO));
        assertTrue(hasEdge(graph, EvidenceGraph.EdgeType.CLASSIFIED_AS));
        assertTrue(hasEdge(graph, EvidenceGraph.EdgeType.REDIRECTS_TO));
    }

    private static void analyzerConnectsQueueScoringAndGraph() {
        NetScopeAnalyzer analyzer = new NetScopeAnalyzer();
        assertEquals(1, analyzer.enqueueTopResults(Arrays.asList("https://queued.example")));
        UrlAssessmentJob job = analyzer.dequeueNext().get();
        RiskAssessment result = analyzer.assess(job, Arrays.asList(
                concern("listed", RiskArea.REPUTATION, 50)));

        assertEquals(RiskLevel.RISKY, result.getLevel());
        assertTrue(hasEdge(analyzer.getEvidenceGraph(), EvidenceGraph.EdgeType.CLASSIFIED_AS));
    }

    private static void invalidUrlsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RiskScorer().assess("file:///tmp/x", new ArrayList<>()));
        assertThrows(IllegalArgumentException.class, () -> new UrlAssessmentQueue().enqueue("javascript:alert(1)", 0, 0));
    }

    private static RiskConcern concern(String id, RiskArea area, int points) {
        return new RiskConcern(id, area, id, "test source", points);
    }

    private static boolean hasEdge(EvidenceGraph graph, EvidenceGraph.EdgeType type) {
        for (EvidenceGraph.Edge edge : graph.getEdges()) {
            if (edge.getType() == type) {
                return true;
            }
        }
        return false;
    }

    private static void assertThrows(Class<? extends Throwable> expected, Runnable action) {
        try {
            action.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) {
                return;
            }
            throw new AssertionError("Unexpected exception", actual);
        }
        throw new AssertionError("Expected exception " + expected.getName());
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("Expected condition to be true");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
