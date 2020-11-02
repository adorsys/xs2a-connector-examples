package de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation;

import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaResponseMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionHandler;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.aspsp.xs2a.util.TestSpiDataProvider;
import de.adorsys.ledgers.keycloak.client.api.KeycloakTokenService;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.OpTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.middleware.api.domain.um.ScaMethodTypeTO;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.ledgers.rest.client.RedirectScaRestClient;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.*;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentCancellationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCancellationSpiImplTest {

    private final static String PAYMENT_PRODUCT = "sepa-credit-transfers";
    private static final SpiPsuData PSU_ID_DATA = SpiPsuData.builder()
                                                          .psuId("1")
                                                          .psuIdType("2")
                                                          .psuCorporateId("3")
                                                          .psuCorporateIdType("4")
                                                          .psuIpAddress("5")
                                                          .psuIpPort("6")
                                                          .psuUserAgent("7")
                                                          .psuGeoLocation("8")
                                                          .psuAccept("9")
                                                          .psuAcceptCharset("10")
                                                          .psuAcceptEncoding("11")
                                                          .psuAcceptLanguage("12")
                                                          .psuHttpMethod("13")
                                                          .psuDeviceId(UUID.randomUUID())
                                                          .build();
    private static final SpiContextData SPI_CONTEXT_DATA = TestSpiDataProvider.getSpiContextData();
    private static final String AUTHORISATION_ID = "6f3c444d-c664-4cfc-aff3-576651000726";
    private static final String AUTHENTICATION_METHOD_ID = "VJJwaiPJT2EptJO0jqL37E";
    private static final String ACCESS_TOKEN = "access_token";
    private static final byte[] CONSENT_DATA_BYTES = "consent_data".getBytes();
    private static final String PAYMENT_ID = "c966f143-f6a2-41db-9036-8abaeeef3af7";
    private static final String SECRET = "12345";
    private static final String TAN_NUMBER = "32453454";

    @InjectMocks
    private PaymentCancellationSpiImpl authorisationSpi;

    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;
    @Mock
    private AspspConsentDataService consentDataService;
    @Mock
    private GeneralAuthorisationService authorisationService;
    @Mock
    private AuthRequestInterceptor authRequestInterceptor;
    @Mock
    private FeignExceptionReader feignExceptionReader;
    @Mock
    private RedirectScaRestClient redirectScaRestClient;
    @Mock
    private PaymentRestClient paymentRestClient;
    @Mock
    private KeycloakTokenService keycloakTokenService;
    @Mock
    private GeneralPaymentService paymentService;

    @Spy
    private ScaMethodConverter scaMethodConverter = Mappers.getMapper(ScaMethodConverter.class);
    @Spy
    private ScaResponseMapper scaResponseMapper = Mappers.getMapper(ScaResponseMapper.class);

    private SpiSinglePayment businessObject;
    private SpiScaConfirmation spiScaConfirmation;

    @BeforeEach
    void setUp() {
        businessObject = new SpiSinglePayment(PAYMENT_PRODUCT);
        businessObject.setPaymentId(PAYMENT_ID);

        spiScaConfirmation = new SpiScaConfirmation();
        spiScaConfirmation.setTanNumber(TAN_NUMBER);
    }

    @Test
    void initiatePaymentCancellation_transactionStatusRCVD() {
        businessObject.setPaymentStatus(TransactionStatus.RCVD);
        SpiResponse<SpiPaymentCancellationResponse> actual = authorisationSpi.initiatePaymentCancellation(SPI_CONTEXT_DATA, businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());
        assertNotNull(actual.getPayload());
        assertEquals(TransactionStatus.RCVD, actual.getPayload().getTransactionStatus());
        assertFalse(actual.getPayload().isCancellationAuthorisationMandated());
    }

    @Test
    void initiatePaymentCancellation_transactionStatusAnotherFromRCVD() {
        businessObject.setPaymentStatus(TransactionStatus.ACSP);
        SpiResponse<SpiPaymentCancellationResponse> actual = authorisationSpi.initiatePaymentCancellation(SPI_CONTEXT_DATA, businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());
        assertNotNull(actual.getPayload());
        assertEquals(TransactionStatus.ACSP, actual.getPayload().getTransactionStatus());
        assertTrue(actual.getPayload().isCancellationAuthorisationMandated());
    }

    @Test
    void cancelPaymentWithoutSca_scaStatusIsNotEXEMPTED() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaPaymentResponseTO);

        SpiResponse<SpiResponse.VoidResponse> actual = authorisationSpi.cancelPaymentWithoutSca(SPI_CONTEXT_DATA, businessObject, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.CANCELLATION_INVALID, actual.getErrors().get(0).getErrorCode());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, true);
    }

    @Test
    void cancelPaymentWithoutSca_scaStatusEXEMPTED_error() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaStatus(ScaStatusTO.EXEMPTED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaPaymentResponseTO);

        doNothing().when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        when(paymentRestClient.initiatePmtCancellation(PAYMENT_ID))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message"));

        SpiResponse<SpiResponse.VoidResponse> actual = authorisationSpi.cancelPaymentWithoutSca(SPI_CONTEXT_DATA, businessObject, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.FORMAT_ERROR_CANCELLATION, actual.getErrors().get(0).getErrorCode());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, true);
        verify(authRequestInterceptor, times(1)).setAccessToken(ACCESS_TOKEN);
        verify(paymentRestClient, times(1)).initiatePmtCancellation(PAYMENT_ID);
    }

    @Test
    void cancelPaymentWithoutSca_scaStatusEXEMPTED() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaStatus(ScaStatusTO.EXEMPTED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaPaymentResponseTO);

        doNothing().when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);

        SpiResponse<SpiResponse.VoidResponse> actual = authorisationSpi.cancelPaymentWithoutSca(SPI_CONTEXT_DATA, businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());
        assertEquals(SpiResponse.voidResponse(), actual.getPayload());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, true);
        verify(authRequestInterceptor, times(1)).setAccessToken(ACCESS_TOKEN);
    }

    @Test
    void cancelPaymentWithoutSca_transactionStatusRCVD() {
        businessObject.setPaymentStatus(TransactionStatus.RCVD);

        SpiResponse<SpiResponse.VoidResponse> actual = authorisationSpi.cancelPaymentWithoutSca(SPI_CONTEXT_DATA, businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());
        assertEquals(SpiResponse.voidResponse(), actual.getPayload());
    }

    @Test
    void verifyScaAuthorisationAndCancelPayment_success() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO globalScaResponseTO = getGlobalScaResponseTO(ScaStatusTO.PSUIDENTIFIED);

        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED));

        doNothing().when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        when(redirectScaRestClient.validateScaCode(AUTHORISATION_ID, TAN_NUMBER))
                .thenReturn(ResponseEntity.ok(globalScaResponseTO));

        SpiPaymentExecutionResponse expected = new SpiPaymentExecutionResponse(SpiAuthorisationStatus.SUCCESS);
        SpiResponse<SpiPaymentExecutionResponse> actual = authorisationSpi.verifyScaAuthorisationAndCancelPaymentWithResponse(SPI_CONTEXT_DATA, spiScaConfirmation,
                                                                                                                              businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());
        assertEquals(expected, actual.getPayload());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES);
        verify(authRequestInterceptor, times(3)).setAccessToken(ACCESS_TOKEN);
    }

    @Test
    void verifyScaAuthorisationAndCancelPayment_httpStatusNotOK() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(scaPaymentResponseTO);

        doNothing().when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        when(redirectScaRestClient.validateScaCode(AUTHORISATION_ID, TAN_NUMBER))
                .thenReturn(ResponseEntity.badRequest().build());

        SpiResponse<SpiPaymentExecutionResponse> actual = authorisationSpi.verifyScaAuthorisationAndCancelPaymentWithResponse(SPI_CONTEXT_DATA, spiScaConfirmation,
                                                                                                                              businessObject, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.UNAUTHORIZED_CANCELLATION, actual.getErrors().get(0).getErrorCode());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES);
        verify(authRequestInterceptor, times(1)).setAccessToken(ACCESS_TOKEN);
        verify(redirectScaRestClient, times(1)).validateScaCode(AUTHORISATION_ID, TAN_NUMBER);
    }

    @Test
    void verifyScaAuthorisationAndCancelPayment_exception() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(scaPaymentResponseTO);

        doNothing().when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);

        FeignException feignException = FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message");
        when(redirectScaRestClient.validateScaCode(AUTHORISATION_ID, TAN_NUMBER))
                .thenThrow(feignException);
        when(feignExceptionReader.getErrorCode(feignException)).thenReturn("error code");

        SpiResponse<SpiPaymentExecutionResponse> actual = authorisationSpi.verifyScaAuthorisationAndCancelPaymentWithResponse(SPI_CONTEXT_DATA, spiScaConfirmation,
                                                                                                                              businessObject, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES);
        verify(authRequestInterceptor, times(1)).setAccessToken(ACCESS_TOKEN);
    }

    @Test
    void verifyScaAuthorisationAndCancelPayment_exceptionAttemptFailure() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(scaPaymentResponseTO);

        doNothing().when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);

        FeignException feignException = FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message");

        when(redirectScaRestClient.validateScaCode(AUTHORISATION_ID, TAN_NUMBER))
                .thenThrow(feignException);
        when(feignExceptionReader.getErrorCode(feignException)).thenReturn("SCA_VALIDATION_ATTEMPT_FAILED");

        SpiPaymentExecutionResponse expected = new SpiPaymentExecutionResponse(SpiAuthorisationStatus.ATTEMPT_FAILURE);
        SpiResponse<SpiPaymentExecutionResponse> actual = authorisationSpi.verifyScaAuthorisationAndCancelPaymentWithResponse(SPI_CONTEXT_DATA, spiScaConfirmation,
                                                                                                                              businessObject, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertNotNull(actual.getPayload());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());
        assertEquals(expected, actual.getPayload());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES);
        verify(authRequestInterceptor, times(1)).setAccessToken(ACCESS_TOKEN);
        verify(redirectScaRestClient, times(1)).validateScaCode(AUTHORISATION_ID, TAN_NUMBER);
    }

    @Test
    void authorisePsu_success() {
        businessObject.setPaymentProduct(null);
        BearerTokenTO token = new BearerTokenTO();
        token.setAccess_token(ACCESS_TOKEN);

        when(keycloakTokenService.login(SPI_CONTEXT_DATA.getPsuData().getPsuId(), SECRET)).thenReturn(token);
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        GlobalScaResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(paymentService.initiatePaymentCancellationInLedgers(PAYMENT_ID)).thenReturn(scaPaymentResponseTO);

        when(authorisationService.authorisePsuInternal(PAYMENT_ID, AUTHORISATION_ID, OpTypeTO.CANCEL_PAYMENT, scaPaymentResponseTO, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPsuAuthorisationResponse>builder()
                                    .payload(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS))
                                    .build());

        SpiResponse<SpiPsuAuthorisationResponse> actual = authorisationSpi.authorisePsu(SPI_CONTEXT_DATA, AUTHORISATION_ID, PSU_ID_DATA, SECRET,
                                                                                        businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());
        assertEquals(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS), actual.getPayload());

        verify(keycloakTokenService).login(SPI_CONTEXT_DATA.getPsuData().getPsuId(), SECRET);
        verify(authRequestInterceptor, times(2)).setAccessToken(ACCESS_TOKEN);
        verify(paymentService).initiatePaymentCancellationInLedgers(PAYMENT_ID);
        verify(authorisationService).authorisePsuInternal(PAYMENT_ID, AUTHORISATION_ID, OpTypeTO.CANCEL_PAYMENT, scaPaymentResponseTO, spiAspspConsentDataProvider);
    }

    @Test
    void authorisePsu_formatError() {
        BearerTokenTO token = new BearerTokenTO();
        token.setAccess_token(ACCESS_TOKEN);

        when(keycloakTokenService.login(SPI_CONTEXT_DATA.getPsuData().getPsuId(), SECRET)).thenReturn(token);
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        GlobalScaResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(paymentService.initiatePaymentCancellationInLedgers(PAYMENT_ID)).thenThrow(FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message"));

        SpiResponse<SpiPsuAuthorisationResponse> actual = authorisationSpi.authorisePsu(SPI_CONTEXT_DATA, AUTHORISATION_ID, PSU_ID_DATA, SECRET,
                                                                                        businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());
        assertEquals(SpiAuthorisationStatus.FAILURE, actual.getPayload().getSpiAuthorisationStatus());

        verify(keycloakTokenService).login(SPI_CONTEXT_DATA.getPsuData().getPsuId(), SECRET);
        verify(authRequestInterceptor, times(2)).setAccessToken(ACCESS_TOKEN);
        verify(paymentService).initiatePaymentCancellationInLedgers(PAYMENT_ID);
        verify(authorisationService, never()).authorisePsuInternal(anyString(), anyString(), any(), any(GlobalScaResponseTO.class), any());
    }

    @Test
    void requestAvailableScaMethods_success() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaPaymentResponseTO);

        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);
        when(redirectScaRestClient.getSCA(AUTHORISATION_ID))
                .thenReturn(ResponseEntity.ok(scaPaymentResponseTO));

        SpiResponse<SpiAvailableScaMethodsResponse> actual = authorisationSpi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                                         businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, true);
        verify(authRequestInterceptor, times(2)).setAccessToken(ACCESS_TOKEN);
        verify(redirectScaRestClient, times(1)).getSCA(AUTHORISATION_ID);
    }

    @Test
    void requestAvailableScaMethods_scaMethodUnknown() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(null);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaPaymentResponseTO);

        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);
        when(redirectScaRestClient.getSCA(AUTHORISATION_ID))
                .thenReturn(ResponseEntity.ok(scaPaymentResponseTO));

        SpiResponse<SpiAvailableScaMethodsResponse> actual = authorisationSpi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                                         businessObject, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.SCA_METHOD_UNKNOWN_PROCESS_MISMATCH, actual.getErrors().get(0).getErrorCode());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, true);
        verify(authRequestInterceptor, times(2)).setAccessToken(ACCESS_TOKEN);
        verify(redirectScaRestClient, times(1)).getSCA(AUTHORISATION_ID);
    }

    @Test
    void requestAvailableScaMethods_feignExceptionOnGetCancelSca() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaPaymentResponseTO);

        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);
        when(redirectScaRestClient.getSCA(AUTHORISATION_ID))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message"));

        SpiResponse<SpiAvailableScaMethodsResponse> actual = authorisationSpi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                                         businessObject, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.FORMAT_ERROR_SCA_METHODS, actual.getErrors().get(0).getErrorCode());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, true);
        verify(authRequestInterceptor, times(2)).setAccessToken(ACCESS_TOKEN);
        verify(redirectScaRestClient, times(1)).getSCA(AUTHORISATION_ID);
    }

    @Test
    void startScaDecoupled_success() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaPaymentResponseTO);

        doNothing().when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        when(redirectScaRestClient.selectMethod(AUTHORISATION_ID, AUTHENTICATION_METHOD_ID))
                .thenReturn(ResponseEntity.ok(getGlobalScaResponseTO(ScaStatusTO.PSUIDENTIFIED)));
        when(authorisationService.returnScaMethodSelection(spiAspspConsentDataProvider, scaPaymentResponseTO, AUTHENTICATION_METHOD_ID))
                .thenReturn(SpiResponse.<SpiAuthorizationCodeResult>builder()
                                    .payload(new SpiAuthorizationCodeResult())
                                    .build());

        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = authorisationSpi.startScaDecoupled(SPI_CONTEXT_DATA, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID,
                                                                                                      businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, true);
        verify(authRequestInterceptor, times(1)).setAccessToken(ACCESS_TOKEN);
        verify(authorisationService, times(1)).returnScaMethodSelection(spiAspspConsentDataProvider, scaPaymentResponseTO, AUTHENTICATION_METHOD_ID);
    }

    @Test
    void startScaDecoupled_errorOnReturningScaMethodSelection() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaPaymentResponseTO);

        doNothing().when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        when(redirectScaRestClient.selectMethod(AUTHORISATION_ID, AUTHENTICATION_METHOD_ID))
                .thenReturn(ResponseEntity.ok(getGlobalScaResponseTO(ScaStatusTO.PSUIDENTIFIED)));
        FeignException feignException = FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message");
        when(authorisationService.returnScaMethodSelection(spiAspspConsentDataProvider, scaPaymentResponseTO, AUTHENTICATION_METHOD_ID))
                .thenThrow(feignException);
        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn("message");

        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = authorisationSpi.startScaDecoupled(SPI_CONTEXT_DATA, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID,
                                                                                                      businessObject, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals("", actual.getErrors().get(0).getMessageText());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, true);
        verify(authRequestInterceptor, times(1)).setAccessToken(ACCESS_TOKEN);
        verify(authorisationService, times(1)).returnScaMethodSelection(spiAspspConsentDataProvider, scaPaymentResponseTO, AUTHENTICATION_METHOD_ID);
    }

    @Test
    void startScaDecoupled_scaSelected() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.FINALISED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaPaymentResponseTO);

        when(authorisationService.getResponseIfScaSelected(spiAspspConsentDataProvider, scaPaymentResponseTO, AUTHENTICATION_METHOD_ID))
                .thenReturn(SpiResponse.<SpiAuthorizationCodeResult>builder()
                                    .payload(new SpiAuthorizationCodeResult())
                                    .build());

        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = authorisationSpi.startScaDecoupled(SPI_CONTEXT_DATA, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID,
                                                                                                      businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, true);
        verify(authorisationService, times(1)).getResponseIfScaSelected(spiAspspConsentDataProvider, scaPaymentResponseTO, AUTHENTICATION_METHOD_ID);
    }

    @Test
    void startScaDecoupled_authenticationMethodIdIsNull() {
        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = authorisationSpi.startScaDecoupled(SPI_CONTEXT_DATA, AUTHORISATION_ID, null,
                                                                                                      businessObject, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.SERVICE_NOT_SUPPORTED, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void requestAvailableScaMethods_validateStatuses_transactionRCVD() {
        businessObject.setPaymentStatus(TransactionStatus.RCVD);
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaPaymentResponseTO);

        SpiResponse<SpiAvailableScaMethodsResponse> actual = authorisationSpi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                                         businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());

        SpiAvailableScaMethodsResponse actualPayload = actual.getPayload();
        assertFalse(actualPayload.isScaExempted());
        assertTrue(actualPayload.getAvailableScaMethods().isEmpty());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, true);
    }

    @Test
    void requestAvailableScaMethods_validateStatuses_scaStatusEXEMTED() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        scaPaymentResponseTO.setScaStatus(ScaStatusTO.EXEMPTED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaPaymentResponseTO);

        SpiResponse<SpiAvailableScaMethodsResponse> actual = authorisationSpi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                                         businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());

        SpiAvailableScaMethodsResponse actualPayload = actual.getPayload();
        assertFalse(actualPayload.isScaExempted());
        assertTrue(actualPayload.getAvailableScaMethods().isEmpty());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, true);
    }

    @Test
    void initiateBusinessObject_notImplemented() {
        assertNull(authorisationSpi.initiateBusinessObject(businessObject, spiAspspConsentDataProvider, AUTHORISATION_ID));
    }

    @Test
    void getAuthorisePsuFailureMessage() {
        assertEquals(MessageErrorCode.PAYMENT_FAILED, authorisationSpi.getAuthorisePsuFailureMessage(businessObject).getErrorCode());
    }

    @Test
    void isFirstInitiationOfMultilevelSca() {
        assertTrue(authorisationSpi.isFirstInitiationOfMultilevelSca(businessObject, null));
    }

    @Test
    void executeBusinessObject() {
        SCAPaymentResponseTO scaPaymentResponseTO = new SCAPaymentResponseTO();
        when(paymentRestClient.executeCancelPayment(PAYMENT_ID)).thenReturn(ResponseEntity.ok(scaPaymentResponseTO));

        assertNotNull(authorisationSpi.executeBusinessObject(businessObject));
    }

    @Test
    void getScaMethods() {
        GlobalScaResponseTO globalScaResponseTO = getGlobalScaResponseTO(ScaStatusTO.PSUIDENTIFIED);
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);
        when(redirectScaRestClient.getSCA(AUTHORISATION_ID)).thenReturn(ResponseEntity.ok(globalScaResponseTO));

        Optional<List<ScaUserDataTO>> actual = authorisationSpi.getScaMethods(globalScaResponseTO);

        assertFalse(actual.isEmpty());
        assertEquals(1, actual.get().size());

        verify(authRequestInterceptor, times(2)).setAccessToken(ACCESS_TOKEN);
        verify(redirectScaRestClient).getSCA(AUTHORISATION_ID);
    }

    @Test
    void requestTrustedBeneficiaryFlag() {
        SpiResponse<Boolean> actual = authorisationSpi.requestTrustedBeneficiaryFlag(SPI_CONTEXT_DATA, businessObject, AUTHORISATION_ID, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());
        assertTrue(actual.getPayload());
    }

    private GlobalScaResponseTO getScaPaymentResponseTO(ScaStatusTO scaStatusTO) {
        GlobalScaResponseTO scaPaymentResponseTO = new GlobalScaResponseTO();
        scaPaymentResponseTO.setOperationObjectId(PAYMENT_ID);
        scaPaymentResponseTO.setAuthorisationId(AUTHORISATION_ID);
        scaPaymentResponseTO.setScaStatus(scaStatusTO);
        scaPaymentResponseTO.setScaMethods(Collections.singletonList(getScaUserData()));
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        scaPaymentResponseTO.setBearerToken(bearerToken);
        return scaPaymentResponseTO;
    }

    private ScaUserDataTO getScaUserData() {
        ScaUserDataTO userDataTO = new ScaUserDataTO();
        userDataTO.setScaMethod(ScaMethodTypeTO.EMAIL);
        userDataTO.setDecoupled(false);
        return userDataTO;
    }

    private GlobalScaResponseTO getGlobalScaResponseTO(ScaStatusTO scaStatusTO) {
        GlobalScaResponseTO scaPaymentResponseTO = new GlobalScaResponseTO();
        scaPaymentResponseTO.setOperationObjectId(PAYMENT_ID);
        scaPaymentResponseTO.setAuthorisationId(AUTHORISATION_ID);
        scaPaymentResponseTO.setScaStatus(scaStatusTO);
        scaPaymentResponseTO.setScaMethods(Collections.singletonList(getScaUserData()));
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        scaPaymentResponseTO.setBearerToken(bearerToken);
        return scaPaymentResponseTO;
    }
}