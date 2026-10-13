package com.cadparser.parse.dxf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DxfObject {

    private final String type;
    private final List<DxfGroup> groups = new ArrayList<>();

    DxfObject(String type) {
        this.type = type;
    }

    String type() {
        return type;
    }

    void add(DxfGroup group) {
        groups.add(group);
    }

    List<DxfGroup> groups() {
        return groups;
    }

    String first(int code) {
        for (DxfGroup group : groups) {
            if (group.code() == code) {
                return group.value();
            }
        }
        return null;
    }

    List<String> all(int code) {
        List<String> values = new ArrayList<>();
        for (DxfGroup group : groups) {
            if (group.code() == code) {
                values.add(group.value());
            }
        }
        return values;
    }

    Map<Integer, List<String>> grouped() {
        Map<Integer, List<String>> map = new LinkedHashMap<>();
        for (DxfGroup group : groups) {
            map.computeIfAbsent(group.code(), key -> new ArrayList<>()).add(group.value());
        }
        return map;
    }
}
