package com.bguzman.civlint.support;

/**
 * Thrown when untrusted JSON cannot be parsed, or breaches a documented safety bound.
 */
public class JsonParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public JsonParseException(String message) {
        super(message);
    }
}
