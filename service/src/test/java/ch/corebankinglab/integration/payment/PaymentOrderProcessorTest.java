package ch.corebankinglab.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import ch.corebankinglab.integration.error.BankError;
import ch.corebankinglab.integration.error.BankException;
import ch.corebankinglab.integration.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.QueryTimeoutException;

class PaymentOrderProcessorTest {

    private static final String ORDER = """
            {"reference":"ORD-1","fromAccountNumber":"A","toAccountNumber":"B","amount":50.00,"currency":"CHF"}""";

    private final BookingService bookingService = mock(BookingService.class);
    private PaymentOrderProcessor processor;

    @BeforeEach
    void setUp() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        processor = new PaymentOrderProcessor(new ObjectMapper(), validator, bookingService);
    }

    @Test
    void successfulTransferIsBooked() {
        when(bookingService.transfer(anyString(), anyString(), any(), anyString(), anyString())).thenReturn(1L);

        PaymentResult result = processor.process(ORDER);

        assertThat(result).isEqualTo(PaymentResult.booked("ORD-1"));
        verify(bookingService).transfer("A", "B", new BigDecimal("50.00"), "CHF", "ORD-1");
    }

    @Test
    void duplicateReferenceIsDuplicateNotRejected() {
        when(bookingService.transfer(anyString(), anyString(), any(), anyString(), anyString()))
                .thenThrow(new BankException(BankError.DUPLICATE_REFERENCE, "db"));

        assertThat(processor.process(ORDER)).isEqualTo(PaymentResult.duplicate("ORD-1"));
    }

    @ParameterizedTest
    @EnumSource(value = BankError.class, names = "DUPLICATE_REFERENCE", mode = EnumSource.Mode.EXCLUDE)
    void everyOtherBusinessErrorIsRejectedWithItsCode(BankError error) {
        when(bookingService.transfer(anyString(), anyString(), any(), anyString(), anyString()))
                .thenThrow(new BankException(error, "db"));

        assertThat(processor.process(ORDER)).isEqualTo(PaymentResult.rejected("ORD-1", error.name()));
    }

    @Test
    void invalidFieldsAreRejectedWithoutCallingTheDatabase() {
        String order = """
                {"reference":"ORD-2","fromAccountNumber":"A","toAccountNumber":"B","amount":-5,"currency":"chf"}""";

        PaymentResult result = processor.process(order);

        assertThat(result).isEqualTo(PaymentResult.rejected("ORD-2", "INVALID_ORDER"));
        verify(bookingService, never()).transfer(anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void technicalErrorsAreNotSwallowedSoTheyCanBeRetried() {
        when(bookingService.transfer(anyString(), anyString(), any(), anyString(), anyString()))
                .thenThrow(new QueryTimeoutException("db down"));

        assertThatThrownBy(() -> processor.process(ORDER)).isInstanceOf(QueryTimeoutException.class);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "not json at all",
            "{\"reference\":\"ORD-3\",\"amount\":\"abc\"}",
            "{\"fromAccountNumber\":\"A\",\"toAccountNumber\":\"B\",\"amount\":5,\"currency\":\"CHF\"}",
            "{\"reference\":\" \",\"fromAccountNumber\":\"A\",\"toAccountNumber\":\"B\",\"amount\":5,\"currency\":\"CHF\"}",
            "null"
    })
    void unreadableOrdersArePoisonMessages(String payload) {
        assertThatThrownBy(() -> processor.process(payload)).isInstanceOf(PoisonMessageException.class);
    }

    @Test
    void nullValueIsPoisonMessage() {
        assertThatThrownBy(() -> processor.process(null)).isInstanceOf(PoisonMessageException.class);
    }
}
