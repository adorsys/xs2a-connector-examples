package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.type;

import de.adorsys.aspsp.xs2a.connector.spi.converter.AddressMapperImpl;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ChallengeDataMapperImpl;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapperImpl;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionHandler;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.aspsp.xs2a.util.TestSpiDataProvider;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PeriodicPaymentTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiGetPaymentStatusResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPeriodicPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {LedgersSpiPaymentMapperImpl.class, AddressMapperImpl.class, ChallengeDataMapperImpl.class})
public class PeriodicPaymentSpiImplTest {
    private final static String PAYMENT_PRODUCT = "sepa-credit-transfers";
    private static final SpiContextData SPI_CONTEXT_DATA = TestSpiDataProvider.getSpiContextData();
    private static final String PAYMENT_ID = "c966f143-f6a2-41db-9036-8abaeeef3af7";
    private static final byte[] CONSENT_DATA_BYTES = "consent_data".getBytes();

    private PeriodicPaymentSpiImpl paymentSpi;

    private GeneralPaymentService paymentService;
    private AspspConsentDataService consentDataService;
    private FeignExceptionReader feignExceptionReader;

    @Autowired
    private LedgersSpiPaymentMapper spiPaymentMapper;
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;
    private SpiPeriodicPayment payment;

    @Before
    public void setUp() {
        payment = new SpiPeriodicPayment(PAYMENT_PRODUCT);
        payment.setPaymentId(PAYMENT_ID);
        payment.setPaymentStatus(TransactionStatus.RCVD);

        paymentService = mock(GeneralPaymentService.class);
        consentDataService = mock(AspspConsentDataService.class);
        feignExceptionReader = mock(FeignExceptionReader.class);
        spiAspspConsentDataProvider = mock(SpiAspspConsentDataProvider.class);

        paymentSpi = new PeriodicPaymentSpiImpl(paymentService, consentDataService, feignExceptionReader, spiPaymentMapper);
    }

    @Test
    public void getPaymentById() {
        when(paymentService.getPaymentById(eq(payment), eq(spiAspspConsentDataProvider), eq(PeriodicPaymentTO.class),
                                           any(), eq(PaymentTypeTO.PERIODIC)))
                .thenReturn(SpiResponse.<SpiPeriodicPayment>builder()
                                    .payload(new SpiPeriodicPayment(PAYMENT_PRODUCT))
                                    .build());

        paymentSpi.getPaymentById(SPI_CONTEXT_DATA, payment, spiAspspConsentDataProvider);

        verify(paymentService, times(1)).getPaymentById(eq(payment),
                                                        eq(spiAspspConsentDataProvider), eq(PeriodicPaymentTO.class),
                                                        any(), eq(PaymentTypeTO.PERIODIC));
    }

    @Test
    public void getPaymentStatusById() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(paymentService.getPaymentStatusById(PaymentTypeTO.PERIODIC, PAYMENT_ID, TransactionStatus.RCVD, CONSENT_DATA_BYTES))
                .thenReturn(SpiResponse.<SpiGetPaymentStatusResponse>builder()
                                    .payload(new SpiGetPaymentStatusResponse(TransactionStatus.RCVD, false))
                                    .build());

