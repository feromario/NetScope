package com.netscope.analysis;

import java.util.Objects;

public final class RiskConcern {
    private final String id;
    private final RiskArea area;
    private final String description;
    private final String evidenceSource;
    private final int points;

    public RiskConcern(String id, RiskArea area, String description, String evidenceSource, int points) {
        this.id = requireText(id, "id");
        this.area = Objects.requireNonNull(area, "area");
        this.description = requireText(description, "description");
        this.evidenceSource = requireText(evidenceSource, "evidenceSource");
        if (points < 0 || points > 100) {
            throw new IllegalArgumentException("Concern points must be between 0 and 100");
        }
        this.points = points;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    public String getId() {
        return id;
    }

    public RiskArea getArea() {
        return area;
    }

    public String getDescription() {
        return description;
    }

    public String getEvidenceSource() {
        return evidenceSource;
    }

    public int getPoints() {
        return points;
    }
}
