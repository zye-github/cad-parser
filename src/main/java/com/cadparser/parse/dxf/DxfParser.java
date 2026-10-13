package com.cadparser.parse.dxf;

import com.cadparser.domain.CadBounds;
import com.cadparser.domain.CadDocument;
import com.cadparser.domain.CadEntity;
import com.cadparser.domain.CadFormat;
import com.cadparser.domain.CadLayer;
import com.cadparser.domain.CadPoint;
import com.cadparser.parse.CadParseException;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * ASCII DXF parser. New entity types belong in {@link #toEntity(DxfObject)}.
 */
@Component
public class DxfParser {

    public CadDocument parse(String sourceFileName, InputStream in, int maxEntities) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return parse(sourceFileName, reader, maxEntities, false);
        } catch (IOException e) {
            throw new CadParseException("Failed to read DXF stream", e);
        }
    }

    CadDocument parse(String sourceFileName, BufferedReader reader, int maxEntities, boolean convertedFromDwg)
            throws IOException {
        String section = null;
        String table = null;
        String version = "UNKNOWN";
        List<CadLayer> layers = new ArrayList<>();
        List<CadEntity> entities = new ArrayList<>();
        DxfObject current = null;
        DxfObject polyline = null;
        List<CadPoint> polylineVertices = new ArrayList<>();

        while (true) {
            Optional<DxfGroup> maybe = nextGroup(reader);
            if (maybe.isEmpty()) {
                break;
            }
            DxfGroup group = maybe.get();
            if (group.code() != 0) {
                if (current != null) {
                    current.add(group);
                }
                if ("HEADER".equals(section) && group.code() == 9 && "$ACADVER".equalsIgnoreCase(group.value())) {
                    Optional<DxfGroup> ver = nextGroup(reader);
                    if (ver.isPresent()) {
                        version = ver.get().value();
                    }
                }
                continue;
            }

            flushObject(section, table, current, layers, entities, polyline, polylineVertices, maxEntities);
            String type = group.value().trim().toUpperCase(Locale.ROOT);
            switch (type) {
                case "SECTION" -> {
                    Optional<DxfGroup> name = nextGroup(reader);
                    section = name.map(DxfGroup::value).orElse("");
                    current = null;
                }
                case "ENDSEC" -> {
                    section = null;
                    table = null;
                    current = null;
                }
                case "TABLE" -> {
                    Optional<DxfGroup> name = nextGroup(reader);
                    table = name.map(DxfGroup::value).orElse("");
                    current = null;
                }
                case "ENDTAB" -> {
                    table = null;
                    current = null;
                }
                case "EOF" -> {
                    current = null;
                }
                case "VERTEX" -> {
                    current = new DxfObject(type);
                    if (polyline == null) {
                        polyline = new DxfObject("POLYLINE");
                    }
                }
                case "SEQEND" -> {
                    if (polyline != null) {
                        entities.add(polylineEntity(polyline, polylineVertices));
                        if (entities.size() > maxEntities) {
                            throw new CadParseException("Entity count exceeds limit " + maxEntities);
                        }
                    }
                    polyline = null;
                    polylineVertices = new ArrayList<>();
                    current = null;
                }
                default -> {
                    current = new DxfObject(type);
                    if ("POLYLINE".equals(type) || "POLYLINE".equalsIgnoreCase(type)) {
                        polyline = current;
                        polylineVertices = new ArrayList<>();
                    }
                }
            }
        }
        flushObject(section, table, current, layers, entities, polyline, polylineVertices, maxEntities);
        if (polyline != null && !polylineVertices.isEmpty()) {
            entities.add(polylineEntity(polyline, polylineVertices));
        }
        return new CadDocument(
                CadFormat.DXF,
                version,
                sourceFileName,
                convertedFromDwg,
                List.copyOf(layers),
                List.copyOf(entities),
                CadBounds.from(entities)
        );
    }

    private void flushObject(
            String section,
            String table,
            DxfObject current,
            List<CadLayer> layers,
            List<CadEntity> entities,
            DxfObject polyline,
            List<CadPoint> polylineVertices,
            int maxEntities
    ) {
        if (current == null) {
            return;
        }
        if ("TABLES".equals(section) && "LAYER".equalsIgnoreCase(table) && "LAYER".equals(current.type())) {
            layers.add(toLayer(current));
            return;
        }
        if ("ENTITIES".equals(section)) {
            if ("VERTEX".equals(current.type())) {
                polylineVertices.add(point(current, 10, 20, 30));
                return;
            }
            if ("POLYLINE".equals(current.type())) {
                return;
            }
            CadEntity entity = toEntity(current);
            if (entity != null) {
                entities.add(entity);
                if (entities.size() > maxEntities) {
                    throw new CadParseException("Entity count exceeds limit " + maxEntities);
                }
            }
        }
    }

    /**
     * Map a DXF object in ENTITIES to a {@link CadEntity}. Add new CAD types here.
     */
    CadEntity toEntity(DxfObject object) {
        return switch (object.type()) {
            case "LINE" -> line(object);
            case "POINT" -> simple(object, List.of(point(object, 10, 20, 30)));
            case "CIRCLE" -> circle(object);
            case "ARC" -> arc(object);
            case "LWPOLYLINE" -> lwPolyline(object);
            case "TEXT", "MTEXT" -> text(object);
            case "INSERT" -> insert(object);
            case "ELLIPSE" -> ellipse(object);
            case "SPLINE" -> spline(object);
            case "SOLID", "TRACE", "3DFACE" -> simple(object, collectPairs(object, 10, 11, 12, 13));
            default -> simple(object, collectKnownPoints(object));
        };
    }

    private CadEntity line(DxfObject object) {
        return simple(object, List.of(point(object, 10, 20, 30), point(object, 11, 21, 31)));
    }

    private CadEntity circle(DxfObject object) {
        Map<String, Object> props = baseProps(object);
        props.put("radius", num(object.first(40)));
        return entity(object, List.of(point(object, 10, 20, 30)), props);
    }

    private CadEntity arc(DxfObject object) {
        Map<String, Object> props = baseProps(object);
        props.put("radius", num(object.first(40)));
        props.put("startAngle", num(object.first(50)));
        props.put("endAngle", num(object.first(51)));
        return entity(object, List.of(point(object, 10, 20, 30)), props);
    }

    private CadEntity lwPolyline(DxfObject object) {
        Map<String, Object> props = baseProps(object);
        int flags = (int) num(object.first(70));
        props.put("closed", (flags & 1) == 1);
        props.put("bulges", object.all(42).stream().map(this::num).toList());
        List<String> xs = object.all(10);
        List<String> ys = object.all(20);
        List<CadPoint> points = new ArrayList<>();
        int n = Math.min(xs.size(), ys.size());
        for (int i = 0; i < n; i++) {
            points.add(new CadPoint(num(xs.get(i)), num(ys.get(i)), 0));
        }
        return entity(object, points, props);
    }

    private CadEntity text(DxfObject object) {
        Map<String, Object> props = baseProps(object);
        props.put("text", joinText(object));
        props.put("height", num(object.first(40)));
        props.put("rotation", num(object.first(50)));
        return entity(object, List.of(point(object, 10, 20, 30)), props);
    }

    private CadEntity insert(DxfObject object) {
        Map<String, Object> props = baseProps(object);
        props.put("block", object.first(2));
        props.put("scaleX", num(object.first(41), 1));
        props.put("scaleY", num(object.first(42), 1));
        props.put("scaleZ", num(object.first(43), 1));
        props.put("rotation", num(object.first(50)));
        return entity(object, List.of(point(object, 10, 20, 30)), props);
    }

    private CadEntity ellipse(DxfObject object) {
        Map<String, Object> props = baseProps(object);
        props.put("majorAxis", point(object, 11, 21, 31));
        props.put("ratio", num(object.first(40)));
        props.put("startParam", num(object.first(41)));
        props.put("endParam", num(object.first(42)));
        return entity(object, List.of(point(object, 10, 20, 30)), props);
    }

    private CadEntity spline(DxfObject object) {
        Map<String, Object> props = baseProps(object);
        props.put("degree", (int) num(object.first(71)));
        return entity(object, collectKnownPoints(object), props);
    }

    private CadEntity polylineEntity(DxfObject object, List<CadPoint> vertices) {
        Map<String, Object> props = baseProps(object);
        int flags = (int) num(object.first(70));
        props.put("closed", (flags & 1) == 1);
        return entity(object, List.copyOf(vertices), props);
    }

    private CadEntity simple(DxfObject object, List<CadPoint> points) {
        return entity(object, points, baseProps(object));
    }

    private CadEntity entity(DxfObject object, List<CadPoint> points, Map<String, Object> props) {
        return new CadEntity(
                object.type(),
                object.first(5),
                Optional.ofNullable(object.first(8)).orElse("0"),
                color(object),
                List.copyOf(points),
                Map.copyOf(props)
        );
    }

    private Map<String, Object> baseProps(DxfObject object) {
        Map<String, Object> props = new LinkedHashMap<>();
        if (object.first(6) != null) {
            props.put("linetype", object.first(6));
        }
        return props;
    }

    private CadLayer toLayer(DxfObject object) {
        String name = Optional.ofNullable(object.first(2)).orElse("0");
        int flags = (int) num(object.first(70));
        return new CadLayer(name, color(object), (flags & 1) == 1, (flags & 4) == 4);
    }

    private Integer color(DxfObject object) {
        String value = object.first(62);
        if (value == null || value.isBlank()) {
            return null;
        }
        return (int) num(value);
    }

    private CadPoint point(DxfObject object, int xCode, int yCode, int zCode) {
        return new CadPoint(num(object.first(xCode)), num(object.first(yCode)), num(object.first(zCode)));
    }

    private List<CadPoint> collectPairs(DxfObject object, int... xCodes) {
        List<CadPoint> points = new ArrayList<>();
        for (int xCode : xCodes) {
            if (object.first(xCode) != null) {
                points.add(point(object, xCode, xCode + 10, xCode + 20));
            }
        }
        return points;
    }

    private List<CadPoint> collectKnownPoints(DxfObject object) {
        List<String> xs = object.all(10);
        List<String> ys = object.all(20);
        List<String> zs = object.all(30);
        List<CadPoint> points = new ArrayList<>();
        int n = Math.min(xs.size(), ys.size());
        for (int i = 0; i < n; i++) {
            double z = i < zs.size() ? num(zs.get(i)) : 0;
            points.add(new CadPoint(num(xs.get(i)), num(ys.get(i)), z));
        }
        return points;
    }

    private String joinText(DxfObject object) {
        StringBuilder text = new StringBuilder();
        for (String extra : object.all(3)) {
            text.append(extra);
        }
        if (object.first(1) != null) {
            text.append(object.first(1));
        }
        return text.toString();
    }

    private double num(String value) {
        return num(value, 0);
    }

    private double num(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Optional<DxfGroup> nextGroup(BufferedReader reader) throws IOException {
        String codeLine = nextNonNull(reader);
        if (codeLine == null) {
            return Optional.empty();
        }
        String valueLine = reader.readLine();
        if (valueLine == null) {
            throw new CadParseException("DXF group code without value: " + codeLine);
        }
        try {
            return Optional.of(new DxfGroup(Integer.parseInt(codeLine.trim()), valueLine.trim()));
        } catch (NumberFormatException e) {
            throw new CadParseException("Invalid DXF group code: " + codeLine, e);
        }
    }

    private String nextNonNull(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) {
                return line;
            }
        }
        return null;
    }
}
