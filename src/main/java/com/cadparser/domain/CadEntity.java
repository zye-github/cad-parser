package com.cadparser.domain;

import java.util.List;
import java.util.Map;

public record CadEntity(
        String type,
        String handle,
        String layer,
        Integer color,
        List<CadPoint> points,
        Map<String, Object> properties
) {
}
