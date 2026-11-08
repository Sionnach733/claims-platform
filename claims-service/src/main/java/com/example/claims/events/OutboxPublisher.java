package com.example.claims.events;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import tools.jackson.databind.json.JsonMapper;

/**
 * Transactional-outbox relay: sends unpublished claim_status_history rows to SQS.
 *
 * <ul>
 *   <li>The status change and its outbox row commit atomically, so an event is never lost if SQS
 *       is down. It is just sent on a later poll.</li>
 *   <li>{@code FOR UPDATE SKIP LOCKED} lets several instances poll at once without double-sending.</li>
 *   <li>Delivery is at-least-once (a crash between send and commit resends), so each event carries a
 *       stable {@code eventId} for consumers to de-duplicate on.</li>
 * </ul>
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxStore store;
    private final TransactionTemplate tx;
    private final SqsClient sqs;
    private final JsonMapper json;
    private final EventProperties properties;
    private final Clock clock;
    private final Counter published;
    private final Counter failures;
    private volatile String queueUrl;

    OutboxPublisher(OutboxStore store, TransactionTemplate tx, SqsClient sqs, JsonMapper json,
                    EventProperties properties, Clock clock, MeterRegistry meters) {
        this.store = store;
        this.tx = tx;
        this.sqs = sqs;
        this.json = json;
        this.properties = properties;
        this.clock = clock;
        this.published = meters.counter("claims.events.published");
        this.failures = meters.counter("claims.events.publish.failures");
    }

    @Scheduled(fixedDelayString = "${app.events.poll-interval:2s}", initialDelayString = "${app.events.poll-interval:2s}")
    public void poll() {
        if (!properties.publisherEnabled()) {
            return;
        }
        try {
            int sent;
            do {
                sent = publishBatch();
            } while (sent == properties.batchSize()); // drain a backlog, then wait for the next tick
        } catch (RuntimeException e) {
            failures.increment();
            log.warn("Outbox publish failed; unsent events will be retried on the next poll: {}", e.toString());
        }
    }

    /**
     * Publishes one batch and returns how many events were sent. If a send fails part-way, the rows
     * already sent are still marked and committed (so they aren't re-sent), and the failure is
     * rethrown only after the commit.
     */
    public int publishBatch() {
        BatchResult result = tx.execute(status -> {
            List<OutboxRecord> rows = store.lockPending(properties.batchSize());
            List<Long> sentIds = new ArrayList<>();
            RuntimeException failure = null;
            for (OutboxRecord row : rows) {
                try {
                    send(row.toEvent());
                    sentIds.add(row.id());
                } catch (RuntimeException e) {
                    failure = e;
                    break; // preserve order: don't send this claim's later events ahead of this one
                }
            }
            store.markPublished(sentIds, clock.instant());
            return new BatchResult(sentIds.size(), failure);
        });
        if (result == null) {
            return 0;
        }
        if (result.failure() != null) {
            throw result.failure();
        }
        return result.sent();
    }

    private void send(ClaimStatusChangedEvent event) {
        sqs.sendMessage(b -> b
                .queueUrl(queueUrl())
                .messageBody(json.writeValueAsString(event))
                .messageAttributes(Map.of(
                        "eventType", stringAttr(ClaimStatusChangedEvent.TYPE),
                        "toStatus", stringAttr(event.toStatus().name()))));
        published.increment();
        log.debug("Published {} eventId={} claimNumber={}", ClaimStatusChangedEvent.TYPE, event.eventId(),
                event.claimNumber());
    }

    private String queueUrl() {
        if (queueUrl == null) {
            queueUrl = sqs.getQueueUrl(b -> b.queueName(properties.queueName())).queueUrl();
        }
        return queueUrl;
    }

    private static MessageAttributeValue stringAttr(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }

    private record BatchResult(int sent, RuntimeException failure) {
    }
}
