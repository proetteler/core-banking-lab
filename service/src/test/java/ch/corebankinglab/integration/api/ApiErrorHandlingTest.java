package ch.corebankinglab.integration.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import ch.corebankinglab.integration.error.BankError;
import ch.corebankinglab.integration.error.BankException;
import ch.corebankinglab.integration.error.GlobalExceptionHandler;
import ch.corebankinglab.integration.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AccountController.class, BookingController.class})
@Import(GlobalExceptionHandler.class)
class ApiErrorHandlingTest {

    private static final String VALID_DEPOSIT = """
            {"accountNumber":"CUS-1001-01","amount":100.00,"currency":"CHF","reference":"T-1"}""";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    BookingService service;

    @Test
    void depositReturns201WithBookingId() throws Exception {
        when(service.deposit(anyString(), any(), anyString(), anyString())).thenReturn(42L);

        mvc.perform(post("/deposits").contentType(MediaType.APPLICATION_JSON).content(VALID_DEPOSIT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value(42));
    }

    @Test
    void balanceReturnsAmount() throws Exception {
        when(service.getBalance("CUS-1001-01")).thenReturn(new BigDecimal("700.00"));

        mvc.perform(get("/accounts/CUS-1001-01/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(700.00));
    }

    @Test
    void unknownAccountIs404ProblemDetail() throws Exception {
        when(service.getBalance("X")).thenThrow(new BankException(BankError.ACCOUNT_NOT_FOUND, "db text"));

        mvc.perform(get("/accounts/X/balance"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.type").value("urn:core-banking-lab:problem:account-not-found"));
    }

    @Test
    void duplicateReferenceIs409() throws Exception {
        when(service.deposit(anyString(), any(), anyString(), anyString()))
                .thenThrow(new BankException(BankError.DUPLICATE_REFERENCE, "db text"));

        mvc.perform(post("/deposits").contentType(MediaType.APPLICATION_JSON).content(VALID_DEPOSIT))
                .andExpect(status().isConflict());
    }

    @Test
    void insufficientFundsIs422AndDoesNotLeakDbMessage() throws Exception {
        when(service.transfer(anyString(), anyString(), any(), anyString(), anyString()))
                .thenThrow(new BankException(BankError.INSUFFICIENT_FUNDS, "Saldo 12345.67 deckt nicht"));

        mvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fromAccountNumber":"A","toAccountNumber":"B","amount":5,"currency":"CHF","reference":"T-2"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("12345.67"))));
    }

    @Test
    void invalidRequestIs400WithFieldErrors() throws Exception {
        mvc.perform(post("/deposits").contentType(MediaType.APPLICATION_JSON).content("""
                        {"accountNumber":"","amount":-5,"currency":"chf","reference":"T-3"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void unexpectedErrorIs500WithoutDetails() throws Exception {
        when(service.getBalance("X")).thenThrow(new IllegalStateException("jdbc:oracle:thin:@secret-host"));

        mvc.perform(get("/accounts/X/balance"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-host"))));
    }
}
