package ch.corebankinglab.integration.error;

/** Fachlicher Fehler aus der Banklogik. RuntimeException, damit @Transactional zurückrollt. */
public class BankException extends RuntimeException {

    private final BankError error;

    public BankException(BankError error, String dbMessage) {
        // Die DB-Meldung bleibt nur für das Log, sie geht nicht an den Client
        super(dbMessage);
        this.error = error;
    }

    public BankError error() {
        return error;
    }
}
