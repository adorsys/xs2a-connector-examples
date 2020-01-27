package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.type;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.*;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.aspsp.xs2a.util.TestSpiDataProvider;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PeriodicPaymentTO;
import de.adorsys.psd2.xs2a.core.pis.FrequencyCode;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiGetPaymentStatusResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPeriodicPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {LedgersSpiPaymentMapperImpl.class, AddressMapperImpl.class, ChallengeDataMapperImpl.class, LedgersSpiAccountMapperImpl.class, ObjectMapper.class})
class PeriodicPaymentSpiImplTest {
    private final static String PAYMENT_PRODUCT = "sepa-credit-transfers";
    private static final SpiContextData SPI_CONTEXT_DATA = TestSpiDataProvider.getSpiContextData();
    private static final String PAYMENT_ID = "c966f143-f6a2-41db-9036-8abaeeef3af7";
    private static final byte[] CONSENT_DATA_BYTES = "consent_data".getBytes();
    private static final String JSON_ACCEPT_MEDIA_TYPE = "application/json";

    private PeriodicPaymentSpiImpl paymentSpi;
    private GeneralPaymentService paymentService;

    @Autowired
    private LedgersSpiPaymentMapper spiPaymentMapper;
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;
    private SpiPeriodicPayment payment;

    @BeforeEach
    void setUp() {
        payment = new SpiPeriodicPayment(PAYMENT_PRODUCT);
        payment.setPaymentId(PAYMENT_ID);
        payment.setPaymentStatus(TransactionStatus.RCVD);
        payment.setFrequency(FrequencyCode.MONTHLY);

        paymentService = mock(GeneralPaymentService.class);
        spiAspspConsentDataProvider = mock(SpiAspspConsentDataProvider.class);
        paymentSpi = new PeriodicPaymentSpiImpl(paymentService, spiPaymentMapper);
    }

    @Test
    void getPaymentById() {
        when(paymentService.getPaymentById(eq(payment), eq(spiAspspConsentDataProvider), eq(PeriodicPaymentTO.class),
                                           any(), eq(PaymentTypeTO.PERIODIC)))
                .thenReturn(SpiResponse.<SpiPeriodicPayment>builder()
                                    .payload(new SpiPeriodicPayment(PAYMENT_PRODUCT))
                                    .build());

        paymentSpi.getPaymentById(SPI_CONTEXT_DATA, JSON_ACCEPT_MEDIA_TYPE, payment, spiAspspConsentDataProvider);

        verify(paymentService, times(1)).getPaymentById(eq(payment),
                                                        eq(spiAspspConsentDataProvider), eq(PeriodicPaymentTO.class),
                                                        any(), eq(PaymentTypeTO.PERIODIC));
    }

    @Test
    void getPaymentStatusById() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(paymentService.getPaymentStatusById(PaymentTypeTO.PERIODIC, JSON_ACCEPT_MEDIA_TYPE, PAYMENT_ID, TransactionStatus.RCVD, CONSENT_DATA_BYTES))
                .thenReturn(SpiResponse.<SpiGetPaymentStatusResponse>builder()
                                    .payload(new SpiGetPaymentStatusResponse(TransactionStatus.RCVD, false, SpiGetPaymentStatusResponse.RESPONSE_TYPE_JSON, null))
                                    .build());

        paymentSpi.getPaymentStatusById(SPI_CONTEXT_DATA, JSON_ACCEPT_MEDIA_TYPE, payment, spiAspspConsentDataProvider);

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(paymentService, times(1)).getPaymentStatusById(PaymentTypeTO.PERIODIC, JSON_ACCEPT_MEDIA_TYPE, PAYMENT_ID, TransactionStatus.RCVD, CONSENT_DATA_BYTES);
    }

    @Test
    void executePaymentWithoutSca() {
        when(paymentService.executePaymentWithoutSca(spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPaymentExecutionResponse>builder()
                                    .payload(new SpiPaymentExecutionResponse(TransactionStatus.RCVD))
                                    .build());

        paymentSpi.executePaymentWithoutSca(SPI_CONTEXT_DATA, payment, spiAspspConsentDataProvider);

        verify(paymentService, times(1)).executePaymentWithoutSca(spiAspspConsentDataProvider);
    }

    @Test
    void verifyScaAuthorisationAndExecutePayment() {
        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        when(paymentService.verifyScaAuthorisationAndExecutePayment(spiScaConfirmation, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPaymentExecutionResponse>builder()
                                    .payload(new SpiPaymentExecutionResponse(TransactionStatus.RCVD))
                                    .build());

        paymentSpi.verifyScaAuthorisationAndExecutePayment(SPI_CONTEXT_DATA, spiScaConfirmation, payment, spiAspspConsentDataProvider);

        verify(paymentService).verifyScaAuthorisationAndExecutePayment(spiScaConfirmation, spiAspspConsentDataProvider);
    }

    @Test
    void initiatePayment_emptyConsentData() {
        ArgumentCaptor<SpiPeriodicPaymentInitiationResponse> spiPeriodicPaymentInitiationResponseCaptor
                = ArgumentCaptor.forClass(SpiPeriodicPaymentInitiationResponse.class);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(new byte[]{});
        Set<SpiAccountReference> spiAccountReferences = new HashSet<>(Collections.singleton(payment.getDebtorAccount()));
        when(paymentService.firstCallInstantiatingPayment(eq(PaymentTypeTO.PERIODIC), eq(payment),
                                                          eq(spiAspspConsentDataProvider), spiPeriodicPaymentInitiationResponseCaptor.capture(), eq(SPI_CONTEXT_DATA.getPsuData()), eq(spiAccountReferences)))
                .thenReturn(SpiResponse.<SpiPeriodicPaymentInitiationResponse>builder()
                                    .payload(new SpiPeriodicPaymentInitiationResponse())
                                    .build());

        paymentSpi.initiatePayment(SPI_CONTEXT_DATA, payment, spiAspspConsentDataProvider);

        verify(paymentService, times(1)).firstCallInstantiatingPayment(eq(PaymentTypeTO.PERIODIC), eq(payment),
                                                                       eq(spiAspspConsentDataProvider), any(SpiPeriodicPaymentInitiationResponse.class), eq(SPI_CONTEXT_DATA.getPsuData()), eq(spiAccountReferences));
        assertNull(spiPeriodicPaymentInitiationResponseCaptor.getValue().getPaymentId());
    }
}