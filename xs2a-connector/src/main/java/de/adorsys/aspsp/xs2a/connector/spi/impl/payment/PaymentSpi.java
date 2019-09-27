package de.adorsys.aspsp.xs2a.connector.spi.impl.payment;

import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import org.jetbrains.annotations.NotNull;

public interface PaymentSpi {

    <P extends SpiPayment> SpiResponse<? extends SpiPaymentInitiationResponse> initiatePayment(@NotNull SpiContextData contextData,
                                                                                               @NotNull P payment,
                                                                                               @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) throws NotSupportedPaymentTypeException;
}
