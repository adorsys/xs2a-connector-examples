package de.adorsys.aspsp.xs2a.connector.spi.impl.payment;

public class NotSupportedPaymentTypeException extends Exception {

    public NotSupportedPaymentTypeException(String message) {
        super(message);
    }
}
