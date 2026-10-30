package ch.corebankinglab.integration.payment;

/**
 * Nachricht ist nicht verarbeitbar (kein gültiges JSON, keine Referenz). Ein Retry würde dasselbe
 * Ergebnis liefern, darum geht sie ohne Wiederholung direkt ins Dead-Letter-Topic.
 */
public class PoisonMessageException extends RuntimeException {

    public PoisonMessageException(String message, Throwable cause) {
        super(message, cause);
    }

    public PoisonMessageException(String message) {
        super(message);
    }
}
