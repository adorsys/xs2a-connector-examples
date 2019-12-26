package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.type;

import de.adorsys.aspsp.xs2a.connector.spi.converter.AddressMapperImpl;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ChallengeDataMapperImpl;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapperImpl;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionHandler;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.ledgers.middleware.api.domain.payment.BulkPaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.core.tpp.TppInfo;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiBulkPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiBulkPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiGetPaymentStatusResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {LedgersSpiPaymentMapperImpl.class, AddressMapperImpl.class, ChallengeDataMapperImpl.class})
public class BulkPaymentSpiImplTest {
    private final static String PAYMENT_PRODUCT = "sepa-credit-transfers";
    private static final SpiPsuData PSU_ID_DATA = new SpiPsuData("1", "2", "3", "4", "5");
    private static final SpiContextData SPI_CONTEXT_DATA = new SpiContextData(PSU_ID_DATA, new TppInfo(), UUID.randomUUID(), UUID.randomUUID());
    private static final String PAYMENT_ID = "c966f143-f6a2-41db-9036-8abaeeef3af7";
    private static final byte[] CONSENT_DATA_BYTES = "consent_data".getBytes();

    private BulkPaymentSpiImpl paymentSpi;

    private GeneralPaymentService paymentService;
    private AspspConsentDataService consentDataService;
    private FeignExceptionReader feignExceptionReader;

    @Autowired
    private LedgersSpiPaymentMapper spiPaymentMapper;
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;
    private SpiBulkPayment payment;

    @Before
    public void setUp() {
        payment = new SpiBulkPayment();
        payment.setPaymentId(PAYMENT_ID);
        payment.setPaymentProduct(PAYMENT_PRODUCT);
        payment.setPaymentStatus(TransactionStatus.RCVD);

        paymentService = mock(GeneralPaymentService.class);
        consentDataService = mock(AspspConsentDataService.class);
        feignExceptionReader = mock(FeignExceptionReader.class);
        spiAspspConsentDataProvider = mock(SpiAspspConsentDataProvider.class);

        paymentSpi = new BulkPaymentSpiImpl(paymentService, consentDataService, feignExceptionReader, spiPaymentMapper);
    }

    @Test
    public void getPaymentById() {
        when(paymentService.getPaymentById(eq(payment), eq(spiAspspConsentDataProvider), eq(BulkPaymentTO.class),
                                           any(), eq(PaymentTypeTO.BULK)))
                .thenReturn(SpiResponse.<SpiBulkPayment>builder()
                                    .payload(payment)
                                    .build());

        paymentSpi.getPaymentById(SPI_CONTEXT_DATA, payment, spiAspspConsentDataProvider);

        verify(paymentService, times(1)).getPaymentById(eq(payment),
                                                        eq(spiAspspConsentDataProvider), eq(BulkPaymentTO.class),
                                                        any(), eq(PaymentTypeTO.BULK));
    }

    @Test
    public void getPaymentStatusById() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(paymentService.getPaymentStatusById(PaymentTypeTO.BULK, PAYMENT_ID, TransactionStatus.RCVD, CONSENT_DATA_BYTES))
                .thenReturn(SpiResponse.<SpiGetPaymentStatusResponse>builder()
                                    .payload(new SpiGetPaymentStatusResponse(TransactionStatus.RCVD, false))
                                    .build());

        paymentSpi.getPaymentStatusById(SPI_CONTEXT_DATA, payment, spiAspspConsentDataProvider);

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(paymentService, times(1)).getPaymentStatusById(PaymentTypeTO.BULK, PAYMENT_ID, TransactionStatus.RCVD, CONSENT_DATA_BYTES);
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
        ArgumentCaptor<SpiBulkPaymentInitiationResponse> spiBulkPaymentInitiationResponseCaptor
                = ArgumentCaptor.forClass(SpiBulkPaymentInitiationResponse.class);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(new byte[]{});
        when(paymentService.firstCallInstantiatingPayment(eq(PaymentTypeTO.BULK), eq(payment),
                                                          eq(spiAspspConsentDataProvider), spiBulkPaymentInitiationResponseCaptor.capture()))
                .thenReturn(SpiResponse.<SpiBulkPaymentInitiationResponse>builder()
                                    .payload(new SpiBulkPaymentInitiationResponse())
                                    .build());

