package com.netscope.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RiskAssessment {
    private final String id;
    private final String url;
    private final int score;
    private final RiskLevel level;
    private final List<RiskConcern> concerns;

    RiskAssessment(String id, String url, int score, RiskLevel level, List<RiskConcern> concerns) {
        this.id = Objects.requireNonNull(id, "id");
        this.url = Objects.requireNonNull(url, "url");
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Risk score must be between 0 and 100");
        }
        this.score = score;
        this.level = Objects.requireNonNull(level, "level");
        this.concerns = Collections.unmodifiableList(new ArrayList<>(concerns));
    }

    public String getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public int getScore() {
        return score;
    }

    public RiskLevel getLevel() {
        return level;
    }

    public List<RiskConcern> getConcerns() {
        return concerns;
    }
}
