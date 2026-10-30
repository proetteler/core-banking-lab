package ch.corebankinglab.integration.api;

import ch.corebankinglab.integration.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Bookings")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Deposit cash onto an account (debit cash, credit account)")
    public BookingResponse deposit(@Valid @RequestBody DepositRequest request) {
        long id = service.deposit(request.accountNumber(), request.amount(), request.currency(), request.reference());
        return new BookingResponse(id);
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Transfer money between two accounts")
    public BookingResponse transfer(@Valid @RequestBody TransferRequest request) {
        long id = service.transfer(request.fromAccountNumber(), request.toAccountNumber(),
                request.amount(), request.currency(), request.reference());
        return new BookingResponse(id);
    }
}
