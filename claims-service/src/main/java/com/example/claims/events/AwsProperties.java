package com.example.claims.events;

import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param region   AWS region, e.g. eu-west-1
 * @param endpoint optional endpoint override (LocalStack). Leave empty in real AWS.
 */
@Validated
@ConfigurationProperties("app.aws")
public record AwsProperties(@NotBlank String region, URI endpoint) {
}