        paymentSpi.initiatePayment(SPI_CONTEXT_DATA, payment, spiAspspConsentDataProvider);

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(paymentService, times(1)).firstCallInstantiatingPayment(eq(PaymentTypeTO.BULK), eq(payment),
                                                                       eq(spiAspspConsentDataProvider), any(SpiBulkPaymentInitiationResponse.class));
        assertNull(spiBulkPaymentInitiationResponseCaptor.getValue().getPaymentId());
    }

    @Test
    public void initiatePayment_success() {
        ArgumentCaptor<BulkPaymentTO> bulkPaymentTOCaptor
                = ArgumentCaptor.forClass(BulkPaymentTO.class);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO response = new SCAPaymentResponseTO();
        response.setScaStatus(ScaStatusTO.PSUIDENTIFIED);
        when(paymentService.initiatePaymentInternal(eq(payment),
                                                    eq(CONSENT_DATA_BYTES), eq(PaymentTypeTO.BULK), bulkPaymentTOCaptor.capture()))
                .thenReturn(response);
        byte[] responseBytes = "response_byte".getBytes();
        when(consentDataService.store(response)).thenReturn(responseBytes);
        doNothing().when(spiAspspConsentDataProvider).updateAspspConsentData(responseBytes);

        SpiResponse<SpiBulkPaymentInitiationResponse> actual = paymentSpi.initiatePayment(SPI_CONTEXT_DATA, payment, spiAspspConsentDataProvider);

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(paymentService, times(1)).initiatePaymentInternal(eq(payment), eq(CONSENT_DATA_BYTES),
                                                                 eq(PaymentTypeTO.BULK), any(BulkPaymentTO.class));
        verify(paymentService, never()).getSCAPaymentResponseTO(any());
        verify(consentDataService, times(1)).store(response);
        verify(spiAspspConsentDataProvider, times(1)).updateAspspConsentData(responseBytes);

        assertFalse(actual.hasError());
        assertEquals(PaymentProductTO.SEPA, bulkPaymentTOCaptor.getValue().getPaymentProduct());
    }

    @Test
    public void initiatePayment_success_paymentProductIsNull() {
        payment.setPaymentProduct(null);
        ArgumentCaptor<BulkPaymentTO> bulkPaymentTOCaptor
                = ArgumentCaptor.forClass(BulkPaymentTO.class);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = new SCAPaymentResponseTO();
        scaPaymentResponseTO.setPaymentProduct(PaymentProductTO.SEPA.getValue());
        when(paymentService.getSCAPaymentResponseTO(CONSENT_DATA_BYTES)).thenReturn(scaPaymentResponseTO);
        SCAPaymentResponseTO response = new SCAPaymentResponseTO();
        response.setScaStatus(ScaStatusTO.PSUIDENTIFIED);
        when(paymentService.initiatePaymentInternal(eq(payment),
                                                    eq(CONSENT_DATA_BYTES), eq(PaymentTypeTO.BULK), bulkPaymentTOCaptor.capture()))
                .thenReturn(response);
        byte[] responseBytes = "response_byte".getBytes();
        when(consentDataService.store(response)).thenReturn(responseBytes);
        doNothing().when(spiAspspConsentDataProvider).updateAspspConsentData(responseBytes);

        SpiResponse<SpiBulkPaymentInitiationResponse> actual = paymentSpi.initiatePayment(SPI_CONTEXT_DATA, payment, spiAspspConsentDataProvider);

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(paymentService, times(1)).initiatePaymentInternal(eq(payment), eq(CONSENT_DATA_BYTES),
                                                                 eq(PaymentTypeTO.BULK), any(BulkPaymentTO.class));
        verify(paymentService, times(1)).getSCAPaymentResponseTO(any());
        verify(consentDataService, times(1)).store(response);
        verify(spiAspspConsentDataProvider, times(1)).updateAspspConsentData(responseBytes);

        assertFalse(actual.hasError());
        assertEquals(PaymentProductTO.SEPA, bulkPaymentTOCaptor.getValue().getPaymentProduct());
    }

    @Test
    public void initiatePayment_error() {
        payment.setPaymentProduct(null);
        ArgumentCaptor<BulkPaymentTO> bulkPaymentTOCaptor
                = ArgumentCaptor.forClass(BulkPaymentTO.class);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = new SCAPaymentResponseTO();
        scaPaymentResponseTO.setPaymentProduct(PaymentProductTO.SEPA.getValue());
        when(paymentService.getSCAPaymentResponseTO(CONSENT_DATA_BYTES)).thenReturn(scaPaymentResponseTO);
        SCAPaymentResponseTO response = new SCAPaymentResponseTO();
        response.setScaStatus(ScaStatusTO.PSUIDENTIFIED);
        when(paymentService.initiatePaymentInternal(eq(payment),
                                                    eq(CONSENT_DATA_BYTES), eq(PaymentTypeTO.BULK), bulkPaymentTOCaptor.capture()))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message1"));

        SpiResponse<SpiBulkPaymentInitiationResponse> actual = paymentSpi.initiatePayment(SPI_CONTEXT_DATA, payment, spiAspspConsentDataProvider);

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(paymentService, times(1)).initiatePaymentInternal(eq(payment), eq(CONSENT_DATA_BYTES),
                                                                 eq(PaymentTypeTO.BULK), any(BulkPaymentTO.class));
        verify(paymentService, times(1)).getSCAPaymentResponseTO(any());
        verify(consentDataService, never()).store(any());
        verify(spiAspspConsentDataProvider, never()).updateAspspConsentData(any());

        assertEquals(PaymentProductTO.SEPA, bulkPaymentTOCaptor.getValue().getPaymentProduct());

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.PAYMENT_FAILED, actual.getErrors().get(0).getErrorCode());
    }
}