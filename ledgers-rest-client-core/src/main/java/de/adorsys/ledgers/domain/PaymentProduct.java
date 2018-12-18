package de.adorsys.ledgers.domain;

public enum PaymentProduct {
    SEPA("sepa-credit-transfers"),
    INSTANT_SEPA("instant-sepa-credit-transfers"),
    TARGET2("target-2-payments"),
    CROSS_BORDER("cross-border-credit-transfers");

    private String value;

    PaymentProduct(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
