package com.smarttrader.v2.integrity;

/**
 * Thrown by DataIntegrityValidator when incoming data fails one of the section 10 checks
 * (stale data, candle sequence continuity, price gap).
 */
public class DataIntegrityException extends RuntimeException {

    private final DataIntegrityViolationType violationType;

    public DataIntegrityException(DataIntegrityViolationType violationType, String message) {
        super(message);
        this.violationType = violationType;
    }

    public DataIntegrityViolationType violationType() {
        return violationType;
    }
}
