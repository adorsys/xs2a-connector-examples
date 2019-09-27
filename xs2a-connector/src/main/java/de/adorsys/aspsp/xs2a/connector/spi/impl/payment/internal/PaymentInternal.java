package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.internal;

import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;

public interface PaymentInternal<P extends SpiPayment> {
    SCAPaymentResponseTO initiatePaymentInternal(P payment, byte[] initialAspspConsentData);
}
