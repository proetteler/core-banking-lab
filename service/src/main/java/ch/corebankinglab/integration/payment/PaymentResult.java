package ch.corebankinglab.integration.payment;

/** Ergebnis einer Payment-Order. errorCode ist nur bei REJECTED gesetzt. */
public record PaymentResult(String reference, Status status, String errorCode) {

    public enum Status {
        /** Buchung wurde ausgeführt */
        BOOKED,
        /** Referenz ist schon gebucht, es wurde nicht nochmal gebucht */
        DUPLICATE,
        /** Fachlich abgelehnt, ein Retry würde dasselbe Ergebnis liefern */
        REJECTED
    }

    public static PaymentResult booked(String reference) {
        return new PaymentResult(reference, Status.BOOKED, null);
    }

    public static PaymentResult duplicate(String reference) {
        return new PaymentResult(reference, Status.DUPLICATE, null);
    }

    public static PaymentResult rejected(String reference, String errorCode) {
        return new PaymentResult(reference, Status.REJECTED, errorCode);
    }
}
