package com.example.claims.service;

/** A request that is invalid in a way bean validation can't express (e.g. cross-field checks). Maps to HTTP 400. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
