package com.cadparser.parse.dxf;

record DxfGroup(int code, String value) {

    boolean isType(String type) {
        return code == 0 && type.equalsIgnoreCase(value.trim());
    }
}
