package com.example.claims.api.error;

import com.example.claims.observability.CorrelationId;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** One factory for every RFC 7807 body, so all errors share a shape and carry the correlation ID. */
public final class Problems {

    public static final String TYPE_BASE = "https://claims.example.com/problems/";

    private Problems() {
    }

    public static ProblemDetail create(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + type));
        problem.setTitle(title);
        CorrelationId.current().ifPresent(id -> problem.setProperty("correlationId", id));
        return problem;
    }
}
