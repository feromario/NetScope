package com.netscope.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Set<Edge> edges = new LinkedHashSet<>();

    public synchronized void recordAssessment(RiskAssessment assessment) {
        Objects.requireNonNull(assessment, "assessment");
        String urlId = urlNodeId(assessment.getUrl());
        String scoreId = "score:" + assessment.getId();
        String assessmentId = "assessment:" + assessment.getId();
        String riskId = "risk-level:" + assessment.getLevel().name();

        addNode(new Node(urlId, NodeType.URL, assessment.getUrl(), Collections.emptyMap()));
        addNode(new Node(scoreId, NodeType.SCORE, Integer.toString(assessment.getScore()),
                Collections.singletonMap("points", Integer.toString(assessment.getScore()))));
        addNode(new Node(assessmentId, NodeType.ASSESSMENT, assessment.getLevel().getLabel(),
                Collections.singletonMap("score", Integer.toString(assessment.getScore()))));
        addNode(new Node(riskId, NodeType.RISK_LEVEL, assessment.getLevel().getLabel(),
                Collections.singletonMap("color", assessment.getLevel().getColor())));
        addEdge(new Edge(scoreId, assessmentId, EdgeType.PRODUCES));
        addEdge(new Edge(assessmentId, riskId, EdgeType.CLASSIFIED_AS));

        for (RiskConcern concern : assessment.getConcerns()) {
            String concernId = "concern:" + assessment.getId() + ":" + concern.getId();
            String evidenceId = "evidence:" + assessment.getId() + ":" + concern.getId();
            addNode(new Node(concernId, NodeType.CONCERN, concern.getDescription(),
                    Collections.singletonMap("points", Integer.toString(concern.getPoints()))));
            addNode(new Node(evidenceId, NodeType.EVIDENCE, concern.getEvidenceSource(),
                    Collections.singletonMap("description", concern.getDescription())));
            addEdge(new Edge(urlId, concernId, EdgeType.HAS_CONCERN));
            addEdge(new Edge(concernId, evidenceId, EdgeType.SUPPORTED_BY));
            addEdge(new Edge(concernId, scoreId, EdgeType.CONTRIBUTES_TO));
        }
    }

    public synchronized void recordRedirect(String sourceUrl, String destinationUrl) {
        String sourceId = urlNodeId(sourceUrl);
        String destinationId = urlNodeId(destinationUrl);
        addNode(new Node(sourceId, NodeType.URL, sourceUrl, Collections.emptyMap()));
        addNode(new Node(destinationId, NodeType.URL, destinationUrl, Collections.emptyMap()));
        addEdge(new Edge(sourceId, destinationId, EdgeType.REDIRECTS_TO));
    }

    public synchronized List<Node> getNodes() {
        return Collections.unmodifiableList(new ArrayList<>(nodes.values()));
    }

    public synchronized List<Edge> getEdges() {
        return Collections.unmodifiableList(new ArrayList<>(edges));
    }

    private static String urlNodeId(String url) {
        return "url:" + WebUrl.canonicalKey(url);
    }

    private void addNode(Node node) {
        Node existing = nodes.get(node.getId());
        if (existing != null && !existing.equals(node)) {
            throw new IllegalArgumentException("Conflicting graph node id: " + node.getId());
        }
        nodes.putIfAbsent(node.getId(), node);
    }

    private void addEdge(Edge edge) {
        if (!nodes.containsKey(edge.getFrom()) || !nodes.containsKey(edge.getTo())) {
            throw new IllegalStateException("Graph edges must reference existing nodes");
        }
        edges.add(edge);
    }

    public static final class Node {
        private final String id;
        private final NodeType type;
        private final String label;
        private final Map<String, String> attributes;

        private Node(String id, NodeType type, String label, Map<String, String> attributes) {
            this.id = id;
            this.type = type;
            this.label = label;
            this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
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

        public Map<String, String> getAttributes() {
            return attributes;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Node)) {
                return false;
            }
            Node node = (Node) other;
            return id.equals(node.id)
                    && type == node.type
                    && label.equals(node.label)
                    && attributes.equals(node.attributes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, type, label, attributes);
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

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Edge)) {
                return false;
            }
            Edge edge = (Edge) other;
            return from.equals(edge.from) && to.equals(edge.to) && type == edge.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to, type);
        }
    }
}
