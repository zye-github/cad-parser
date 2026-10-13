package com.cadparser.detect;

import com.cadparser.domain.CadFormat;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class CadFormatDetector {

    public CadFormat detect(String fileName, InputStream in) throws IOException {
        byte[] header = in.readNBytes(64);
        CadFormat fromMagic = detectMagic(header);
        if (fromMagic != CadFormat.UNKNOWN) {
            return fromMagic;
        }
        return detectByName(fileName);
    }

    public CadFormat detectByName(String fileName) {
        if (fileName == null) {
            return CadFormat.UNKNOWN;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".dxf")) {
            return CadFormat.DXF;
        }
        if (lower.endsWith(".dwg")) {
            return CadFormat.DWG;
        }
        return CadFormat.UNKNOWN;
    }

    private CadFormat detectMagic(byte[] header) {
        if (header.length >= 6) {
            String ascii = new String(header, 0, Math.min(header.length, 22), StandardCharsets.US_ASCII);
            if (ascii.startsWith("AC10")) {
                return CadFormat.DWG;
            }
            if (ascii.startsWith("AutoCAD Binary DXF")) {
                throw new com.cadparser.parse.CadParseException("Binary DXF is not supported. Save as ASCII DXF.");
            }
        }
        String text = new String(header, StandardCharsets.US_ASCII);
        if (text.contains("SECTION") || text.contains("0\r\nSECTION") || text.contains("0\nSECTION")) {
            return CadFormat.DXF;
        }
        return CadFormat.UNKNOWN;
    }
}
