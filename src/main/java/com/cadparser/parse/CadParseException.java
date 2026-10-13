package com.cadparser.parse;

public class CadParseException extends RuntimeException {

    public CadParseException(String message) {
        super(message);
    }

    public CadParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
