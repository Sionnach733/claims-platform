package com.example.claims.service;

/** Maps to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, String id) {
        super("%s '%s' was not found".formatted(resource, id));
    }
}
