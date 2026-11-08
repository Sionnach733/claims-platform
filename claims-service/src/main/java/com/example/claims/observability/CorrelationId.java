package com.example.claims.observability;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * Request correlation ID: accepted from the caller (UI or gateway) when well-formed, otherwise generated.
 * Lives in the SLF4J MDC for the duration of a request, so every log line, every error body and every
 * downstream call carries it.
 *
 * <p>In a larger estate this would be W3C {@code traceparent} via Micrometer Tracing / OpenTelemetry; a plain
 * header keeps the idea visible and works across Java, Python and the browser with no agents.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /** Bounded and log-safe: blocks header-injection / log-forging via a crafted ID. */
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    private CorrelationId() {
    }

    public static String acceptOrGenerate(String incoming) {
        return incoming != null && VALID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
    }

    public static Optional<String> current() {
        return Optional.ofNullable(MDC.get(MDC_KEY));
    }
}
