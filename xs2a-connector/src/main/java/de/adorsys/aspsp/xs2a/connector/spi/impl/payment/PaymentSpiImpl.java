package de.adorsys.aspsp.xs2a.connector.spi.impl.payment;

import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.psd2.xs2a.core.profile.PaymentType;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiBulkPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiBulkPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPeriodicPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentSpiImpl implements PaymentSpi {

    protected final GeneralPaymentService paymentService;

    @Override
    public <P extends SpiPayment> SpiResponse<? extends SpiPaymentInitiationResponse> initiatePayment(@NotNull SpiContextData contextData,
                                                                                                      @NotNull P spiPayment,
                                                                                                      @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) throws NotSupportedPaymentTypeException {
        // Payment initiation can only be called if exemption.
        PaymentType paymentType = spiPayment.getPaymentType();

        Set<SpiAccountReference> spiAccountReferences;
        SpiPsuData spiPsuData = contextData.getPsuData();

        switch (paymentType) {
            case SINGLE:
                SpiSinglePayment singlePayment = (SpiSinglePayment) spiPayment;
                spiAccountReferences = new HashSet<>(Collections.singleton(singlePayment.getDebtorAccount()));

                return paymentService.firstCallInstantiatingPayment(PaymentTypeTO.SINGLE, singlePayment, aspspConsentDataProvider, new SpiSinglePaymentInitiationResponse(), spiPsuData, spiAccountReferences);

            case BULK:
                SpiBulkPayment bulkPayment = (SpiBulkPayment) spiPayment;
                spiAccountReferences = bulkPayment.getPayments().stream()
                                               .map(SpiSinglePayment::getDebtorAccount)
                                               .collect(Collectors.toSet());
                return paymentService.firstCallInstantiatingPayment(PaymentTypeTO.BULK, bulkPayment, aspspConsentDataProvider, new SpiBulkPaymentInitiationResponse(), spiPsuData, spiAccountReferences);

            case PERIODIC:
                SpiPeriodicPayment periodicPayment = (SpiPeriodicPayment) spiPayment;
                spiAccountReferences = new HashSet<>(Collections.singleton(periodicPayment.getDebtorAccount()));

                return paymentService.firstCallInstantiatingPayment(PaymentTypeTO.PERIODIC, periodicPayment, aspspConsentDataProvider, new SpiPeriodicPaymentInitiationResponse(), spiPsuData, spiAccountReferences);

            default:
                throw new NotSupportedPaymentTypeException(String.format("Unknown payment type: %s", paymentType.getValue()));
        }
    }
}
