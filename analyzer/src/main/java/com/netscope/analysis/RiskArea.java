package com.netscope.analysis;

public enum RiskArea {
    REPUTATION(50),
    REDIRECTS(15),
    DOMAIN(15),
    CONNECTION(10),
    PAGE_INDICATORS(30);

    private final int pointCap;

    RiskArea(int pointCap) {
        this.pointCap = pointCap;
    }

    public int getPointCap() {
        return pointCap;
    }
}
