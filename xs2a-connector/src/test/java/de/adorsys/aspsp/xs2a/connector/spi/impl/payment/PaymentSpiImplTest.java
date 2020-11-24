package de.adorsys.aspsp.xs2a.connector.spi.impl.payment;

import de.adorsys.aspsp.xs2a.util.TestSpiDataProvider;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiBulkPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiBulkPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPeriodicPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentSpiImplTest {

    private final static String PAYMENT_PRODUCT = "sepa-credit-transfers";
    private static final SpiContextData SPI_CONTEXT_DATA = TestSpiDataProvider.getSpiContextData();
    private static final SpiAccountReference DEBTOR_ACCOUNT = new SpiAccountReference(null, "mocked debtor iban", null, null, null, null, null);

    @InjectMocks
    private PaymentSpiImpl paymentSpi;

    @Mock
    private GeneralPaymentService paymentService;

    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;

    @Test
    void initiatePayment_singlePayment() throws NotSupportedPaymentTypeException {
        SpiSinglePayment spiPayment = new SpiSinglePayment(PAYMENT_PRODUCT);
        spiPayment.setDebtorAccount(DEBTOR_ACCOUNT);

        paymentSpi.initiatePayment(SPI_CONTEXT_DATA, spiPayment, spiAspspConsentDataProvider);

        verify(paymentService, times(1)).firstCallInstantiatingPayment(eq(PaymentTypeTO.SINGLE), eq(spiPayment), eq(spiAspspConsentDataProvider),
                                                                       any(SpiSinglePaymentInitiationResponse.class), eq(SPI_CONTEXT_DATA.getPsuData()),
                                                                       eq(Collections.singleton(DEBTOR_ACCOUNT)));
    }

    @Test
    void initiatePayment_periodicPayment() throws NotSupportedPaymentTypeException {
        SpiPeriodicPayment spiPayment = new SpiPeriodicPayment(PAYMENT_PRODUCT);
        spiPayment.setDebtorAccount(DEBTOR_ACCOUNT);

        paymentSpi.initiatePayment(SPI_CONTEXT_DATA, spiPayment, spiAspspConsentDataProvider);

        verify(paymentService, times(1)).firstCallInstantiatingPayment(eq(PaymentTypeTO.PERIODIC), eq(spiPayment), eq(spiAspspConsentDataProvider),
                                                                       any(SpiPeriodicPaymentInitiationResponse.class), eq(SPI_CONTEXT_DATA.getPsuData()),
                                                                       eq(Collections.singleton(DEBTOR_ACCOUNT)));
    }

    @Test
    void initiatePayment_bulkPayment() throws NotSupportedPaymentTypeException {
        SpiBulkPayment spiPayment = new SpiBulkPayment();
        SpiSinglePayment singlePayment = new SpiSinglePayment(PAYMENT_PRODUCT);
        SpiPeriodicPayment periodicPayment = new SpiPeriodicPayment(PAYMENT_PRODUCT);
        periodicPayment.setDebtorAccount(DEBTOR_ACCOUNT);
        singlePayment.setDebtorAccount(DEBTOR_ACCOUNT);
        spiPayment.setPayments(Arrays.asList(singlePayment, periodicPayment));

        paymentSpi.initiatePayment(SPI_CONTEXT_DATA, spiPayment, spiAspspConsentDataProvider);

        verify(paymentService, times(1)).firstCallInstantiatingPayment(eq(PaymentTypeTO.BULK), eq(spiPayment), eq(spiAspspConsentDataProvider),
                                                                       any(SpiBulkPaymentInitiationResponse.class), eq(SPI_CONTEXT_DATA.getPsuData()),
                                                                       eq(Collections.singleton(DEBTOR_ACCOUNT)));
    }
}