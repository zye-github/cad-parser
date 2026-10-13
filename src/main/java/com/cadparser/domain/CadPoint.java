package com.cadparser.domain;

public record CadPoint(double x, double y, double z) {

    public static CadPoint of(double x, double y) {
        return new CadPoint(x, y, 0);
    }
}
