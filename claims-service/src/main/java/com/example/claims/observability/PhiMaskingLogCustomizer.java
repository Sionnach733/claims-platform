package com.example.claims.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Applies {@link PhiMasker} to every string value in structured (JSON) log events: message, exception
 * text, MDC values. Registered via {@code logging.structured.json.customizer}.
 */
public class PhiMaskingLogCustomizer implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

    @Override
    public void customize(JsonWriter.Members<ILoggingEvent> members) {
        members.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, PhiMasker::mask));
    }
}
