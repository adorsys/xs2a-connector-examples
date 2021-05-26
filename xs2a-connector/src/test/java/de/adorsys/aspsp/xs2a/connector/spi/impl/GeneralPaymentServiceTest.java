package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.aspsp.xs2a.connector.cms.CmsPsuPisClient;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaResponseMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.payment.TransactionStatusTO;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.OpTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.ledgers.rest.client.RedirectScaRestClient;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.service.RequestProviderService;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiBulkPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiGetPaymentStatusResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.Charset;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeneralPaymentServiceTest {
    private final static String PAYMENT_PRODUCT = "sepa-credit-transfers";
    private static final String ANY_MEDIA_TYPE = MediaType.ALL_VALUE;
    private static final String JSON_MEDIA_TYPE = MediaType.APPLICATION_JSON_VALUE;
    private static final String XML_MEDIA_TYPE = MediaType.APPLICATION_XML_VALUE;
    private static final String PSU_MESSAGE = "Mocked PSU message from SPI for this payment";
    private static final byte[] BYTES = "data".getBytes();
    private static final String ACCESS_TOKEN = "access_token";
    private static final String PAYMENT_ID = "payment id";
    private static final String AUTHORISATION_ID = "authorisation id";
    private static final String TAN_NUMBER = "123456";
    private final static String INSTANCE_ID = "test-instance-id";
    private final static String DEBTOR_NAME = "Mocked debtorName";

    private final JsonReader jsonReader = new JsonReader();

    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;
    @Mock
    private AuthRequestInterceptor authRequestInterceptor;
    @Mock
    private AspspConsentDataService consentDataService;
    @Mock
    private FeignExceptionReader feignExceptionReader;
    @Mock
    private PaymentRestClient paymentRestClient;
    @Mock
    private LedgersSpiPaymentMapper paymentMapper;
    @Mock
    private MultilevelScaService multilevelScaService;
    @Mock
    private RedirectScaRestClient redirectScaRestClient;
    @Mock
    private CmsPsuPisClient cmsPsuPisClient;
    @Mock
    private RequestProviderService requestProviderService;

    @Spy
    private ScaResponseMapper scaResponseMapper = Mappers.getMapper(ScaResponseMapper.class);

    private GeneralPaymentService generalPaymentService;
    private String paymentBodyXml;

    @BeforeEach
    void setUp() {
        paymentBodyXml = jsonReader.getStringFromFile("xml/payment-body.xml");
        generalPaymentService = new GeneralPaymentService(paymentRestClient, authRequestInterceptor, consentDataService,
                                                          feignExceptionReader, paymentBodyXml, multilevelScaService,
                                                          redirectScaRestClient, scaResponseMapper,
                                                          cmsPsuPisClient, requestProviderService);
    }

    @Test
    void getPaymentStatusById_withXmlMediaType_shouldReturnMockResponse() {
        // Given
        byte[] xmlBody = paymentBodyXml.getBytes();
        SpiGetPaymentStatusResponse expectedResponse = new SpiGetPaymentStatusResponse(TransactionStatus.ACSP, null, XML_MEDIA_TYPE, xmlBody, PSU_MESSAGE);

        // When
        SpiResponse<SpiGetPaymentStatusResponse> spiResponse = generalPaymentService.getPaymentStatusById(PaymentTypeTO.SINGLE, XML_MEDIA_TYPE, "payment id", TransactionStatus.ACSP, BYTES);

        // Then
        assertFalse(spiResponse.hasError());

        SpiGetPaymentStatusResponse payload = spiResponse.getPayload();
        assertEquals(expectedResponse, payload);
    }

    @Test
    void getPaymentStatusById_withNotAcspStatus_shouldReturnSameStatus() {
        // Given
        SpiGetPaymentStatusResponse expectedResponse = new SpiGetPaymentStatusResponse(TransactionStatus.ACSC, null, JSON_MEDIA_TYPE, null, PSU_MESSAGE);

        // When
        SpiResponse<SpiGetPaymentStatusResponse> spiResponse = generalPaymentService.getPaymentStatusById(PaymentTypeTO.SINGLE, ANY_MEDIA_TYPE, "payment id", TransactionStatus.ACSC, BYTES);

        // Then
        assertFalse(spiResponse.hasError());

        SpiGetPaymentStatusResponse payload = spiResponse.getPayload();
        assertEquals(expectedResponse, payload);
    }

    @Test
    void getPaymentByIdTransactionStatusRCVD() {
        //Given
        SpiPayment initialPayment = getSpiSingle(TransactionStatus.RCVD, "initialPayment");
        //When
        SpiResponse<SpiPayment> paymentById = generalPaymentService.getPaymentById(initialPayment, null, null);
        //Then
        assertTrue(paymentById.isSuccessful());
        assertEquals(initialPayment, paymentById.getPayload());
    }

    @Test
    void getPaymentByIdTransactionStatusACSP() {
        //Given
        SpiPayment initialPayment = getSpiSingle(TransactionStatus.ACSP, "initialPayment");
        SpiPayment paymentAspsp = getSpiSingle(TransactionStatus.ACSP, "paymentAspsp");

        PaymentTO paymentTO = new PaymentTO();
        paymentTO.setPaymentId("myPaymentId");
        paymentTO.setTransactionStatus(TransactionStatusTO.ACSP);

        GlobalScaResponseTO sca = new GlobalScaResponseTO();
        sca.setOperationObjectId(initialPayment.getPaymentId());
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("accessToken");
        sca.setBearerToken(bearerTokenTO);
        byte[] aspspConsentData = "".getBytes();

        doReturn(ResponseEntity.ok(paymentTO))
                .when(paymentRestClient).getPaymentById(paymentTO.getPaymentId());
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(aspspConsentData);
        doNothing()
                .when(authRequestInterceptor).setAccessToken(anyString());
        when(consentDataService.response(aspspConsentData))
                .thenReturn(sca);
        doReturn(paymentAspsp)
                .when(paymentMapper).toSpiSinglePayment(paymentTO);

        //When
        SpiResponse<SpiPayment> paymentById = generalPaymentService.getPaymentById(initialPayment, spiAspspConsentDataProvider, paymentMapper::toSpiSinglePayment);
        //Then
        assertTrue(paymentById.isSuccessful());
        assertEquals(paymentAspsp, paymentById.getPayload());
    }

    @Test
    void getPaymentByIdTransactionStatusACSP_withDebtorName() {
        //Given
        SpiPayment input = getSpiSingleWithDebtorName(TransactionStatus.ACSP);
        SpiPayment expected = getSpiSingleWithDebtorName(TransactionStatus.ACSP);

        PaymentTO paymentTO = getPaymentToWithDebtorName();

        GlobalScaResponseTO sca = getGlobalScaResponseTO();

        byte[] aspspConsentData = BYTES;

        doReturn(ResponseEntity.ok(paymentTO))
                .when(paymentRestClient).getPaymentById(paymentTO.getPaymentId());
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(aspspConsentData);
        doNothing()
                .when(authRequestInterceptor).setAccessToken(anyString());
        when(consentDataService.response(aspspConsentData))
                .thenReturn(sca);
        doReturn(expected)
                .when(paymentMapper).toSpiSinglePayment(paymentTO);

        //When
        SpiResponse<SpiPayment> paymentById = generalPaymentService.getPaymentById(input, spiAspspConsentDataProvider, paymentMapper::toSpiSinglePayment);

        //Then
        assertTrue(paymentById.isSuccessful());
        assertEquals(expected.getDebtorName(), paymentById.getPayload().getDebtorName());
        assertEquals(expected, paymentById.getPayload());
    }

    @Test
    void getPaymentByIdTransactionStatusACSP_BulkPaymentWithDebtorName() {
        //Given
        SpiBulkPayment input = new SpiBulkPayment();
        input.setPaymentStatus(TransactionStatus.ACSP);
        input.setDebtorName(DEBTOR_NAME);
        SpiPayment expected = new SpiBulkPayment();
        expected.setDebtorName(DEBTOR_NAME);
        expected.setPaymentStatus(TransactionStatus.ACSP);

        PaymentTO paymentTO = getPaymentToWithDebtorName();

        GlobalScaResponseTO sca = getGlobalScaResponseTO();

        byte[] aspspConsentData = BYTES;

        doReturn(ResponseEntity.ok(paymentTO))
                .when(paymentRestClient).getPaymentById(paymentTO.getPaymentId());
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(aspspConsentData);
        doNothing()
                .when(authRequestInterceptor).setAccessToken(anyString());
        when(consentDataService.response(aspspConsentData))
                .thenReturn(sca);
        doReturn(expected)
                .when(paymentMapper).mapToSpiBulkPayment(paymentTO);

        //When
        SpiResponse<SpiPayment> paymentById = generalPaymentService.getPaymentById(input, spiAspspConsentDataProvider, paymentMapper::mapToSpiBulkPayment);

        //Then
        assertTrue(paymentById.isSuccessful());
        assertEquals(expected.getDebtorName(), paymentById.getPayload().getDebtorName());
        assertEquals(expected, paymentById.getPayload());
    }


    @Test
    void getPaymentByIdTransactionStatusRCVD_withDebtorName() {
        //Given
        SpiPayment input = getSpiSingleWithDebtorName(TransactionStatus.RCVD);
        SpiPayment expected = getSpiSingleWithDebtorName(TransactionStatus.RCVD);

        //When
        SpiResponse<SpiPayment> paymentById = generalPaymentService.getPaymentById(input, spiAspspConsentDataProvider, paymentMapper::toSpiSinglePayment);

        //Then
        assertTrue(paymentById.isSuccessful());
        assertEquals(expected.getDebtorName(), paymentById.getPayload().getDebtorName());
        assertEquals(expected, paymentById.getPayload());
    }

    @Test
    void getPaymentByIdTransactionStatusRCVD_BulkPaymentWithDebtorName() {
        //Given
        SpiBulkPayment input = getBulkPaymentWithDebtorName();
        SpiBulkPayment expected = getBulkPaymentWithDebtorName();

        //When
        SpiResponse<SpiPayment> paymentById = generalPaymentService.getPaymentById(input, spiAspspConsentDataProvider, paymentMapper::mapToSpiBulkPayment);

        //Then
        assertTrue(paymentById.isSuccessful());
        assertEquals(expected.getDebtorName(), paymentById.getPayload().getDebtorName());
        assertEquals(expected, paymentById.getPayload());
    }

    @Test
    void firstCallInstantiatingPayment_LedgersError() {
        SpiPayment initialPayment = getSpiSingle(TransactionStatus.RCVD, "initialPayment");

        SpiAccountReference spiAccountReference = jsonReader.getObjectFromFile("json/spi/impl/account-reference.json", SpiAccountReference.class);

        SpiSinglePaymentInitiationResponse responsePayload = new SpiSinglePaymentInitiationResponse();

        SpiPsuData spiPsuData = SpiPsuData.builder().build();

        when(multilevelScaService.isMultilevelScaRequired(spiPsuData, Collections.singleton(spiAccountReference))).thenThrow(buildFeignException()); //spiPsuData, spiAccountReferences

        SpiResponse<SpiPaymentInitiationResponse> actualResponse = generalPaymentService.firstCallInstantiatingPayment(PaymentTypeTO.SINGLE, initialPayment, spiAspspConsentDataProvider, responsePayload, spiPsuData, Collections.singleton(spiAccountReference));

        assertFalse(actualResponse.getErrors().isEmpty());
        assertNull(actualResponse.getPayload());
        assertTrue(actualResponse.getErrors().contains(new TppMessage(MessageErrorCode.FORMAT_ERROR_UNKNOWN_ACCOUNT)));
    }

    @Test
    void firstCallInstantiatingPayment_Success() {
        GlobalScaResponseTO response = new GlobalScaResponseTO();
        response.setOperationObjectId("myPaymentId");
        response.setOpType(OpTypeTO.PAYMENT);
        response.setMultilevelScaRequired(true);

        SpiPayment initialPayment = getSpiSingle(TransactionStatus.RCVD, "initialPayment");

        SpiAccountReference spiAccountReference = jsonReader.getObjectFromFile("json/spi/impl/account-reference.json", SpiAccountReference.class);

        SpiSinglePaymentInitiationResponse responsePayload = new SpiSinglePaymentInitiationResponse();

        SpiPsuData spiPsuData = SpiPsuData.builder().build();

        when(multilevelScaService.isMultilevelScaRequired(spiPsuData, Collections.singleton(spiAccountReference))).thenReturn(true); //spiPsuData, spiAccountReferences

        SpiResponse<SpiPaymentInitiationResponse> actualResponse = generalPaymentService.firstCallInstantiatingPayment(PaymentTypeTO.SINGLE, initialPayment, spiAspspConsentDataProvider, responsePayload, spiPsuData, Collections.singleton(spiAccountReference));

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(consentDataService, times(1)).store(response, false);
        verify(spiAspspConsentDataProvider, times(1)).updateAspspConsentData(any());
    }

    @Test
    void getPaymentStatusById_success() {
        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setOperationObjectId(PAYMENT_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        globalScaResponseTO.setBearerToken(bearerToken);

        when(consentDataService.response(BYTES)).thenReturn(globalScaResponseTO);
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        when(paymentRestClient.getPaymentStatusById(PAYMENT_ID)).thenReturn(ResponseEntity.ok(TransactionStatusTO.ACSP));

        SpiResponse<SpiGetPaymentStatusResponse> actual = generalPaymentService.getPaymentStatusById(PaymentTypeTO.SINGLE, JSON_MEDIA_TYPE, PAYMENT_ID, TransactionStatus.ACSP, BYTES);

        assertTrue(actual.isSuccessful());
        assertEquals(TransactionStatus.ACSP, actual.getPayload().getTransactionStatus());
        assertEquals(SpiGetPaymentStatusResponse.RESPONSE_TYPE_JSON, actual.getPayload().getResponseContentType());
        assertEquals(PSU_MESSAGE, actual.getPayload().getPsuMessage());
        assertNull(actual.getPayload().getFundsAvailable());
        assertNull(actual.getPayload().getPaymentStatusRaw());

        verify(authRequestInterceptor, times(1)).setAccessToken(null);
    }

    @Test
    void getPaymentStatusById_feignException() {
        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setOperationObjectId(PAYMENT_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        globalScaResponseTO.setBearerToken(bearerToken);

        when(consentDataService.response(BYTES)).thenReturn(globalScaResponseTO);
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        FeignException feignException = buildFeignException();
        when(paymentRestClient.getPaymentStatusById(PAYMENT_ID)).thenThrow(feignException);
        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn("dev message");

        SpiResponse<SpiGetPaymentStatusResponse> actual = generalPaymentService.getPaymentStatusById(PaymentTypeTO.SINGLE, JSON_MEDIA_TYPE, PAYMENT_ID, TransactionStatus.ACSP, BYTES);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.FORMAT_ERROR, actual.getErrors().get(0).getErrorCode());

        verify(authRequestInterceptor, times(1)).setAccessToken(null);
    }

    @Test
    void getPaymentStatusById_paymentStatusIsIncorrect() {
        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setOperationObjectId(PAYMENT_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        globalScaResponseTO.setBearerToken(bearerToken);

        when(consentDataService.response(BYTES)).thenReturn(globalScaResponseTO);
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        when(paymentRestClient.getPaymentStatusById(PAYMENT_ID)).thenReturn(ResponseEntity.ok(null));

        assertThrows(IllegalStateException.class, () -> generalPaymentService.getPaymentStatusById(PaymentTypeTO.SINGLE, JSON_MEDIA_TYPE, PAYMENT_ID, TransactionStatus.ACSP, BYTES));

        verify(authRequestInterceptor, times(1)).setAccessToken(null);
    }

    @Test
    void verifyScaAuthorisationAndExecutePaymentWithPaymentResponse() {
        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setOperationObjectId(PAYMENT_ID);
        globalScaResponseTO.setAuthorisationId(AUTHORISATION_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        globalScaResponseTO.setBearerToken(bearerToken);

        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        spiScaConfirmation.setTanNumber(TAN_NUMBER);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(BYTES);
        when(consentDataService.response(BYTES)).thenReturn(globalScaResponseTO);
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        when(redirectScaRestClient.validateScaCode(AUTHORISATION_ID, TAN_NUMBER)).thenReturn(ResponseEntity.ok(globalScaResponseTO));
        SCAPaymentResponseTO scaPaymentResponseTO = new SCAPaymentResponseTO();
        scaPaymentResponseTO.setPaymentId(PAYMENT_ID);
        scaPaymentResponseTO.setTransactionStatus(TransactionStatusTO.ACCP);
        when(paymentRestClient.executePayment(PAYMENT_ID)).thenReturn(ResponseEntity.accepted().body(scaPaymentResponseTO));
        when(requestProviderService.getInstanceId()).thenReturn(INSTANCE_ID);

        when(consentDataService.store(globalScaResponseTO)).thenReturn(BYTES);
        doNothing().when(spiAspspConsentDataProvider).updateAspspConsentData(BYTES);

        when(paymentRestClient.getPaymentStatusById(PAYMENT_ID)).thenReturn(ResponseEntity.ok(TransactionStatusTO.ACCP));

        SpiResponse<SpiPaymentExecutionResponse> actual = generalPaymentService.verifyScaAuthorisationAndExecutePaymentWithPaymentResponse(spiScaConfirmation, spiAspspConsentDataProvider);

        assertTrue(actual.isSuccessful());
        assertEquals(TransactionStatus.ACCP, actual.getPayload().getTransactionStatus());

        verify(cmsPsuPisClient, times(1)).updatePaymentStatus(PAYMENT_ID, TransactionStatus.ACCP, INSTANCE_ID);
        verify(authRequestInterceptor, times(1)).setAccessToken(null);
    }

    @Test
    void verifyScaAuthorisationAndExecutePaymentWithPaymentResponse_attemptFailure() {
        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setOperationObjectId(PAYMENT_ID);
        globalScaResponseTO.setAuthorisationId(AUTHORISATION_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        globalScaResponseTO.setBearerToken(bearerToken);

        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        spiScaConfirmation.setTanNumber(TAN_NUMBER);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(BYTES);
        when(consentDataService.response(BYTES)).thenReturn(globalScaResponseTO);
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        FeignException feignException = buildFeignException();
        when(redirectScaRestClient.validateScaCode(AUTHORISATION_ID, TAN_NUMBER)).thenThrow(feignException);
        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn("dev message");
        when(feignExceptionReader.getLedgersErrorCode(feignException)).thenReturn(LedgersErrorCode.SCA_VALIDATION_ATTEMPT_FAILED);

        SpiResponse<SpiPaymentExecutionResponse> actual = generalPaymentService.verifyScaAuthorisationAndExecutePaymentWithPaymentResponse(spiScaConfirmation, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());
        assertEquals(SpiAuthorisationStatus.ATTEMPT_FAILURE, actual.getPayload().getSpiAuthorisationStatus());

        verify(cmsPsuPisClient, never()).updatePaymentStatus(any(), any(), any());
        verify(authRequestInterceptor, times(1)).setAccessToken(null);
    }

    @Test
    void verifyScaAuthorisationAndExecutePaymentWithPaymentResponse_psuCredentialsInvalid() {
        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setOperationObjectId(PAYMENT_ID);
        globalScaResponseTO.setAuthorisationId(AUTHORISATION_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        globalScaResponseTO.setBearerToken(bearerToken);

        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        spiScaConfirmation.setTanNumber(TAN_NUMBER);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(BYTES);
        when(consentDataService.response(BYTES)).thenReturn(globalScaResponseTO);
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        FeignException feignException = buildFeignException();
        when(redirectScaRestClient.validateScaCode(AUTHORISATION_ID, TAN_NUMBER)).thenThrow(feignException);
        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn("dev message");
        when(feignExceptionReader.getLedgersErrorCode(feignException)).thenReturn(LedgersErrorCode.INSUFFICIENT_FUNDS);

        SpiResponse<SpiPaymentExecutionResponse> actual = generalPaymentService.verifyScaAuthorisationAndExecutePaymentWithPaymentResponse(spiScaConfirmation, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());
        assertNull(actual.getPayload());

        verify(cmsPsuPisClient, never()).updatePaymentStatus(any(), any(), any());
        verify(authRequestInterceptor, times(1)).setAccessToken(null);
    }

    @Test
    void executePaymentWithoutSca_excepted() {
        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setOperationObjectId(PAYMENT_ID);
        globalScaResponseTO.setScaStatus(ScaStatusTO.EXEMPTED);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        globalScaResponseTO.setBearerToken(bearerToken);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(BYTES);
        when(consentDataService.response(BYTES)).thenReturn(globalScaResponseTO);
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        when(paymentRestClient.getPaymentStatusById(PAYMENT_ID)).thenReturn(ResponseEntity.ok(TransactionStatusTO.ACCP));

        SpiResponse<SpiPaymentExecutionResponse> actual = generalPaymentService.executePaymentWithoutSca(spiAspspConsentDataProvider);

        assertTrue(actual.isSuccessful());
        assertEquals(TransactionStatus.ACCP, actual.getPayload().getTransactionStatus());
    }

    @Test
    void executePaymentWithoutSca_finalised() {
        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setOperationObjectId(PAYMENT_ID);
        globalScaResponseTO.setScaStatus(ScaStatusTO.FINALISED);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        globalScaResponseTO.setBearerToken(bearerToken);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(BYTES);
        when(consentDataService.response(BYTES)).thenReturn(globalScaResponseTO);
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        when(paymentRestClient.getPaymentStatusById(PAYMENT_ID)).thenReturn(ResponseEntity.ok(TransactionStatusTO.ACCP));

        SpiResponse<SpiPaymentExecutionResponse> actual = generalPaymentService.executePaymentWithoutSca(spiAspspConsentDataProvider);

        assertTrue(actual.isSuccessful());
        assertEquals(TransactionStatus.ACCP, actual.getPayload().getTransactionStatus());
    }

    @Test
    void executePaymentWithoutSca_transactionStatusNull() {
        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setOperationObjectId(PAYMENT_ID);
        globalScaResponseTO.setScaStatus(ScaStatusTO.FINALISED);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        globalScaResponseTO.setBearerToken(bearerToken);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(BYTES);
        when(consentDataService.response(BYTES)).thenReturn(globalScaResponseTO);
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        when(paymentRestClient.getPaymentStatusById(PAYMENT_ID)).thenReturn(ResponseEntity.ok(null));

        SpiResponse<SpiPaymentExecutionResponse> actual = generalPaymentService.executePaymentWithoutSca(spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.FORMAT_ERROR_PAYMENT_NOT_EXECUTED, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void executePaymentWithoutSca_feignException() {
        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setOperationObjectId(PAYMENT_ID);
        globalScaResponseTO.setScaStatus(ScaStatusTO.FINALISED);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        globalScaResponseTO.setBearerToken(bearerToken);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(BYTES);
        when(consentDataService.response(BYTES)).thenReturn(globalScaResponseTO);
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        FeignException feignException = buildFeignException();
        when(paymentRestClient.getPaymentStatusById(PAYMENT_ID)).thenThrow(feignException);
        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn("dev message");

        SpiResponse<SpiPaymentExecutionResponse> actual = generalPaymentService.executePaymentWithoutSca(spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.FORMAT_ERROR, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void initiatePaymentInLedgers() {
        PaymentTO paymentTO = new PaymentTO();

        when(paymentRestClient.initiatePayment(paymentTO))
                .thenReturn(ResponseEntity.ok(new SCAPaymentResponseTO()));

        GlobalScaResponseTO actual = generalPaymentService.initiatePaymentInLedgers(PaymentTypeTO.SINGLE, PaymentTypeTO.SINGLE, paymentTO);

        assertNotNull(actual);
        verify(authRequestInterceptor, times(1)).setAccessToken(null);
    }

    @Test
    void initiatePaymentCancellationInLedgers() {
        when(paymentRestClient.initiatePmtCancellation(PAYMENT_ID))
                .thenReturn(ResponseEntity.ok(new SCAPaymentResponseTO()));

        GlobalScaResponseTO actual = generalPaymentService.initiatePaymentCancellationInLedgers(PAYMENT_ID);

        assertNotNull(actual);
        verify(authRequestInterceptor, times(1)).setAccessToken(null);
    }

    private SpiSinglePayment getSpiSingle(TransactionStatus transactionStatus, String agent) {
        SpiSinglePayment spiPayment = new SpiSinglePayment(PAYMENT_PRODUCT);
        spiPayment.setPaymentId("myPaymentId");
        spiPayment.setCreditorAgent(agent);
        spiPayment.setPaymentStatus(transactionStatus);
        return spiPayment;
    }

    private SpiSinglePayment getSpiSingleWithDebtorName(TransactionStatus transactionStatus) {
        SpiSinglePayment spiPayment = new SpiSinglePayment(PAYMENT_PRODUCT);
        spiPayment.setPaymentId("myPaymentId");
        spiPayment.setDebtorName(DEBTOR_NAME);
        spiPayment.setPaymentStatus(transactionStatus);
        return spiPayment;
    }

    private PaymentTO getPaymentToWithDebtorName() {
        PaymentTO paymentTO = new PaymentTO();
        paymentTO.setPaymentId("myPaymentId");
        paymentTO.setTransactionStatus(TransactionStatusTO.ACSP);
        paymentTO.setDebtorName(DEBTOR_NAME);
        return paymentTO;
    }

    private SpiBulkPayment getBulkPaymentWithDebtorName() {
        SpiBulkPayment initialPayment = new SpiBulkPayment();
        initialPayment.setDebtorName(DEBTOR_NAME);
        return initialPayment;
    }

    private GlobalScaResponseTO getGlobalScaResponseTO() {
        SpiPayment initialPayment = getSpiSingleWithDebtorName(TransactionStatus.ACSP);
        GlobalScaResponseTO sca = new GlobalScaResponseTO();
        sca.setOperationObjectId(initialPayment.getPaymentId());
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("accessToken");
        sca.setBearerToken(bearerTokenTO);
        return sca;
    }

    private FeignException buildFeignException() {
        return FeignException.errorStatus("User doesn't have access to the requested account",
                                          buildErrorResponseForbidden());
    }

    private Response buildErrorResponseForbidden() {
        return Response.builder()
                       .status(403)
                       .request(Request.create(Request.HttpMethod.GET, "", Collections.emptyMap(), null, Charset.defaultCharset(), null))
                       .headers(Collections.emptyMap())
                       .build();
    }
}
