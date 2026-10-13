package com.cadparser.domain;

import java.util.List;

public record CadBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    public static CadBounds from(List<CadEntity> entities) {
        boolean any = false;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (CadEntity entity : entities) {
            for (CadPoint point : entity.points()) {
                any = true;
                minX = Math.min(minX, point.x());
                minY = Math.min(minY, point.y());
                minZ = Math.min(minZ, point.z());
                maxX = Math.max(maxX, point.x());
                maxY = Math.max(maxY, point.y());
                maxZ = Math.max(maxZ, point.z());
            }
        }
        if (!any) {
            return new CadBounds(0, 0, 0, 0, 0, 0);
        }
        return new CadBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