        paymentSpi.getPaymentStatusById(SPI_CONTEXT_DATA, payment, spiAspspConsentDataProvider);

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(paymentService, times(1)).getPaymentStatusById(PaymentTypeTO.PERIODIC, PAYMENT_ID, TransactionStatus.RCVD, CONSENT_DATA_BYTES);
    }

    @Test
    public void executePaymentWithoutSca() {
        when(paymentService.executePaymentWithoutSca(spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPaymentExecutionResponse>builder()
                                    .payload(new SpiPaymentExecutionResponse(TransactionStatus.RCVD))
                                    .build());

        paymentSpi.executePaymentWithoutSca(SPI_CONTEXT_DATA, payment, spiAspspConsentDataProvider);

        verify(paymentService, times(1)).executePaymentWithoutSca(spiAspspConsentDataProvider);
    }

    @Test
    public void verifyScaAuthorisationAndExecutePayment() {
        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        when(paymentService.verifyScaAuthorisationAndExecutePayment(spiScaConfirmation, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPaymentExecutionResponse>builder()
                                    .payload(new SpiPaymentExecutionResponse(TransactionStatus.RCVD))
                                    .build());

        paymentSpi.verifyScaAuthorisationAndExecutePayment(SPI_CONTEXT_DATA, spiScaConfirmation, payment, spiAspspConsentDataProvider);

        verify(paymentService).verifyScaAuthorisationAndExecutePayment(spiScaConfirmation, spiAspspConsentDataProvider);
    }

    @Test
    public void initiatePayment_emptyConsentData() {
        ArgumentCaptor<SpiPeriodicPaymentInitiationResponse> spiPeriodicPaymentInitiationResponseCaptor
                = ArgumentCaptor.forClass(SpiPeriodicPaymentInitiationResponse.class);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(new byte[]{});
        when(paymentService.firstCallInstantiatingPayment(eq(PaymentTypeTO.PERIODIC), eq(payment),
                                                          eq(spiAspspConsentDataProvider), spiPeriodicPaymentInitiationResponseCaptor.capture()))
                .thenReturn(SpiResponse.<SpiPeriodicPaymentInitiationResponse>builder()
                                    .payload(new SpiPeriodicPaymentInitiationResponse())
                                    .build());

        paymentSpi.initiatePayment(SPI_CONTEXT_DATA, payment, spiAspspConsentDataProvider);

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(paymentService, times(1)).firstCallInstantiatingPayment(eq(PaymentTypeTO.PERIODIC), eq(payment),
                                                                       eq(spiAspspConsentDataProvider), any(SpiPeriodicPaymentInitiationResponse.class));
        assertNull(spiPeriodicPaymentInitiationResponseCaptor.getValue().getPaymentId());
    }

    @Test
    public void initiatePayment_success() {
        ArgumentCaptor<PeriodicPaymentTO> periodicPaymentTOCaptor
                = ArgumentCaptor.forClass(PeriodicPaymentTO.class);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO response = new SCAPaymentResponseTO();
        response.setScaStatus(ScaStatusTO.PSUIDENTIFIED);
        when(paymentService.initiatePaymentInternal(eq(payment),
                                                    eq(CONSENT_DATA_BYTES), eq(PaymentTypeTO.PERIODIC), periodicPaymentTOCaptor.capture()))
                .thenReturn(response);
        byte[] responseBytes = "response_byte".getBytes();
        when(consentDataService.store(response)).thenReturn(responseBytes);
        doNothing().when(spiAspspConsentDataProvider).updateAspspConsentData(responseBytes);

        SpiResponse<SpiPeriodicPaymentInitiationResponse> actual = paymentSpi.initiatePayment(SPI_CONTEXT_DATA, payment, spiAspspConsentDataProvider);

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(paymentService, times(1)).initiatePaymentInternal(eq(payment), eq(CONSENT_DATA_BYTES),
                                                                 eq(PaymentTypeTO.PERIODIC), any(PeriodicPaymentTO.class));
        verify(paymentService, never()).getSCAPaymentResponseTO(any());
        verify(consentDataService, times(1)).store(response);
        verify(spiAspspConsentDataProvider, times(1)).updateAspspConsentData(responseBytes);

        assertFalse(actual.hasError());
        assertEquals(PaymentProductTO.SEPA, periodicPaymentTOCaptor.getValue().getPaymentProduct());
    }

    @Test
    public void initiatePayment_success_paymentProductIsNull() {
        payment.setPaymentProduct(null);
        ArgumentCaptor<PeriodicPaymentTO> periodicPaymentTOCaptor
                = ArgumentCaptor.forClass(PeriodicPaymentTO.class);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = new SCAPaymentResponseTO();
        scaPaymentResponseTO.setPaymentProduct(PaymentProductTO.SEPA);
        when(paymentService.getSCAPaymentResponseTO(CONSENT_DATA_BYTES)).thenReturn(scaPaymentResponseTO);
        SCAPaymentResponseTO response = new SCAPaymentResponseTO();
        response.setScaStatus(ScaStatusTO.PSUIDENTIFIED);
        when(paymentService.initiatePaymentInternal(eq(payment),
                                                    eq(CONSENT_DATA_BYTES), eq(PaymentTypeTO.PERIODIC), periodicPaymentTOCaptor.capture()))
                .thenReturn(response);
        byte[] responseBytes = "response_byte".getBytes();
        when(consentDataService.store(response)).thenReturn(responseBytes);
        doNothing().when(spiAspspConsentDataProvider).updateAspspConsentData(responseBytes);

        SpiResponse<SpiPeriodicPaymentInitiationResponse> actual = paymentSpi.initiatePayment(SPI_CONTEXT_DATA, payment, spiAspspConsentDataProvider);

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(paymentService, times(1)).initiatePaymentInternal(eq(payment), eq(CONSENT_DATA_BYTES),
                                                                 eq(PaymentTypeTO.PERIODIC), any(PeriodicPaymentTO.class));
        verify(paymentService, times(1)).getSCAPaymentResponseTO(any());
        verify(consentDataService, times(1)).store(response);
        verify(spiAspspConsentDataProvider, times(1)).updateAspspConsentData(responseBytes);

        assertFalse(actual.hasError());
        assertEquals(PaymentProductTO.SEPA, periodicPaymentTOCaptor.getValue().getPaymentProduct());
    }

    @Test
    public void initiatePayment_error() {
        payment.setPaymentProduct(null);
        ArgumentCaptor<PeriodicPaymentTO> periodicPaymentTOCaptor
                = ArgumentCaptor.forClass(PeriodicPaymentTO.class);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = new SCAPaymentResponseTO();
        scaPaymentResponseTO.setPaymentProduct(PaymentProductTO.SEPA);
        when(paymentService.getSCAPaymentResponseTO(CONSENT_DATA_BYTES)).thenReturn(scaPaymentResponseTO);
        SCAPaymentResponseTO response = new SCAPaymentResponseTO();
        response.setScaStatus(ScaStatusTO.PSUIDENTIFIED);
        when(paymentService.initiatePaymentInternal(eq(payment),
                                                    eq(CONSENT_DATA_BYTES), eq(PaymentTypeTO.PERIODIC), periodicPaymentTOCaptor.capture()))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message1"));

        SpiResponse<SpiPeriodicPaymentInitiationResponse> actual = paymentSpi.initiatePayment(SPI_CONTEXT_DATA, payment, spiAspspConsentDataProvider);

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(paymentService, times(1)).initiatePaymentInternal(eq(payment), eq(CONSENT_DATA_BYTES),
                                                                 eq(PaymentTypeTO.PERIODIC), any(PeriodicPaymentTO.class));
        verify(paymentService, times(1)).getSCAPaymentResponseTO(any());
        verify(consentDataService, never()).store(any());
        verify(spiAspspConsentDataProvider, never()).updateAspspConsentData(any());

        assertEquals(PaymentProductTO.SEPA, periodicPaymentTOCaptor.getValue().getPaymentProduct());

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.PAYMENT_FAILED, actual.getErrors().get(0).getErrorCode());
    }
}