package com.example.claims.events;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class AwsConfig {

    /**
     * In AWS (ECS) credentials come from the task role through the default provider chain, so
     * no keys live in config. With an endpoint override (LocalStack) we use LocalStack's dummy keys.
     */
    @Bean(destroyMethod = "close")
    SqsClient sqsClient(AwsProperties aws) {
        SqsClientBuilder builder = SqsClient.builder()
                .region(Region.of(aws.region()))
                .httpClient(UrlConnectionHttpClient.create());
        if (aws.endpoint() != null) {
            builder.endpointOverride(aws.endpoint())
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
        }
        return builder.build();
    }
}
