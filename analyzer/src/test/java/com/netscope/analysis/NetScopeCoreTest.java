package com.netscope.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class NetScopeCoreTest {
    public static void main(String[] args) throws IOException {
        queueUsesPriorityAndSearchOrder();
        queueLimitsAndDeduplicatesTopResults();
        scorerCapsAreasAndMapsThresholds();
        graphRecordsEvidenceAndRedirects();
        analyzerConnectsQueueScoringAndGraph();
        serverFindsMultipleUrlWarnings();
        knownMaliciousDomainListIsLoadedAndMatched();
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
        int nodeCount = graph.getNodes().size();
        int edgeCount = graph.getEdges().size();
        graph.recordRedirect("https://source.example", "https://destination.example");

        assertEquals(nodeCount, graph.getNodes().size());
        assertEquals(edgeCount, graph.getEdges().size());
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

    private static void serverFindsMultipleUrlWarnings() {
        String url = "http://bit.ly:8080/verify-login/a%2F?q=1&a=2&b=3&c=4&d=5"
                + "&next=https%3A%2F%2Fdestination.example";
        List<RiskConcern> concerns = NetScopeServer.collectConcerns(url);
        RiskAssessment assessment = new RiskScorer().assess(url, concerns);

        assertEquals(65, assessment.getScore());
        assertTrue(hasConcern(concerns, "insecure-connection"));
        assertTrue(hasConcern(concerns, "shortened-url"));
        assertTrue(hasConcern(concerns, "unusual-port"));
        assertTrue(hasConcern(concerns, "sensitive-url-term"));
        assertTrue(hasConcern(concerns, "encoded-url-content"));
        assertTrue(hasConcern(concerns, "many-query-parameters"));
        assertTrue(hasConcern(concerns, "redirect-parameter"));
    }

    private static void knownMaliciousDomainListIsLoadedAndMatched() throws IOException {
        Path domainFile = Files.createTempFile("netscope-domains", ".txt");
        try {
            Files.write(domainFile, Arrays.asList(
                    "# local test list",
                    " BAD.EXAMPLE ",
                    "bad.example # duplicate entry"), StandardCharsets.UTF_8);
            List<String> domains = NetScopeServer.loadKnownMaliciousDomains(domainFile);
            assertEquals(1, domains.size());

            List<RiskConcern> concerns = NetScopeServer.collectConcerns(
                    "https://sub.bad.example/path", domains);
            RiskAssessment assessment = new RiskScorer().assess("https://sub.bad.example/path", concerns);
            assertEquals(50, assessment.getScore());
            assertTrue(hasConcern(concerns, "known-malicious-domain"));

            List<RiskConcern> ipConcerns = NetScopeServer.collectConcerns(
                    "http://203.0.113.8/path", domains);
            assertTrue(hasConcern(ipConcerns, "ip-address-host"));
        } finally {
            Files.deleteIfExists(domainFile);
        }
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

    private static boolean hasConcern(List<RiskConcern> concerns, String id) {
        for (RiskConcern concern : concerns) {
            if (concern.getId().equals(id)) {
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
