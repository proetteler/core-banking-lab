package ch.corebankinglab.integration.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TransferRequest(
        @NotBlank @Size(max = 20) String fromAccountNumber,
        @NotBlank @Size(max = 20) String toAccountNumber,
        @NotNull @Positive @Digits(integer = 16, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO currency code") String currency,
        @NotBlank @Size(max = 50) String reference) {
}
