package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.internal;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentToMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.StandardPaymentProductsResolverConnector;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.psd2.xs2a.core.profile.PaymentType;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPaymentInfo;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentInternalGeneral {
    private final StandardPaymentProductsResolverConnector standardPaymentProductsResolverConnector;
    private final LedgersSpiPaymentToMapper ledgersSpiPaymentToMapper;
    private final GeneralPaymentService paymentService;

    public SCAPaymentResponseTO initiatePaymentInternal(SpiPayment payment, byte[] initialAspspConsentData) {
        PaymentType paymentType = payment.getPaymentType();
        PaymentTO paymentTO = buildPaymentTO(paymentType, (SpiPaymentInfo) payment);

        return paymentService.initiatePaymentInternal(payment, initialAspspConsentData, PaymentTypeTO.valueOf(paymentType.toString()), paymentTO);
    }

    private PaymentTO buildPaymentTO(PaymentType paymentType, SpiPaymentInfo spiPaymentInfo) {
        if (standardPaymentProductsResolverConnector.isRawPaymentProduct(spiPaymentInfo.getPaymentProduct())) {
            return ledgersSpiPaymentToMapper.toCommonPaymentTO(spiPaymentInfo);
        } else {
            switch (paymentType) {
                case SINGLE: return ledgersSpiPaymentToMapper.toPaymentTO_Single(spiPaymentInfo);
                case BULK: return ledgersSpiPaymentToMapper.toPaymentTO_Bulk(spiPaymentInfo);
                case PERIODIC: return ledgersSpiPaymentToMapper.toPaymentTO_Periodic(spiPaymentInfo);
                default:
                    throw new IllegalArgumentException(String.format("Unknown payment type: %s", paymentType.getValue()));
            }
        }
    }

}
