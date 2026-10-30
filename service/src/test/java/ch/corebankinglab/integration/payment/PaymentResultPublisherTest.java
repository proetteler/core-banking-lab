package ch.corebankinglab.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

@SuppressWarnings("unchecked")
class PaymentResultPublisherTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-30T09:12:44.123Z"), ZoneOffset.UTC);
    private final PaymentResultPublisher publisher =
            new PaymentResultPublisher(kafka, mapper, clock, "results", 1000);

    @Test
    void publishesRejectedResultWithReferenceAsKey() throws Exception {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(PaymentResult.rejected("ORD-3", "INSUFFICIENT_FUNDS"));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("results"), eq("ORD-3"), payload.capture());
        JsonNode json = mapper.readTree(payload.getValue());
        assertThat(json.get("eventType").asText()).isEqualTo("PAYMENT_RESULT");
        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(json.get("occurredAt").asText()).isEqualTo("2026-10-30T09:12:44.123Z");
        assertThat(json.get("reference").asText()).isEqualTo("ORD-3");
        assertThat(json.get("status").asText()).isEqualTo("REJECTED");
        assertThat(json.get("errorCode").asText()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(json.get("eventId").asText()).hasSize(36);
    }

    @Test
    void bookedResultHasNoErrorCodeField() throws Exception {
        String json = publisher.toJson(PaymentResult.booked("ORD-1"));

        assertThat(mapper.readTree(json).has("errorCode")).isFalse();
        assertThat(mapper.readTree(json).get("status").asText()).isEqualTo("BOOKED");
    }

    @Test
    void failedSendThrowsSoTheOrderIsRetried() {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThatThrownBy(() -> publisher.publish(PaymentResult.booked("ORD-1")))
                .isInstanceOf(IllegalStateException.class);
    }
}
