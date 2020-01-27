package de.adorsys.aspsp.xs2a.connector.spi.impl.payment;

import de.adorsys.aspsp.xs2a.util.TestSpiDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiBulkPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiBulkPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPeriodicPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.BulkPaymentSpi;
import de.adorsys.psd2.xs2a.spi.service.PeriodicPaymentSpi;
import de.adorsys.psd2.xs2a.spi.service.SinglePaymentSpi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentSpiImplTest {

    private final static String PAYMENT_PRODUCT = "sepa-credit-transfers";
    private static final SpiContextData SPI_CONTEXT_DATA = TestSpiDataProvider.getSpiContextData();

    @InjectMocks
    private PaymentSpiImpl paymentSpi;

    @Mock
    private SinglePaymentSpi singlePaymentSpi;
    @Mock
    private PeriodicPaymentSpi periodicPaymentSpi;
    @Mock
    private BulkPaymentSpi bulkPaymentSpi;

    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;

    @Test
    void initiatePayment_singlePayment() throws NotSupportedPaymentTypeException {
        SpiSinglePayment spiPayment = new SpiSinglePayment(PAYMENT_PRODUCT);
        when(singlePaymentSpi.initiatePayment(SPI_CONTEXT_DATA, spiPayment, spiAspspConsentDataProvider)).thenReturn(SpiResponse.<SpiSinglePaymentInitiationResponse>builder()
                                                                                                                             .build());
        paymentSpi.initiatePayment(SPI_CONTEXT_DATA, spiPayment, spiAspspConsentDataProvider);

        verify(singlePaymentSpi, times(1)).initiatePayment(SPI_CONTEXT_DATA, spiPayment, spiAspspConsentDataProvider);
    }

    @Test
    void initiatePayment_periodicPayment() throws NotSupportedPaymentTypeException {
        SpiPeriodicPayment spiPayment = new SpiPeriodicPayment(PAYMENT_PRODUCT);
        when(periodicPaymentSpi.initiatePayment(SPI_CONTEXT_DATA, spiPayment, spiAspspConsentDataProvider)).thenReturn(SpiResponse.<SpiPeriodicPaymentInitiationResponse>builder()
                                                                                                                               .build());
        paymentSpi.initiatePayment(SPI_CONTEXT_DATA, spiPayment, spiAspspConsentDataProvider);

        verify(periodicPaymentSpi, times(1)).initiatePayment(SPI_CONTEXT_DATA, spiPayment, spiAspspConsentDataProvider);
    }

    @Test
    void initiatePayment_bulkPayment() throws NotSupportedPaymentTypeException {
        SpiBulkPayment spiPayment = new SpiBulkPayment();
        when(bulkPaymentSpi.initiatePayment(SPI_CONTEXT_DATA, spiPayment, spiAspspConsentDataProvider)).thenReturn(SpiResponse.<SpiBulkPaymentInitiationResponse>builder()
                                                                                                                           .build());
        paymentSpi.initiatePayment(SPI_CONTEXT_DATA, spiPayment, spiAspspConsentDataProvider);

        verify(bulkPaymentSpi, times(1)).initiatePayment(SPI_CONTEXT_DATA, spiPayment, spiAspspConsentDataProvider);
    }
}