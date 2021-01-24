package de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation;

import de.adorsys.aspsp.xs2a.connector.cms.CmsPsuPisClient;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiCommonPaymentTOMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaResponseMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.*;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.ledgers.keycloak.client.api.KeycloakTokenService;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.payment.TransactionStatusTO;
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
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.core.profile.PaymentType;
import de.adorsys.psd2.xs2a.core.tpp.TppInfo;
import de.adorsys.psd2.xs2a.service.RequestProviderService;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.*;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPaymentInfo;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import feign.FeignException;
import feign.Request;
import feign.Response;
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
import org.springframework.util.CollectionUtils;

import java.nio.charset.Charset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentAuthorisationSpiImplTest {

    private final static String PAYMENT_PRODUCT = "sepa-credit-transfers";
    private final static String PSU_ID = "anton.brueckner";
    private final static String INSTANCE_ID = "test-instance-id";
    private static final SpiPsuData PSU_ID_DATA_1 = SpiPsuData.builder()
                                                            .psuId(PSU_ID)
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
    private static final SpiPsuData PSU_ID_DATA_2 = SpiPsuData.builder()
                                                            .psuId("11")
                                                            .psuIdType("22")
                                                            .psuCorporateId("33")
                                                            .psuCorporateIdType("44")
                                                            .psuIpAddress("55")
                                                            .psuIpPort("66")
                                                            .psuUserAgent("77")
                                                            .psuGeoLocation("88")
                                                            .psuAccept("99")
                                                            .psuAcceptCharset("1010")
                                                            .psuAcceptEncoding("1111")
                                                            .psuAcceptLanguage("1212")
                                                            .psuHttpMethod("1313")
                                                            .psuDeviceId(UUID.randomUUID())
                                                            .build();
    private static final String ACCESS_TOKEN = "access_token";
    private static final SpiContextData SPI_CONTEXT_DATA = new SpiContextData(PSU_ID_DATA_1, new TppInfo(), UUID.randomUUID(), UUID.randomUUID(), ACCESS_TOKEN, null, null);
    private static final String AUTHORISATION_ID = "6f3c444d-c664-4cfc-aff3-576651000726";
    private static final String AUTHENTICATION_METHOD_ID = "VJJwaiPJT2EptJO0jqL37E";
    private static final byte[] CONSENT_DATA_BYTES = "consent_data".getBytes();
    private static final String PAYMENT_ID = "c966f143-f6a2-41db-9036-8abaeeef3af7";
    private static final String SECRET = "12345";

    @InjectMocks
    private PaymentAuthorisationSpiImpl authorisationSpi;

    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;
    @Mock
    private AspspConsentDataService consentDataService;
    @Mock
    private GeneralAuthorisationService authorisationService;
    @Mock
    private AuthRequestInterceptor authRequestInterceptor;
    @Mock
    private PaymentRestClient paymentRestClient;
    @Mock
    private FeignExceptionReader feignExceptionReader;
    @Mock
    private CmsPsuPisClient cmsPsuPisClient;
    @Mock
    private GeneralPaymentService generalPaymentService;
    @Mock
    private RedirectScaRestClient redirectScaRestClient;
    @Spy
    private ScaResponseMapper scaResponseMapper = Mappers.getMapper(ScaResponseMapper.class);
    @Mock
    private LedgersSpiCommonPaymentTOMapper ledgersSpiCommonPaymentTOMapper;
    @Mock
    private KeycloakTokenService keycloakTokenService;
    @Mock
    private RequestProviderService requestProviderService;
    @Mock
    private LoginAttemptAspspConsentDataService loginAttemptAspspConsentDataService;

    @Spy
    private ScaMethodConverter scaMethodConverter = Mappers.getMapper(ScaMethodConverter.class);

    private SpiPaymentInfo businessObject;

    @BeforeEach
    void setUp() {
        businessObject = new SpiPaymentInfo(PAYMENT_PRODUCT);
        businessObject.setPaymentId(PAYMENT_ID);
        businessObject.setPaymentType(PaymentType.SINGLE);
    }

    @Test
    void authorisePsu_success() {
        // Given
        when(keycloakTokenService.login(PSU_ID, SECRET))
                .thenReturn(getBearerTokenTO());
        GlobalScaResponseTO scaPaymentResponseTO = getGlobalScaResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());

        when(consentDataService.response(CONSENT_DATA_BYTES))
                .thenReturn(scaPaymentResponseTO);
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(CONSENT_DATA_BYTES);

        when(authorisationService.authorisePsuInternal(PAYMENT_ID, AUTHORISATION_ID, OpTypeTO.PAYMENT, scaPaymentResponseTO, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPsuAuthorisationResponse>builder()
                                    .payload(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS))
                                    .build());
        when(ledgersSpiCommonPaymentTOMapper.mapToPaymentTO(any(), any()))
                .thenReturn(getPaymentTO());
        when(generalPaymentService.initiatePaymentInLedgers(businessObject, PaymentTypeTO.SINGLE, getPaymentTO()))
                .thenReturn(scaPaymentResponseTO);
        byte[] responseBytes = "response_byte".getBytes();
        lenient().doNothing().when(spiAspspConsentDataProvider).updateAspspConsentData(responseBytes);

        // When
        SpiResponse<SpiPsuAuthorisationResponse> actual = authorisationSpi.authorisePsu(SPI_CONTEXT_DATA, AUTHORISATION_ID, PSU_ID_DATA_1, SECRET,
                                                                                        businessObject, spiAspspConsentDataProvider);
        // Then
        assertFalse(actual.hasError());
        assertEquals(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS), actual.getPayload());
        verify(authorisationService, times(1)).authorisePsuInternal(PAYMENT_ID, AUTHORISATION_ID, OpTypeTO.PAYMENT, scaPaymentResponseTO, spiAspspConsentDataProvider);
        verify(authRequestInterceptor, times(1)).setAccessToken(ACCESS_TOKEN);
    }

    @Test
    void authorisePsu_fail_wrongLogin() {
        // Given
        when(keycloakTokenService.login(PSU_ID, SECRET))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
        when(consentDataService.getLoginAttemptAspspConsentDataService()).thenReturn(loginAttemptAspspConsentDataService);
        when(loginAttemptAspspConsentDataService.response(any())).thenReturn(new LoginAttemptResponse());

        byte[] responseBytes = "response_byte".getBytes();
        lenient().doNothing().when(spiAspspConsentDataProvider).updateAspspConsentData(responseBytes);

        // When
        SpiResponse<SpiPsuAuthorisationResponse> actual = authorisationSpi.authorisePsu(SPI_CONTEXT_DATA, AUTHORISATION_ID, PSU_ID_DATA_1, SECRET,
                                                                                        businessObject, spiAspspConsentDataProvider);
        // Then
        assertFalse(actual.hasError());
        verify(authorisationService, never()).authorisePsuInternal(eq(PAYMENT_ID), eq(AUTHORISATION_ID), eq(OpTypeTO.PAYMENT), any(), eq(spiAspspConsentDataProvider));
        verify(authRequestInterceptor, never()).setAccessToken(ACCESS_TOKEN);
    }

    @Test
    void authorisePsu_multilevel_success() {
        // Given
        businessObject.setPaymentProduct(null);
        when(keycloakTokenService.login(PSU_ID, SECRET))
                .thenReturn(getBearerTokenTO());
        when(requestProviderService.getInstanceId())
                .thenReturn("1111");
        businessObject.setPsuDataList(Arrays.asList(PSU_ID_DATA_1, PSU_ID_DATA_2));
        GlobalScaResponseTO globalScaResponseTO = getGlobalScaResponseTO(ScaStatusTO.EXEMPTED);
        globalScaResponseTO.setScaMethods(Collections.emptyList());

        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);

        when(scaResponseMapper.toGlobalScaResponse(scaPaymentResponseTO))
                .thenReturn(globalScaResponseTO);
        when(generalPaymentService.initiatePaymentInLedgers(businessObject, PaymentTypeTO.SINGLE, null))
                .thenReturn(globalScaResponseTO);
        when(paymentRestClient.executePayment(PAYMENT_ID))
                .thenReturn(ResponseEntity.ok(getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED)));

        // When
        SpiResponse<SpiPsuAuthorisationResponse> actual = authorisationSpi.authorisePsu(SPI_CONTEXT_DATA, AUTHORISATION_ID, PSU_ID_DATA_1, SECRET,
                                                                                        businessObject, spiAspspConsentDataProvider);
        // Then
        assertFalse(actual.hasError());
        assertEquals(SpiAuthorisationStatus.SUCCESS, actual.getPayload().getSpiAuthorisationStatus());
        verify(authRequestInterceptor, times(2)).setAccessToken(ACCESS_TOKEN);
    }

    @Test
    void requestAvailableScaMethods_success() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaPaymentResponseTO = getGlobalScaResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, true))
                .thenReturn(scaPaymentResponseTO);
        when(redirectScaRestClient.getSCA(AUTHORISATION_ID))
                .thenReturn(ResponseEntity.ok(getGlobalScaResponseTO()));

        // When
        SpiResponse<SpiAvailableScaMethodsResponse> actual = authorisationSpi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                                         businessObject, spiAspspConsentDataProvider);
        // Then
        assertFalse(actual.hasError());
        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
    }

    @Test
    void requestAvailableScaMethods_success_noMethods() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaPaymentResponseTO = getGlobalScaResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, true))
                .thenReturn(scaPaymentResponseTO);
        when(redirectScaRestClient.getSCA(AUTHORISATION_ID))
                .thenReturn(ResponseEntity.ok(scaPaymentResponseTO));

        // When
        SpiResponse<SpiAvailableScaMethodsResponse> actual = authorisationSpi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                                         businessObject, spiAspspConsentDataProvider);
        // Then
        assertTrue(actual.hasError());
        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
    }

    @Test
    void requestAvailableScaMethods_fail_FeignException() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaPaymentResponseTO = getGlobalScaResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, true))
                .thenReturn(scaPaymentResponseTO);
        when(redirectScaRestClient.getSCA(AUTHORISATION_ID))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message"));

        // When
        SpiResponse<SpiAvailableScaMethodsResponse> actual = authorisationSpi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                                         businessObject, spiAspspConsentDataProvider);
        // Then
        assertTrue(actual.hasError());
        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
    }


    @Test
    void startScaDecoupled_success() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaPaymentResponseTO = getGlobalScaResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true))
                .thenReturn(scaPaymentResponseTO);
        doNothing()
                .when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        when(authorisationService.returnScaMethodSelection(spiAspspConsentDataProvider, getScaMethodsResponseTO(), AUTHENTICATION_METHOD_ID))
                .thenReturn(SpiResponse.<SpiAuthorizationCodeResult>builder()
                                    .payload(new SpiAuthorizationCodeResult())
                                    .build());
        when(redirectScaRestClient.selectMethod(AUTHORISATION_ID, AUTHENTICATION_METHOD_ID))
                .thenReturn(ResponseEntity.ok(getGlobalScaResponseTO()));

        // When
        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = authorisationSpi.startScaDecoupled(SPI_CONTEXT_DATA, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID,
                                                                                                      businessObject, spiAspspConsentDataProvider);
        // Then
        assertFalse(actual.hasError());
        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, true);
        verify(authRequestInterceptor, times(1)).setAccessToken(ACCESS_TOKEN);
        verify(redirectScaRestClient, times(1)).selectMethod(AUTHORISATION_ID, AUTHENTICATION_METHOD_ID);
        verify(authorisationService, times(1)).returnScaMethodSelection(spiAspspConsentDataProvider, getScaMethodsResponseTO(), AUTHENTICATION_METHOD_ID);
    }

    @Test
    void startScaDecoupled_errorOnReturningScaMethodSelection() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaPaymentResponseTO = getGlobalScaResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true))
                .thenReturn(scaPaymentResponseTO);
        doNothing()
                .when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        when(authorisationService.returnScaMethodSelection(spiAspspConsentDataProvider, getScaMethodsResponseTO(), AUTHENTICATION_METHOD_ID))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message"));
        when(redirectScaRestClient.selectMethod(AUTHORISATION_ID, AUTHENTICATION_METHOD_ID))
                .thenReturn(ResponseEntity.ok(getGlobalScaResponseTO()));

        // When
        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = authorisationSpi.startScaDecoupled(SPI_CONTEXT_DATA, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID,
                                                                                                      businessObject, spiAspspConsentDataProvider);
        // Then
        assertTrue(actual.hasError());
        assertEquals("", actual.getErrors().get(0).getMessageText());
        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, true);
        verify(authRequestInterceptor, times(1)).setAccessToken(ACCESS_TOKEN);
        verify(redirectScaRestClient, times(1)).selectMethod(AUTHORISATION_ID, AUTHENTICATION_METHOD_ID);
        verify(authorisationService, times(1)).returnScaMethodSelection(spiAspspConsentDataProvider, getScaMethodsResponseTO(), AUTHENTICATION_METHOD_ID);
        verify(feignExceptionReader, times(1)).getErrorMessage(any(FeignException.class));
    }

    @Test
    void getAuthorisePsuFailureMessage() {
        // When
        TppMessage actual = authorisationSpi.getAuthorisePsuFailureMessage(businessObject);

        // Then
        assertEquals(MessageErrorCode.PAYMENT_FAILED, actual.getErrorCode());
    }

    @Test
    void getScaMethods_success_empty() {
        // Given
        GlobalScaResponseTO response = getGlobalScaResponseTO();
        response.setScaMethods(null);

        // When
        Optional<List<ScaUserDataTO>> actual = authorisationSpi.getScaMethods(response);

        // Then
        assertFalse(actual.isEmpty());
        assertTrue(CollectionUtils.isEmpty(actual.get()));
    }

    @Test
    void getScaMethods_success() {
        // When
        Optional<List<ScaUserDataTO>> actual = authorisationSpi.getScaMethods(getGlobalScaResponseTO());

        // Then
        assertFalse(actual.isEmpty());
        assertEquals(getScaUserData(), actual.get().get(0));
    }

    @Test
    void requestAvailableScaMethods_authenticationMethodIdIsNull() {
        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = authorisationSpi.startScaDecoupled(SPI_CONTEXT_DATA, AUTHORISATION_ID, null,
                                                                                                      businessObject, spiAspspConsentDataProvider);
        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.SERVICE_NOT_SUPPORTED, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void initiateBusinessObject_TransactionStatusPATC() {
        businessObject.setStatus(TransactionStatus.PATC);
        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(globalScaResponseTO);

        GlobalScaResponseTO actual = authorisationSpi.initiateBusinessObject(businessObject, spiAspspConsentDataProvider, AUTHORISATION_ID);

        assertEquals(globalScaResponseTO, actual);

        verify(generalPaymentService, never()).initiatePaymentInLedgers(any(), any(), any());
    }

    @Test
    void executeBusinessObject_success() {
        SCAPaymentResponseTO scaPaymentResponseTO = new SCAPaymentResponseTO();
        scaPaymentResponseTO.setTransactionStatus(TransactionStatusTO.ACCC);

        when(paymentRestClient.executePayment(businessObject.getPaymentId())).thenReturn(ResponseEntity.ok(scaPaymentResponseTO));

        when(requestProviderService.getInstanceId()).thenReturn(INSTANCE_ID);

        GlobalScaResponseTO actual = authorisationSpi.executeBusinessObject(businessObject);
        assertNotNull(actual);

        verify(cmsPsuPisClient, times(1)).updatePaymentStatus(businessObject.getPaymentId(), TransactionStatus.ACCC, INSTANCE_ID);
    }

    @Test
    void executeBusinessObject_executePaymentResponseIsNull() {
        when(paymentRestClient.executePayment(businessObject.getPaymentId())).thenReturn(ResponseEntity.ok(null));

        GlobalScaResponseTO actual = authorisationSpi.executeBusinessObject(businessObject);
        assertNull(actual);

        verify(requestProviderService, never()).getInstanceId();
        verify(cmsPsuPisClient, never()).updatePaymentStatus(any(), any(), any());
    }

    @Test
    void executeBusinessObject_feignException() {
        when(paymentRestClient.executePayment(businessObject.getPaymentId()))
                .thenThrow(getFeignException());

        GlobalScaResponseTO actual = authorisationSpi.executeBusinessObject(businessObject);
        assertNull(actual);

        verify(requestProviderService, never()).getInstanceId();
        verify(cmsPsuPisClient, never()).updatePaymentStatus(any(), any(), any());
    }

    @Test
    void resolveErrorResponse_insufficientFunds() {
        FeignException feignException = getFeignException();

        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn("dev message");
        when(feignExceptionReader.getLedgersErrorCode(feignException)).thenReturn(LedgersErrorCode.INSUFFICIENT_FUNDS);

        SpiResponse<SpiPsuAuthorisationResponse> actual = authorisationSpi.resolveErrorResponse(businessObject, feignException);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.FORMAT_ERROR_PAYMENT_NOT_EXECUTED, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void resolveErrorResponse_requestValidationFailure() {
        FeignException feignException = getFeignException();

        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn("dev message");
        when(feignExceptionReader.getLedgersErrorCode(feignException)).thenReturn(LedgersErrorCode.REQUEST_VALIDATION_FAILURE);

        SpiResponse<SpiPsuAuthorisationResponse> actual = authorisationSpi.resolveErrorResponse(businessObject, feignException);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.PRODUCT_UNKNOWN, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void resolveErrorResponse_otherErrors() {
        FeignException feignException = getFeignException();

        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn("dev message");
        when(feignExceptionReader.getLedgersErrorCode(feignException)).thenReturn(LedgersErrorCode.PSU_AUTH_ATTEMPT_INVALID);

        SpiResponse<SpiPsuAuthorisationResponse> actual = authorisationSpi.resolveErrorResponse(businessObject, feignException);

        assertTrue(actual.isSuccessful());
        assertFalse(actual.getPayload().isScaExempted());
        assertEquals(SpiAuthorisationStatus.FAILURE, actual.getPayload().getSpiAuthorisationStatus());
    }

    private GlobalScaResponseTO getGlobalScaResponseTO(ScaStatusTO scaStatusTO) {
        GlobalScaResponseTO scaPaymentResponseTO = new GlobalScaResponseTO();
        scaPaymentResponseTO.setOperationObjectId(PAYMENT_ID);
        scaPaymentResponseTO.setOpType(OpTypeTO.PAYMENT);
        scaPaymentResponseTO.setAuthorisationId(AUTHORISATION_ID);
        scaPaymentResponseTO.setScaStatus(scaStatusTO);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        scaPaymentResponseTO.setBearerToken(bearerToken);
        return scaPaymentResponseTO;
    }


    private GlobalScaResponseTO getScaMethodsResponseTO() {
        GlobalScaResponseTO scaResponseTO = new GlobalScaResponseTO();
        scaResponseTO.setAuthorisationId(AUTHORISATION_ID);
        scaResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);
        scaResponseTO.setScaMethods(Collections.singletonList(getScaUserData()));
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        scaResponseTO.setBearerToken(bearerToken);
        return scaResponseTO;
    }

    private ScaUserDataTO getScaUserData() {
        ScaUserDataTO userDataTO = new ScaUserDataTO();
        userDataTO.setScaMethod(ScaMethodTypeTO.EMAIL);
        userDataTO.setDecoupled(false);
        return userDataTO;
    }

    private GlobalScaResponseTO getGlobalScaResponseTO() {
        GlobalScaResponseTO scaPaymentResponseTO = new GlobalScaResponseTO();
        scaPaymentResponseTO.setAuthorisationId(AUTHORISATION_ID);
        scaPaymentResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.singletonList(getScaUserData()));
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        scaPaymentResponseTO.setBearerToken(bearerToken);
        return scaPaymentResponseTO;
    }

    private SCAPaymentResponseTO getScaPaymentResponseTO(ScaStatusTO scaStatusTO) {
        SCAPaymentResponseTO scaPaymentResponseTO = new SCAPaymentResponseTO();
        scaPaymentResponseTO.setPaymentId(PAYMENT_ID);
        scaPaymentResponseTO.setPaymentProduct(PAYMENT_PRODUCT);
        scaPaymentResponseTO.setAuthorisationId(AUTHORISATION_ID);
        scaPaymentResponseTO.setScaStatus(scaStatusTO);
        scaPaymentResponseTO.setTransactionStatus(TransactionStatusTO.ACTC);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        scaPaymentResponseTO.setBearerToken(bearerToken);
        return scaPaymentResponseTO;
    }


    private PaymentTO getPaymentTO() {
        PaymentTO paymentTO = new PaymentTO();
        paymentTO.setPaymentId(PAYMENT_ID);
        paymentTO.setPaymentType(PaymentTypeTO.SINGLE);
        paymentTO.setPaymentProduct(PAYMENT_PRODUCT);
        return paymentTO;
    }

    private BearerTokenTO getBearerTokenTO() {
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        return bearerToken;
    }

    private FeignException getFeignException() {
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