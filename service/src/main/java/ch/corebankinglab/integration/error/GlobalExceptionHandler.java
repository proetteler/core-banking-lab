package ch.corebankinglab.integration.error;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Zentrales Fehler-Mapping als ProblemDetail (RFC 9457). */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_PREFIX = "urn:core-banking-lab:problem:";

    @ExceptionHandler(BankException.class)
    public ProblemDetail handleBankException(BankException ex) {
        BankError error = ex.error();
        log.info("Business error {}: {}", error, ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(error.status(), error.detail());
        problem.setType(URI.create(TYPE_PREFIX + error.slug()));
        problem.setTitle(error.status().getReasonPhrase());
        problem.setProperty("errorCode", error.name());
        return problem;
    }

    /** Bean Validation: Format- und Pflichtfehler im Request, noch vor dem DB-Aufruf. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setType(URI.create(TYPE_PREFIX + "validation-failed"));
        problem.setTitle("Bad Request");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** Alles andere: 500 ohne interne Details, die Ursache steht nur im Log. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error, please try again later");
        problem.setType(URI.create(TYPE_PREFIX + "internal-error"));
        problem.setTitle("Internal Server Error");
        return problem;
    }
}
