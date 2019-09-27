package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.internal;

import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.psd2.xs2a.core.profile.PaymentType;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiBulkPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentInternalGeneral {
    private final PaymentInternalSingle paymentInternalSingle;
    private final PaymentInternalPeriodic paymentInternalPeriodic;
    private final PaymentInternalBulk paymentInternalBulk;

    public <P extends SpiPayment> SCAPaymentResponseTO initiatePaymentInternal(P payment, byte[] initialAspspConsentData) {
        PaymentType paymentType = payment.getPaymentType();
        switch (paymentType) {
            case SINGLE:
                return paymentInternalSingle.initiatePaymentInternal((SpiSinglePayment) payment, initialAspspConsentData);
            case BULK:
                return paymentInternalBulk.initiatePaymentInternal((SpiBulkPayment) payment, initialAspspConsentData);
            case PERIODIC:
                return paymentInternalPeriodic.initiatePaymentInternal((SpiPeriodicPayment) payment, initialAspspConsentData);
            default:
                throw new IllegalArgumentException(String.format("Unknown payment type: %s", paymentType.getValue()));
        }
    }
}
