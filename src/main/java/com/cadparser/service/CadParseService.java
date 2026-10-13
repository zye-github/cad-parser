package com.cadparser.service;

import com.cadparser.config.CadProperties;
import com.cadparser.convert.DwgToDxfConverter;
import com.cadparser.detect.CadFormatDetector;
import com.cadparser.domain.CadDocument;
import com.cadparser.domain.CadFormat;
import com.cadparser.parse.CadParseException;
import com.cadparser.parse.dxf.DxfParser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class CadParseService {

    private final CadFormatDetector detector;
    private final DxfParser dxfParser;
    private final DwgToDxfConverter dwgToDxfConverter;
    private final CadProperties properties;

    public CadParseService(
            CadFormatDetector detector,
            DxfParser dxfParser,
            DwgToDxfConverter dwgToDxfConverter,
            CadProperties properties
    ) {
        this.detector = detector;
        this.dxfParser = dxfParser;
        this.dwgToDxfConverter = dwgToDxfConverter;
        this.properties = properties;
    }

    public CadDocument parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CadParseException("Upload a .dxf or .dwg file");
        }
        String name = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        try {
            byte[] bytes = file.getBytes();
            CadFormat format = detector.detect(name, new ByteArrayInputStream(bytes));
            return parse(name, format, bytes);
        } catch (IOException e) {
            throw new CadParseException("Failed to read uploaded file", e);
        }
    }

    public CadDocument parse(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            CadFormat format = detector.detect(file.getFileName().toString(), new ByteArrayInputStream(bytes));
            return parse(file.getFileName().toString(), format, bytes);
        } catch (IOException e) {
            throw new CadParseException("Failed to read " + file, e);
        }
    }

    public String dwgStatus() {
        return dwgToDxfConverter.describeAvailability();
    }

    private CadDocument parse(String name, CadFormat format, byte[] bytes) throws IOException {
        int maxEntities = properties.getParse().getMaxEntities();
        return switch (format) {
            case DXF -> dxfParser.parse(name, new ByteArrayInputStream(bytes), maxEntities);
            case DWG -> parseDwg(name, bytes, maxEntities);
            case UNKNOWN -> throw new CadParseException(
                    "Unrecognized CAD format for " + name + ". Upload ASCII DXF or DWG."
            );
        };
    }

    private CadDocument parseDwg(String name, byte[] bytes, int maxEntities) throws IOException {
        Path work = Files.createTempDirectory("cad-dwg-");
        Path dwg = work.resolve(safeName(name, ".dwg"));
        Files.write(dwg, bytes);
        Path dxf = dwgToDxfConverter.convert(dwg);
        try (InputStream in = Files.newInputStream(dxf)) {
            CadDocument document = dxfParser.parse(name, in, maxEntities);
            return new CadDocument(
                    CadFormat.DXF,
                    document.version(),
                    name,
                    true,
                    document.layers(),
                    document.entities(),
                    document.bounds()
            );
        }
    }

    private String safeName(String name, String fallbackExt) {
        String base = Path.of(name).getFileName().toString();
        if (!base.toLowerCase().endsWith(fallbackExt)) {
            return base + fallbackExt;
        }
        return base;
    }
}
