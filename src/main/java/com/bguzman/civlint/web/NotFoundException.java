package com.bguzman.civlint.web;

/**
 * Thrown when a request names a run, procedure or version that does not exist.
 */
public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }
}
