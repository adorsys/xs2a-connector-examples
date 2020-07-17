package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.internal;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiCommonPaymentTOMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.psd2.xs2a.core.profile.PaymentType;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPaymentInfo;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentInternalGeneral {
    private final GeneralPaymentService paymentService;
    private final LedgersSpiCommonPaymentTOMapper ledgersSpiCommonPaymentTOMapper;

    public SCAPaymentResponseTO initiatePaymentInternal(SpiPayment payment, byte[] initialAspspConsentData) {
        PaymentType paymentType = payment.getPaymentType();
        PaymentTO paymentTO = ledgersSpiCommonPaymentTOMapper.mapToPaymentTO(paymentType, (SpiPaymentInfo) payment);

        return paymentService.initiatePaymentInternal(payment, initialAspspConsentData, PaymentTypeTO.valueOf(paymentType.toString()), paymentTO);
    }
}
