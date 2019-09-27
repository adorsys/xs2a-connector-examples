package de.adorsys.aspsp.xs2a.connector.spi.impl.payment;

import de.adorsys.psd2.xs2a.core.profile.PaymentType;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiBulkPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.BulkPaymentSpi;
import de.adorsys.psd2.xs2a.spi.service.PeriodicPaymentSpi;
import de.adorsys.psd2.xs2a.spi.service.SinglePaymentSpi;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentSpiImpl implements PaymentSpi {

    private final SinglePaymentSpi singlePaymentSpi;
    private final PeriodicPaymentSpi periodicPaymentSpi;
    private final BulkPaymentSpi bulkPaymentSpi;

    @Override
    public <P extends SpiPayment> SpiResponse<? extends SpiPaymentInitiationResponse> initiatePayment(@NotNull SpiContextData contextData,
                                                                                                      @NotNull P spiPayment,
                                                                                                      @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) throws NotSupportedPaymentTypeException {
        // Payment initiation can only be called if exemption.
        PaymentType paymentType = spiPayment.getPaymentType();

        // Don't know who came to idea to call external API internally, but it causes now to bring this tricky hack in play
        switch (paymentType) {
            case SINGLE:
                return singlePaymentSpi.initiatePayment(contextData, (@NotNull SpiSinglePayment) spiPayment, aspspConsentDataProvider);
            case BULK:
                return bulkPaymentSpi.initiatePayment(contextData, (@NotNull SpiBulkPayment) spiPayment, aspspConsentDataProvider);
            case PERIODIC:
                return periodicPaymentSpi.initiatePayment(contextData, (@NotNull SpiPeriodicPayment) spiPayment, aspspConsentDataProvider);
            default:
                throw new NotSupportedPaymentTypeException(String.format("Unknown payment type: %s", paymentType.getValue()));
        }
    }
}
