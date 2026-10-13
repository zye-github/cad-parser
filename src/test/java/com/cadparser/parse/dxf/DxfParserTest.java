package com.cadparser.parse.dxf;

import com.cadparser.domain.CadDocument;
import com.cadparser.domain.CadEntity;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DxfParserTest {

    private final DxfParser parser = new DxfParser();

    @Test
    void parsesSampleDxfEntities() {
        InputStream in = getClass().getResourceAsStream("/fixtures/sample.dxf");
        assertThat(in).isNotNull();

        CadDocument document = parser.parse("sample.dxf", in, 10_000);

        assertThat(document.version()).isEqualTo("AC1027");
        assertThat(document.layers()).extracting("name").contains("0", "WALL");
        assertThat(document.entities()).extracting(CadEntity::type)
                .containsExactly("LINE", "CIRCLE", "ARC", "LWPOLYLINE", "TEXT");

        CadEntity line = document.entities().get(0);
        assertThat(line.layer()).isEqualTo("WALL");
        assertThat(line.points()).hasSize(2);
        assertThat(line.points().get(1).x()).isEqualTo(100.0);

        CadEntity circle = document.entities().get(1);
        assertThat(circle.properties()).containsEntry("radius", 5.0);

        CadEntity text = document.entities().get(4);
        assertThat(text.properties()).containsEntry("text", "Hello CAD");
        assertThat(document.bounds().maxX()).isEqualTo(100.0);
    }
}
