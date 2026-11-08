package com.example.claims.observability;

import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Forwards the current correlation ID on outbound HTTP calls (e.g. to fraud-service). */
public class CorrelationIdPropagator implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        CorrelationId.current().ifPresent(id -> request.getHeaders().set(CorrelationId.HEADER, id));
        return execution.execute(request, body);
    }
}
