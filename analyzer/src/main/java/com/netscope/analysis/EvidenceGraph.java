package com.netscope.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class EvidenceGraph {
    public enum NodeType {
        URL,
        CONCERN,
        EVIDENCE,
        SCORE,
        ASSESSMENT,
        RISK_LEVEL
    }

    public enum EdgeType {
        REDIRECTS_TO,
        HAS_CONCERN,
        SUPPORTED_BY,
        CONTRIBUTES_TO,
        PRODUCES,
        CLASSIFIED_AS
    }

    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();

    public synchronized void recordAssessment(RiskAssessment assessment) {
        Objects.requireNonNull(assessment, "assessment");
        String urlId = urlNodeId(assessment.getUrl());
        String scoreId = "score:" + assessment.getId();
        String assessmentId = "assessment:" + assessment.getId();
        String riskId = "risk-level:" + assessment.getLevel().name();

        addNode(new Node(urlId, NodeType.URL, assessment.getUrl()));
        addNode(new Node(scoreId, NodeType.SCORE, Integer.toString(assessment.getScore())));
        addNode(new Node(assessmentId, NodeType.ASSESSMENT, assessment.getLevel().getLabel()));
        addNode(new Node(riskId, NodeType.RISK_LEVEL, assessment.getLevel().getLabel()));
        addEdge(new Edge(scoreId, assessmentId, EdgeType.PRODUCES));
        addEdge(new Edge(assessmentId, riskId, EdgeType.CLASSIFIED_AS));

        for (RiskConcern concern : assessment.getConcerns()) {
            String concernId = "concern:" + assessment.getId() + ":" + concern.getId();
            String evidenceId = "evidence:" + assessment.getId() + ":" + concern.getId();
            addNode(new Node(concernId, NodeType.CONCERN,
                    concern.getDescription() + " (" + concern.getPoints() + " points)"));
            addNode(new Node(evidenceId, NodeType.EVIDENCE, concern.getEvidenceSource()));
            addEdge(new Edge(urlId, concernId, EdgeType.HAS_CONCERN));
            addEdge(new Edge(concernId, evidenceId, EdgeType.SUPPORTED_BY));
            addEdge(new Edge(concernId, scoreId, EdgeType.CONTRIBUTES_TO));
        }
    }

    public synchronized void recordRedirect(String sourceUrl, String destinationUrl) {
        String sourceId = urlNodeId(sourceUrl);
        String destinationId = urlNodeId(destinationUrl);
        addNode(new Node(sourceId, NodeType.URL, sourceUrl));
        addNode(new Node(destinationId, NodeType.URL, destinationUrl));
        addEdge(new Edge(sourceId, destinationId, EdgeType.REDIRECTS_TO));
    }

    public synchronized List<Node> getNodes() {
        return Collections.unmodifiableList(new ArrayList<>(nodes));
    }

    public synchronized List<Edge> getEdges() {
        return Collections.unmodifiableList(new ArrayList<>(edges));
    }

    private static String urlNodeId(String url) {
        return "url:" + WebUrl.canonicalKey(url);
    }

    private void addNode(Node node) {
        for (Node existing : nodes) {
            if (existing.getId().equals(node.getId())) {
                if (existing.getType() != node.getType() || !existing.getLabel().equals(node.getLabel())) {
                    throw new IllegalArgumentException("Conflicting graph node id: " + node.getId());
                }
                return;
            }
        }
        nodes.add(node);
    }

    private void addEdge(Edge edge) {
        if (!hasNode(edge.getFrom()) || !hasNode(edge.getTo())) {
            throw new IllegalStateException("Graph edges must reference existing nodes");
        }
        for (Edge existing : edges) {
            if (existing.getFrom().equals(edge.getFrom())
                    && existing.getTo().equals(edge.getTo())
                    && existing.getType() == edge.getType()) {
                return;
            }
        }
        edges.add(edge);
    }

    private boolean hasNode(String id) {
        for (Node node : nodes) {
            if (node.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    public static final class Node {
        private final String id;
        private final NodeType type;
        private final String label;

        private Node(String id, NodeType type, String label) {
            this.id = id;
            this.type = type;
            this.label = label;
        }

        public String getId() {
            return id;
        }

        public NodeType getType() {
            return type;
        }

        public String getLabel() {
            return label;
        }

    }

    public static final class Edge {
        private final String from;
        private final String to;
        private final EdgeType type;

        private Edge(String from, String to, EdgeType type) {
            this.from = from;
            this.to = to;
            this.type = type;
        }

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }

        public EdgeType getType() {
            return type;
        }

    }
}
