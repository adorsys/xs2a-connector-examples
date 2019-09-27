package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.internal;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.ledgers.middleware.api.domain.payment.BulkPaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiBulkPayment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentInternalBulk implements PaymentInternal<SpiBulkPayment> {
    private final LedgersSpiPaymentMapper paymentMapper;
    private final GeneralPaymentService paymentService;

    @Override
    public SCAPaymentResponseTO initiatePaymentInternal(SpiBulkPayment payment, byte[] initialAspspConsentData) {
        BulkPaymentTO request = paymentMapper.toBulkPaymentTO(payment);
        if (request.getPaymentProduct() == null) {
            request.setPaymentProduct(paymentService.getSCAPaymentResponseTO(initialAspspConsentData).getPaymentProduct());
        }
        return paymentService.initiatePaymentInternal(payment, initialAspspConsentData, PaymentTypeTO.BULK, request);
    }
}
