package com.netscope.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RiskScorer {
    public RiskAssessment assess(String url, Collection<RiskConcern> concerns) {
        WebUrl.canonicalKey(url);
        Objects.requireNonNull(concerns, "concerns");

        int[] areaPoints = new int[RiskArea.values().length];
        List<String> concernIds = new ArrayList<>();
        List<RiskConcern> recordedConcerns = new ArrayList<>();

        for (RiskConcern concern : concerns) {
            Objects.requireNonNull(concern, "concern");
            if (concernIds.contains(concern.getId())) {
                throw new IllegalArgumentException("Duplicate concern id: " + concern.getId());
            }
            concernIds.add(concern.getId());
            int areaIndex = concern.getArea().ordinal();
            int current = areaPoints[areaIndex];
            int capped = Math.min(concern.getArea().getPointCap(), current + concern.getPoints());
            areaPoints[areaIndex] = capped;
            recordedConcerns.add(concern);
        }

        int score = 0;
        for (int points : areaPoints) {
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
