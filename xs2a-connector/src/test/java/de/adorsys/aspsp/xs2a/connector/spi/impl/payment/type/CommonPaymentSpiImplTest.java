package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.type;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.aspsp.xs2a.util.TestSpiDataProvider;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.core.profile.PaymentType;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPaymentInfo;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiGetPaymentStatusResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommonPaymentSpiImplTest {
    private final static String PAYMENT_PRODUCT = "sepa-credit-transfers";
    private static final String PAYMENT_ID = "c966f143-f6a2-41db-9036-8abaeeef3af7";
    private static final SpiContextData SPI_CONTEXT_DATA = TestSpiDataProvider.getSpiContextData();

    @InjectMocks
    private CommonPaymentSpiImpl commonPaymentSpi;
    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;
    @Mock
    private GeneralPaymentService generalPaymentService;
    @Mock
    private LedgersSpiPaymentMapper ledgersSpiPaymentMapper;

    @Test
    void executePaymentWithoutSca() {
        //When
        SpiResponse<SpiPaymentExecutionResponse> response = commonPaymentSpi.executePaymentWithoutSca(SPI_CONTEXT_DATA, new SpiPaymentInfo(PAYMENT_PRODUCT), spiAspspConsentDataProvider);

        //Then
        assertTrue(response.hasError());
        assertEquals(MessageErrorCode.SERVICE_NOT_SUPPORTED, response.getErrors().get(0).getErrorCode());
    }

    @Test
    void verifyScaAuthorisationAndExecutePayment() {
        //When
        SpiResponse<SpiPaymentExecutionResponse> response = commonPaymentSpi.verifyScaAuthorisationAndExecutePayment(SPI_CONTEXT_DATA, new SpiScaConfirmation(), new SpiPaymentInfo(PAYMENT_PRODUCT), spiAspspConsentDataProvider);

        //Then
        assertTrue(response.hasError());
        assertEquals(MessageErrorCode.SERVICE_NOT_SUPPORTED, response.getErrors().get(0).getErrorCode());
    }

    @Test
    void initiatePayment() {
        //Given
        SpiPaymentInfo spiPaymentInfo = new SpiPaymentInfo(PAYMENT_PRODUCT);
        SpiSinglePaymentInitiationResponse spiSinglePaymentInitiationResponse = new SpiSinglePaymentInitiationResponse();
        spiSinglePaymentInitiationResponse.setPaymentId(PAYMENT_ID);

        when(generalPaymentService.firstCallInstantiatingPayment(PaymentTypeTO.SINGLE, spiPaymentInfo, spiAspspConsentDataProvider, new SpiSinglePaymentInitiationResponse(), SPI_CONTEXT_DATA.getPsuData(), new HashSet<>()))
                .thenReturn(buildSpiResponse(spiSinglePaymentInitiationResponse));

        //When
        SpiResponse<SpiPaymentInitiationResponse> response = commonPaymentSpi.initiatePayment(SPI_CONTEXT_DATA, spiPaymentInfo, spiAspspConsentDataProvider);

        //Then
        assertTrue(response.isSuccessful());
        assertEquals(response.getPayload(), spiSinglePaymentInitiationResponse);
    }

    @Test
    void processEmptyAspspConsentData() {
        //Given
        SpiPaymentInfo spiPaymentInfo = new SpiPaymentInfo(PAYMENT_PRODUCT);
        SpiSinglePaymentInitiationResponse spiSinglePaymentInitiationResponse = new SpiSinglePaymentInitiationResponse();
        spiSinglePaymentInitiationResponse.setPaymentId(PAYMENT_ID);

        when(generalPaymentService.firstCallInstantiatingPayment(PaymentTypeTO.SINGLE, spiPaymentInfo, spiAspspConsentDataProvider, new SpiSinglePaymentInitiationResponse(), SPI_CONTEXT_DATA.getPsuData(), new HashSet<>()))
                .thenReturn(buildSpiResponse(spiSinglePaymentInitiationResponse));

        //When
        SpiResponse<SpiPaymentInitiationResponse> response = commonPaymentSpi.processEmptyAspspConsentData(spiPaymentInfo, spiAspspConsentDataProvider, SPI_CONTEXT_DATA.getPsuData());

        //Then
        assertTrue(response.isSuccessful());
        assertEquals(response.getPayload(), spiSinglePaymentInitiationResponse);
    }

    @Test
    void getToSpiPaymentResponse() {
        //Given
        SpiSinglePaymentInitiationResponse spiSinglePaymentInitiationResponse = new SpiSinglePaymentInitiationResponse();
        spiSinglePaymentInitiationResponse.setPaymentId(PAYMENT_ID);
        SCAPaymentResponseTO scaPaymentResponseTO = new SCAPaymentResponseTO();

        when(ledgersSpiPaymentMapper.toSpiSingleResponse(scaPaymentResponseTO))
                .thenReturn(spiSinglePaymentInitiationResponse);

        //When
        SpiPaymentInitiationResponse spiPaymentInitiationResponse = commonPaymentSpi.getToSpiPaymentResponse(scaPaymentResponseTO);

        //Then
        assertEquals(spiSinglePaymentInitiationResponse, spiPaymentInitiationResponse);
    }

    @Test
    void getPaymentById() {
        //Given
        SpiPaymentInfo spiPaymentInfo = new SpiPaymentInfo(PAYMENT_PRODUCT);

        //When
        SpiResponse<SpiPaymentInfo> response = commonPaymentSpi.getPaymentById(SPI_CONTEXT_DATA, MediaType.APPLICATION_JSON_VALUE, spiPaymentInfo, spiAspspConsentDataProvider);

        //Then
        assertEquals(spiPaymentInfo, response.getPayload());
    }

    @Test
    void getPaymentStatusById_JSON() {
        //Given
        String mediaType = MediaType.APPLICATION_JSON_VALUE;

        SpiPaymentInfo spiPaymentInfo = new SpiPaymentInfo(PAYMENT_PRODUCT);
        spiPaymentInfo.setPaymentType(PaymentType.SINGLE);
        spiPaymentInfo.setPaymentId(PAYMENT_ID);
        spiPaymentInfo.setPaymentStatus(TransactionStatus.ACSP);

        SpiGetPaymentStatusResponse spiGetPaymentStatusResponse = new SpiGetPaymentStatusResponse(spiPaymentInfo.getPaymentStatus(), null, SpiGetPaymentStatusResponse.RESPONSE_TYPE_JSON, null);

        //When
        SpiResponse<SpiGetPaymentStatusResponse> paymentStatusById = commonPaymentSpi.getPaymentStatusById(SPI_CONTEXT_DATA, mediaType, spiPaymentInfo, spiAspspConsentDataProvider);

        //Then
        assertEquals(spiGetPaymentStatusResponse, paymentStatusById.getPayload());
    }

    @Test
    void getPaymentStatusById_XML() {
        //Given
        String mediaType = MediaType.APPLICATION_XML_VALUE;

        SpiPaymentInfo spiPaymentInfo = new SpiPaymentInfo(PAYMENT_PRODUCT);
        spiPaymentInfo.setPaymentType(PaymentType.SINGLE);
        spiPaymentInfo.setPaymentId(PAYMENT_ID);
        spiPaymentInfo.setPaymentStatus(TransactionStatus.ACSP);

        SpiGetPaymentStatusResponse spiGetPaymentStatusResponse = new SpiGetPaymentStatusResponse(spiPaymentInfo.getPaymentStatus(), null, SpiGetPaymentStatusResponse.RESPONSE_TYPE_JSON, null);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn("".getBytes());
        when(generalPaymentService.getPaymentStatusById(PaymentTypeTO.valueOf(spiPaymentInfo.getPaymentType().name()),
                                                        mediaType, spiPaymentInfo.getPaymentId(),
                                                        spiPaymentInfo.getPaymentStatus(), spiAspspConsentDataProvider.loadAspspConsentData()))
                .thenReturn(buildSpiResponse(spiGetPaymentStatusResponse));

        //When
        SpiResponse<SpiGetPaymentStatusResponse> paymentStatusById = commonPaymentSpi.getPaymentStatusById(SPI_CONTEXT_DATA, mediaType, spiPaymentInfo, spiAspspConsentDataProvider);

        //Then
        assertEquals(spiGetPaymentStatusResponse, paymentStatusById.getPayload());
    }

    private <T> SpiResponse<T> buildSpiResponse(T responsePayload) {
        return SpiResponse.<T>builder()
                       .payload(responsePayload)
                       .build();
    }
}