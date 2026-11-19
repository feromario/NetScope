package com.netscope.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class RiskScorer {
    public RiskAssessment assess(String url, Collection<RiskConcern> concerns) {
        WebUrl.canonicalKey(url);
        Objects.requireNonNull(concerns, "concerns");

        Map<RiskArea, Integer> areaPoints = new EnumMap<>(RiskArea.class);
        Set<String> concernIds = new HashSet<>();
        List<RiskConcern> recordedConcerns = new ArrayList<>();

        for (RiskConcern concern : concerns) {
            Objects.requireNonNull(concern, "concern");
            if (!concernIds.add(concern.getId())) {
                throw new IllegalArgumentException("Duplicate concern id: " + concern.getId());
            }
            int current = areaPoints.containsKey(concern.getArea()) ? areaPoints.get(concern.getArea()) : 0;
            int capped = Math.min(concern.getArea().getPointCap(), current + concern.getPoints());
            areaPoints.put(concern.getArea(), capped);
            recordedConcerns.add(concern);
        }

        int score = 0;
        for (int points : areaPoints.values()) {
            score += points;
        }
        score = Math.min(100, score);

        return new RiskAssessment(
                UUID.randomUUID().toString(),
                url,
                score,
                RiskLevel.fromScore(score),
                recordedConcerns);
    }
}
