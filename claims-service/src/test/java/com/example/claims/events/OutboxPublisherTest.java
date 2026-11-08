package com.example.claims.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.claims.domain.ClaimStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import tools.jackson.databind.json.JsonMapper;

class OutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

    private final OutboxStore store = mock(OutboxStore.class);
    private final SqsClient sqs = mock(SqsClient.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private OutboxPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        var tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
        publisher = new OutboxPublisher(store, tx, sqs, JsonMapper.builder().findAndAddModules().build(),
                new EventProperties(true, "claim-status-events", 10), Clock.fixed(NOW, ZoneOffset.UTC), meters);
        when(sqs.getQueueUrl(any(Consumer.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl("http://sqs/q").build());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsEachPendingRowAndMarksThemPublished() {
        when(store.lockPending(10)).thenReturn(List.of(row(1), row(2)));
        when(sqs.sendMessage(any(Consumer.class))).thenReturn(SendMessageResponse.builder().build());

        int sent = publisher.publishBatch();

        assertThat(sent).isEqualTo(2);
        verify(store).markPublished(List.of(1L, 2L), NOW);
        assertThat(meters.counter("claims.events.published").count()).isEqualTo(2.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void partialFailureStillMarksTheRowsThatWereSentAndStopsTheBatch() {
        when(store.lockPending(10)).thenReturn(List.of(row(1), row(2), row(3)));
        when(sqs.sendMessage(any(Consumer.class)))
                .thenReturn(SendMessageResponse.builder().build())
                .thenThrow(SdkClientException.create("SQS unavailable"));

        assertThatThrownBy(() -> publisher.publishBatch()).isInstanceOf(SdkClientException.class);

        verify(store).markPublished(List.of(1L), NOW);       // row 1 won't be re-sent
        verify(sqs, times(2)).sendMessage(any(Consumer.class)); // row 3 not attempted (ordering)
    }

    @Test
    @SuppressWarnings("unchecked")
    void pollSwallowsFailuresSoTheSchedulerKeepsRunning() {
        when(store.lockPending(10)).thenReturn(List.of(row(1)));
        when(sqs.sendMessage(any(Consumer.class))).thenThrow(SdkClientException.create("down"));

        publisher.poll();

        assertThat(meters.counter("claims.events.publish.failures").count()).isEqualTo(1.0);
        verify(store).markPublished(List.of(), NOW);
    }

    @Test
    void disabledPublisherDoesNothing() {
        var disabled = new OutboxPublisher(store, new TransactionTemplate(mock(PlatformTransactionManager.class)),
                sqs, JsonMapper.builder().build(), new EventProperties(false, "q", 10), Clock.systemUTC(), meters);

        disabled.poll();

        verify(store, never()).lockPending(eq(10));
        verify(store, never()).markPublished(anyList(), any());
    }

    @Test
    void eventCarriesNoMemberIdentifiers() {
        var event = row(1).toEvent();
        String body = JsonMapper.builder().findAndAddModules().build().writeValueAsString(event);

        assertThat(body).contains("\"claimNumber\":\"CLM-2026-00000001\"", "\"eventType\":\"ClaimStatusChanged\"")
                .doesNotContain("memberId", "AET");
    }

    private static OutboxRecord row(long id) {
        return new OutboxRecord(id, UUID.randomUUID(), "CLM-2026-00000001", ClaimStatus.SUBMITTED,
                ClaimStatus.APPROVED, "auto", "system", NOW);
    }
}
