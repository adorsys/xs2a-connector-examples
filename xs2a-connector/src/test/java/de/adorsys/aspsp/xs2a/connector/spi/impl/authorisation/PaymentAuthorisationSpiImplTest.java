package de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation;

import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaLoginMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.CmsPaymentStatusUpdateService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionHandler;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.internal.PaymentInternalGeneral;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.middleware.api.domain.sca.OpTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCALoginResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.middleware.api.service.TokenStorageService;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.tpp.TppInfo;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationDecoupledScaResponse;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentAuthorisationSpiImplTest {

    private final static String PAYMENT_PRODUCT = "sepa-credit-transfers";
    private static final SpiPsuData PSU_ID_DATA_1 = new SpiPsuData("1", "2", "3", "4", "5");
    private static final SpiPsuData PSU_ID_DATA_2 = new SpiPsuData("11", "22", "33", "44", "55");
    private static final SpiContextData SPI_CONTEXT_DATA = new SpiContextData(PSU_ID_DATA_1, new TppInfo(), UUID.randomUUID(), UUID.randomUUID());
    private static final String AUTHORISATION_ID = "6f3c444d-c664-4cfc-aff3-576651000726";
    private static final String AUTHENTICATION_METHOD_ID = "VJJwaiPJT2EptJO0jqL37E";
    private static final String ACCESS_TOKEN = "access_token";
    private static final byte[] CONSENT_DATA_BYTES = "consent_data".getBytes();
    private static final String PAYMENT_ID = "c966f143-f6a2-41db-9036-8abaeeef3af7";
    private static final String SECRET = "12345";
    private static final SpiAuthorisationStatus SPI_AUTHORISATION_STATUS_FAILURE = SpiAuthorisationStatus.FAILURE;

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
    private TokenStorageService tokenStorageService;
    @Mock
    private CmsPaymentStatusUpdateService cmsPaymentStatusUpdateService;
    @Mock
    private PaymentInternalGeneral paymentInternalGeneral;

    @Spy
    private ScaMethodConverter scaMethodConverter = Mappers.getMapper(ScaMethodConverter.class);
    @Spy
    private ScaLoginMapper scaLoginMapper = Mappers.getMapper(ScaLoginMapper.class);

    private SpiSinglePayment businessObject;

    @BeforeEach
    void setUp() {
        businessObject = new SpiSinglePayment(PAYMENT_PRODUCT);
        businessObject.setPaymentId(PAYMENT_ID);
        businessObject.setPsuDataList(Collections.singletonList(PSU_ID_DATA_1));
    }

    @Test
    void authorisePsu_success() throws IOException {
        businessObject.setPaymentProduct(null);
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false)).thenReturn(scaPaymentResponseTO);

        when(authorisationService.authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiAuthorisationStatus>builder()
                                    .payload(SpiAuthorisationStatus.SUCCESS)
                                    .build());
        SCALoginResponseTO scaLoginResponseTO = new SCALoginResponseTO();
        scaLoginResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);
        when(tokenStorageService.fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class))
                .thenReturn(scaLoginResponseTO);
        doNothing().when(cmsPaymentStatusUpdateService).updatePaymentStatus(PAYMENT_ID, spiAspspConsentDataProvider);

        when(paymentInternalGeneral.initiatePaymentInternal(businessObject, CONSENT_DATA_BYTES)).thenReturn(scaPaymentResponseTO);
        byte[] responseBytes = "response_byte".getBytes();
        when(consentDataService.store(scaPaymentResponseTO)).thenReturn(responseBytes);
        lenient().doNothing().when(spiAspspConsentDataProvider).updateAspspConsentData(responseBytes);

        SpiResponse<SpiAuthorisationStatus> actual = authorisationSpi.authorisePsu(SPI_CONTEXT_DATA, PSU_ID_DATA_1, SECRET,
                                                                                   businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());
        assertEquals(SpiAuthorisationStatus.SUCCESS, actual.getPayload());

        verify(spiAspspConsentDataProvider, times(3)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false);
        verify(authorisationService, times(1)).authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider);
        verify(tokenStorageService, times(1)).fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class);
        verify(cmsPaymentStatusUpdateService, times(1)).updatePaymentStatus(PAYMENT_ID, spiAspspConsentDataProvider);
        verify(paymentInternalGeneral, times(1)).initiatePaymentInternal(businessObject, CONSENT_DATA_BYTES);
        verify(consentDataService, times(1)).store(scaPaymentResponseTO);
        verify(spiAspspConsentDataProvider, times(1)).updateAspspConsentData(responseBytes);
    }

    @Test
    void authorisePsu_multilevel_success() throws IOException {
        businessObject.setPaymentProduct(null);
        businessObject.setPsuDataList(Arrays.asList(PSU_ID_DATA_1, PSU_ID_DATA_2));
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false)).thenReturn(scaPaymentResponseTO);

        when(authorisationService.authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiAuthorisationStatus>builder()
                                    .payload(SpiAuthorisationStatus.SUCCESS)
                                    .build());
        SCALoginResponseTO scaLoginResponseTO = new SCALoginResponseTO();
        scaLoginResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);
        when(tokenStorageService.fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class))
                .thenReturn(scaLoginResponseTO);

        SpiResponse<SpiAuthorisationStatus> actual = authorisationSpi.authorisePsu(SPI_CONTEXT_DATA, PSU_ID_DATA_1, SECRET,
                                                                                   businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());
        assertEquals(SpiAuthorisationStatus.SUCCESS, actual.getPayload());

        verify(spiAspspConsentDataProvider, times(2)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false);
        verify(authorisationService, times(1)).authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider);
        verify(tokenStorageService, times(1)).fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class);
    }

    @Test
    void authorisePsu_FeignException() throws IOException {
        businessObject.setPaymentProduct(null);
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false)).thenReturn(scaPaymentResponseTO);

        when(authorisationService.authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiAuthorisationStatus>builder()
                                    .payload(SpiAuthorisationStatus.SUCCESS)
                                    .build());
        SCALoginResponseTO scaLoginResponseTO = new SCALoginResponseTO();
        scaLoginResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);
        when(tokenStorageService.fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class))
                .thenReturn(scaLoginResponseTO);

        when(paymentInternalGeneral.initiatePaymentInternal(businessObject, CONSENT_DATA_BYTES)).thenThrow(buildFeignException());

        SpiResponse<SpiAuthorisationStatus> actual = authorisationSpi.authorisePsu(SPI_CONTEXT_DATA, PSU_ID_DATA_1, SECRET,
                                                                                   businessObject, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());

        verify(spiAspspConsentDataProvider, times(3)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false);
        verify(authorisationService, times(1)).authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider);
        verify(tokenStorageService, times(1)).fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class);
        verify(paymentInternalGeneral, times(1)).initiatePaymentInternal(businessObject, CONSENT_DATA_BYTES);
        verify(consentDataService, never()).store(any());
        verify(spiAspspConsentDataProvider, times(1)).updateAspspConsentData(any());
    }

    private FeignException buildFeignException() {
        return FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, "message");
    }

    @Test
    void authorisePsu_onSuccessfulAuthorisation_paymentInternalError() throws IOException {
        businessObject.setPaymentProduct(null);
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false)).thenReturn(scaPaymentResponseTO);

        when(authorisationService.authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiAuthorisationStatus>builder()
                                    .payload(SpiAuthorisationStatus.SUCCESS)
                                    .build());
        SCALoginResponseTO scaLoginResponseTO = new SCALoginResponseTO();
        scaLoginResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);
        when(tokenStorageService.fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class))
                .thenReturn(scaLoginResponseTO);

        when(paymentInternalGeneral.initiatePaymentInternal(businessObject, CONSENT_DATA_BYTES)).thenReturn(null);

        SpiResponse<SpiAuthorisationStatus> actual = authorisationSpi.authorisePsu(SPI_CONTEXT_DATA, PSU_ID_DATA_1, SECRET,
                                                                                   businessObject, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.PAYMENT_FAILED, actual.getErrors().get(0).getErrorCode());

        verify(spiAspspConsentDataProvider, times(3)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false);
        verify(authorisationService, times(1)).authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider);
        verify(tokenStorageService, times(1)).fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class);
        verify(paymentInternalGeneral, times(1)).initiatePaymentInternal(businessObject, CONSENT_DATA_BYTES);
        verify(consentDataService, never()).store(any());
        verify(spiAspspConsentDataProvider, times(1)).updateAspspConsentData(any());
    }

    @Test
    void authorisePsu_formatError() throws IOException {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false)).thenReturn(scaPaymentResponseTO);

        when(authorisationService.authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiAuthorisationStatus>builder()
                                    .payload(SpiAuthorisationStatus.SUCCESS)
                                    .build());
        when(tokenStorageService.fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class))
                .thenThrow(new IOException());

        SpiResponse<SpiAuthorisationStatus> actual = authorisationSpi.authorisePsu(SPI_CONTEXT_DATA, PSU_ID_DATA_1, SECRET,
                                                                                   businessObject, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.FORMAT_ERROR_RESPONSE_TYPE, actual.getErrors().get(0).getErrorCode());

        verify(spiAspspConsentDataProvider, times(2)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false);
        verify(authorisationService, times(1)).authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider);
        verify(tokenStorageService, times(1)).fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class);
    }

    @Test
    void authorisePsu_failureStatus() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false)).thenReturn(scaPaymentResponseTO);

        when(authorisationService.authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiAuthorisationStatus>builder()
                                    .build());

        SpiResponse<SpiAuthorisationStatus> actual = authorisationSpi.authorisePsu(SPI_CONTEXT_DATA, PSU_ID_DATA_1, SECRET,
                                                                                   businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());
        assertEquals(SPI_AUTHORISATION_STATUS_FAILURE, actual.getPayload());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false);
        verify(authorisationService, times(1)).authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider);
    }

    @Test
    void authorisePsu_feignException() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        FeignException feignException = FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message");
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false))
                .thenThrow(feignException);
        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn("message");

        SpiResponse<SpiAuthorisationStatus> actual = authorisationSpi.authorisePsu(SPI_CONTEXT_DATA, PSU_ID_DATA_1, SECRET,
                                                                                   businessObject, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false);
        verify(feignExceptionReader, times(1)).getErrorMessage(any(FeignException.class));
    }

    @Test
    void requestAvailableScaMethods_success() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, true)).thenReturn(scaPaymentResponseTO);

        when(authorisationService.validateToken(ACCESS_TOKEN)).thenReturn(scaPaymentResponseTO.getBearerToken());
        byte[] responseBytes = "response_byte".getBytes();
        when(consentDataService.store(scaPaymentResponseTO)).thenReturn(responseBytes);
        doNothing().when(spiAspspConsentDataProvider).updateAspspConsentData(responseBytes);

        SpiResponse<List<SpiAuthenticationObject>> actual = authorisationSpi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                                        businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, true);
        verify(authorisationService, times(1)).validateToken(ACCESS_TOKEN);
        verify(consentDataService, times(1)).store(scaPaymentResponseTO);
        verify(spiAspspConsentDataProvider, times(1)).updateAspspConsentData(responseBytes);
    }

    @Test
    void requestAvailableScaMethods_noScaMethodsInResponseTO() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(null);
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, true)).thenReturn(scaPaymentResponseTO);

        when(authorisationService.validateToken(ACCESS_TOKEN)).thenReturn(scaPaymentResponseTO.getBearerToken());
        byte[] responseBytes = "response_byte".getBytes();
        when(consentDataService.store(scaPaymentResponseTO)).thenReturn(responseBytes);
        doNothing().when(spiAspspConsentDataProvider).updateAspspConsentData(responseBytes);

        SpiResponse<List<SpiAuthenticationObject>> actual = authorisationSpi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                                        businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, true);
        verify(authorisationService, times(1)).validateToken(ACCESS_TOKEN);
        verify(consentDataService, times(1)).store(scaPaymentResponseTO);
        verify(spiAspspConsentDataProvider, times(1)).updateAspspConsentData(responseBytes);
    }

    @Test
    void requestAvailableScaMethods_feignException() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, true))
                .thenReturn(scaPaymentResponseTO);

        when(authorisationService.validateToken(ACCESS_TOKEN))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message"));

        SpiResponse<List<SpiAuthenticationObject>> actual = authorisationSpi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                                        businessObject, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.FORMAT_ERROR_SCA_METHODS, actual.getErrors().get(0).getErrorCode());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, true);
        verify(authorisationService, times(1)).validateToken(ACCESS_TOKEN);
        verify(consentDataService, never()).store(any());
        verify(spiAspspConsentDataProvider, never()).updateAspspConsentData(any());
    }

    @Test
    void startScaDecoupled_success() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, true)).thenReturn(scaPaymentResponseTO);

        doNothing().when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        when(paymentRestClient.selectMethod(PAYMENT_ID, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID))
                .thenReturn(ResponseEntity.ok(scaPaymentResponseTO));
        when(authorisationService.returnScaMethodSelection(spiAspspConsentDataProvider, scaPaymentResponseTO))
                .thenReturn(SpiResponse.<SpiAuthorizationCodeResult>builder()
                                    .payload(new SpiAuthorizationCodeResult())
                                    .build());

        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = authorisationSpi.startScaDecoupled(SPI_CONTEXT_DATA, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID,
                                                                                                      businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, true);
        verify(authRequestInterceptor, times(1)).setAccessToken(ACCESS_TOKEN);
        verify(paymentRestClient, times(1)).selectMethod(PAYMENT_ID, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID);
        verify(authorisationService, times(1)).returnScaMethodSelection(spiAspspConsentDataProvider, scaPaymentResponseTO);
    }

    @Test
    void startScaDecoupled_errorOnReturningScaMethodSelection() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);

        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, true)).thenReturn(scaPaymentResponseTO);

        doNothing().when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        when(paymentRestClient.selectMethod(PAYMENT_ID, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID))
                .thenReturn(ResponseEntity.ok(scaPaymentResponseTO));
        FeignException feignException = FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message");
        when(authorisationService.returnScaMethodSelection(spiAspspConsentDataProvider, scaPaymentResponseTO))
                .thenThrow(feignException);
        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn("message");

        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = authorisationSpi.startScaDecoupled(SPI_CONTEXT_DATA, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID,
                                                                                                      businessObject, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals("", actual.getErrors().get(0).getMessageText());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, true);
        verify(authRequestInterceptor, times(1)).setAccessToken(ACCESS_TOKEN);
        verify(paymentRestClient, times(1)).selectMethod(PAYMENT_ID, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID);
        verify(authorisationService, times(1)).returnScaMethodSelection(spiAspspConsentDataProvider, scaPaymentResponseTO);
        verify(feignExceptionReader, times(1)).getErrorMessage(any(FeignException.class));
    }

    @Test
    void startScaDecoupled_scaSelected() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.FINALISED);
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, true)).thenReturn(scaPaymentResponseTO);

        when(authorisationService.getResponseIfScaSelected(spiAspspConsentDataProvider, scaPaymentResponseTO))
                .thenReturn(SpiResponse.<SpiAuthorizationCodeResult>builder()
                                    .payload(new SpiAuthorizationCodeResult())
                                    .build());

        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = authorisationSpi.startScaDecoupled(SPI_CONTEXT_DATA, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID,
                                                                                                      businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, true);
        verify(authorisationService, times(1)).getResponseIfScaSelected(spiAspspConsentDataProvider, scaPaymentResponseTO);
    }

    @Test
    void requestAvailableScaMethods_authenticationMethodIdIsNull() {
        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = authorisationSpi.startScaDecoupled(SPI_CONTEXT_DATA, AUTHORISATION_ID, null,
                                                                                                      businessObject, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.SERVICE_NOT_SUPPORTED, actual.getErrors().get(0).getErrorCode());
    }

    private SCAPaymentResponseTO getScaPaymentResponseTO(ScaStatusTO scaStatusTO) {
        SCAPaymentResponseTO scaPaymentResponseTO = new SCAPaymentResponseTO();
        scaPaymentResponseTO.setPaymentId(PAYMENT_ID);
        scaPaymentResponseTO.setPaymentProduct(PaymentProductTO.SEPA.getValue());
        scaPaymentResponseTO.setAuthorisationId(AUTHORISATION_ID);
        scaPaymentResponseTO.setScaStatus(scaStatusTO);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        scaPaymentResponseTO.setBearerToken(bearerToken);
        return scaPaymentResponseTO;
    }
}