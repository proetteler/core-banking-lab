package ch.corebankinglab.integration.api;

import ch.corebankinglab.integration.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Accounts")
public class AccountController {

    private final BookingService service;

    public AccountController(BookingService service) {
        this.service = service;
    }

    @GetMapping("/accounts/{accountNumber}/balance")
    @Operation(summary = "Get the current balance of an account")
    public BalanceResponse balance(@PathVariable String accountNumber) {
        return new BalanceResponse(accountNumber, service.getBalance(accountNumber));
    }
}
