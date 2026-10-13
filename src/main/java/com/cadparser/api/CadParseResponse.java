package com.cadparser.api;

import com.cadparser.domain.CadBounds;
import com.cadparser.domain.CadDocument;
import com.cadparser.domain.CadEntity;
import com.cadparser.domain.CadFormat;
import com.cadparser.domain.CadLayer;

import java.util.List;

public record CadParseResponse(
        CadFormat format,
        String version,
        String sourceFileName,
        boolean convertedFromDwg,
        int entityCount,
        int layerCount,
        CadBounds bounds,
        List<CadLayer> layers,
        List<CadEntity> entities
) {

    public static CadParseResponse from(CadDocument document) {
        return new CadParseResponse(
                document.format(),
                document.version(),
                document.sourceFileName(),
                document.convertedFromDwg(),
                document.entityCount(),
                document.layers().size(),
                document.bounds(),
                document.layers(),
                document.entities()
        );
    }
}
