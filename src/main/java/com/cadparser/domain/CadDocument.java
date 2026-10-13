package com.cadparser.domain;

import java.util.List;

public record CadDocument(
        CadFormat format,
        String version,
        String sourceFileName,
        boolean convertedFromDwg,
        List<CadLayer> layers,
        List<CadEntity> entities,
        CadBounds bounds
) {

    public int entityCount() {
        return entities.size();
    }
}
