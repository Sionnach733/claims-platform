package com.example.claims.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class AwsPropertiesTest {

    @Test
    void blankOrMissingEndpointMeansRealAws() {
        assertThat(new AwsProperties("eu-west-1", null).hasEndpointOverride()).isFalse();
        assertThat(new AwsProperties("eu-west-1", URI.create("")).hasEndpointOverride()).isFalse();
    }

    @Test
    void localStackEndpointIsAnOverride() {
        assertThat(new AwsProperties("eu-west-1", URI.create("http://localstack:4566")).hasEndpointOverride()).isTrue();
    }
}
