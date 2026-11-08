package com.example.claims.api.dto;

import com.example.claims.domain.Provider;

public record ProviderResponse(String npi, String name, String specialty, boolean inNetwork) {

    public static ProviderResponse from(Provider p) {
        return new ProviderResponse(p.getNpi(), p.getName(), p.getSpecialty(), p.isInNetwork());
    }
}
