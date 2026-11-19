package com.netscope.analysis;

public enum RiskLevel {
    LOW_RISK("Low Risk", "#00c853"),
    SOME_RISK("Some Risk", "#ffb300"),
    RISKY("Risky", "#d50000");

    private final String label;
    private final String color;

    RiskLevel(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }

    public static RiskLevel fromScore(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Risk score must be between 0 and 100");
        }
        if (score < 20) {
            return LOW_RISK;
        }
        if (score < 50) {
            return SOME_RISK;
        }
        return RISKY;
    }
}
