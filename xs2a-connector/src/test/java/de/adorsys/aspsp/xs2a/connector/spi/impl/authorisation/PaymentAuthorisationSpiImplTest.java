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
import de.adorsys.psd2.xs2a.spi.domain.authorisation.*;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import feign.FeignException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PaymentAuthorisationSpiImplTest {

    private final static String PAYMENT_PRODUCT = "sepa-credit-transfers";
    private static final SpiPsuData PSU_ID_DATA_1 = SpiPsuData.builder()
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
    private static final SpiPsuData PSU_ID_DATA_2  = SpiPsuData.builder()
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
    private static final SpiContextData SPI_CONTEXT_DATA = new SpiContextData(PSU_ID_DATA_1, new TppInfo(), UUID.randomUUID(), UUID.randomUUID(), ACCESS_TOKEN);
    private static final String AUTHORISATION_ID = "6f3c444d-c664-4cfc-aff3-576651000726";
    private static final String AUTHENTICATION_METHOD_ID = "VJJwaiPJT2EptJO0jqL37E";
    private static final byte[] CONSENT_DATA_BYTES = "consent_data".getBytes();
    private static final String PAYMENT_ID = "c966f143-f6a2-41db-9036-8abaeeef3af7";
    private static final String SECRET = "12345";
    private static final SpiPsuAuthorisationResponse SPI_PSU_AUTHORISATION_FAILURE_RESPONSE = new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.FAILURE);

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

    @Before
    public void setUp() {
        businessObject = new SpiSinglePayment(PAYMENT_PRODUCT);
        businessObject.setPaymentId(PAYMENT_ID);
        businessObject.setPsuDataList(Collections.singletonList(PSU_ID_DATA_1));
    }

    @Test
    public void authorisePsu_success() throws IOException {
        businessObject.setPaymentProduct(null);
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false)).thenReturn(scaPaymentResponseTO);

        when(authorisationService.authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPsuAuthorisationResponse>builder()
                                    .payload(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS))
                                    .build());
        SCALoginResponseTO scaLoginResponseTO = new SCALoginResponseTO();
        scaLoginResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);
        when(tokenStorageService.fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class))
                .thenReturn(scaLoginResponseTO);
        doNothing().when(cmsPaymentStatusUpdateService).updatePaymentStatus(PAYMENT_ID, spiAspspConsentDataProvider);

        when(paymentInternalGeneral.initiatePaymentInternal(businessObject, CONSENT_DATA_BYTES)).thenReturn(scaPaymentResponseTO);
        byte[] responseBytes = "response_byte".getBytes();
        when(consentDataService.store(scaPaymentResponseTO)).thenReturn(responseBytes);
        doNothing().when(spiAspspConsentDataProvider).updateAspspConsentData(responseBytes);

        SpiResponse<SpiPsuAuthorisationResponse> actual = authorisationSpi.authorisePsu(SPI_CONTEXT_DATA, PSU_ID_DATA_1, SECRET,
                                                                                        businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());
        assertEquals(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS), actual.getPayload());

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
    public void authorisePsu_multilevel_success() throws IOException {
        businessObject.setPaymentProduct(null);
        businessObject.setPsuDataList(Arrays.asList(PSU_ID_DATA_1, PSU_ID_DATA_2));
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false)).thenReturn(scaPaymentResponseTO);

        when(authorisationService.authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPsuAuthorisationResponse>builder()
                                    .payload(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS))
                                    .build());
        SCALoginResponseTO scaLoginResponseTO = new SCALoginResponseTO();
        scaLoginResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);
        when(tokenStorageService.fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class)).thenReturn(scaLoginResponseTO);

        when(paymentInternalGeneral.initiatePaymentInternal(businessObject, CONSENT_DATA_BYTES)).thenReturn(scaPaymentResponseTO);

        SpiResponse<SpiPsuAuthorisationResponse> actual = authorisationSpi.authorisePsu(SPI_CONTEXT_DATA, PSU_ID_DATA_1, SECRET,
                                                                                        businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());
        assertEquals(SpiAuthorisationStatus.SUCCESS, actual.getPayload().getSpiAuthorisationStatus());

        verify(spiAspspConsentDataProvider, times(3)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false);
        verify(authorisationService, times(1)).authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider);
        verify(tokenStorageService, times(1)).fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class);
    }

    @Test
    public void authorisePsu_FeignException() throws IOException {
        businessObject.setPaymentProduct(null);
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false)).thenReturn(scaPaymentResponseTO);

        when(authorisationService.authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPsuAuthorisationResponse>builder()
                                    .payload(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS))
                                    .build());
        SCALoginResponseTO scaLoginResponseTO = new SCALoginResponseTO();
        scaLoginResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);
        when(tokenStorageService.fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class))
                .thenReturn(scaLoginResponseTO);

        when(paymentInternalGeneral.initiatePaymentInternal(businessObject, CONSENT_DATA_BYTES)).thenThrow(buildFeignException());

        SpiResponse<SpiPsuAuthorisationResponse> actual = authorisationSpi.authorisePsu(SPI_CONTEXT_DATA, PSU_ID_DATA_1, SECRET,
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
    public void authorisePsu_onSuccessfulAuthorisation_paymentInternalError() throws IOException {
        businessObject.setPaymentProduct(null);
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false)).thenReturn(scaPaymentResponseTO);

        when(authorisationService.authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPsuAuthorisationResponse>builder()
                                    .payload(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS))
                                    .build());
        SCALoginResponseTO scaLoginResponseTO = new SCALoginResponseTO();
        scaLoginResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);
        when(tokenStorageService.fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class))
                .thenReturn(scaLoginResponseTO);

        when(paymentInternalGeneral.initiatePaymentInternal(businessObject, CONSENT_DATA_BYTES)).thenReturn(null);

        SpiResponse<SpiPsuAuthorisationResponse> actual = authorisationSpi.authorisePsu(SPI_CONTEXT_DATA, PSU_ID_DATA_1, SECRET,
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
    public void authorisePsu_formatError() throws IOException {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false)).thenReturn(scaPaymentResponseTO);

        when(authorisationService.authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPsuAuthorisationResponse>builder()
                                    .payload(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS))
                                    .build());
        when(tokenStorageService.fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class))
                .thenThrow(new IOException());

        SpiResponse<SpiPsuAuthorisationResponse> actual = authorisationSpi.authorisePsu(SPI_CONTEXT_DATA, PSU_ID_DATA_1, SECRET,
                                                                                        businessObject, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.FORMAT_ERROR_RESPONSE_TYPE, actual.getErrors().get(0).getErrorCode());

        verify(spiAspspConsentDataProvider, times(2)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false);
        verify(authorisationService, times(1)).authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider);
        verify(tokenStorageService, times(1)).fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class);
    }

    @Test
    public void authorisePsu_failureResponse() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false)).thenReturn(scaPaymentResponseTO);

        when(authorisationService.authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPsuAuthorisationResponse>builder()
                                    .build());

        SpiResponse<SpiPsuAuthorisationResponse> actual = authorisationSpi.authorisePsu(SPI_CONTEXT_DATA, PSU_ID_DATA_1, SECRET,
                                                                                        businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());
        assertEquals(actual.getPayload(), SPI_PSU_AUTHORISATION_FAILURE_RESPONSE);

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false);
        verify(authorisationService, times(1)).authorisePsuForConsent(PSU_ID_DATA_1, SECRET, PAYMENT_ID, OpTypeTO.PAYMENT, spiAspspConsentDataProvider);
    }

    @Test
    public void authorisePsu_feignException() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        FeignException feignException = FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message");
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false))
                .thenThrow(feignException);
        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn("message");

        SpiResponse<SpiPsuAuthorisationResponse> actual = authorisationSpi.authorisePsu(SPI_CONTEXT_DATA, PSU_ID_DATA_1, SECRET,
                                                                                        businessObject, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, false);
        verify(feignExceptionReader, times(1)).getErrorMessage(any(FeignException.class));
    }

    @Test
    public void requestAvailableScaMethods_success() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, true)).thenReturn(scaPaymentResponseTO);

        when(authorisationService.validateToken(ACCESS_TOKEN)).thenReturn(scaPaymentResponseTO.getBearerToken());
        byte[] responseBytes = "response_byte".getBytes();
        when(consentDataService.store(scaPaymentResponseTO)).thenReturn(responseBytes);
        doNothing().when(spiAspspConsentDataProvider).updateAspspConsentData(responseBytes);

        SpiResponse<SpiAvailableScaMethodsResponse> actual = authorisationSpi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                                         businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, true);
        verify(authorisationService, times(1)).validateToken(ACCESS_TOKEN);
        verify(consentDataService, times(1)).store(scaPaymentResponseTO);
        verify(spiAspspConsentDataProvider, times(1)).updateAspspConsentData(responseBytes);
    }

    @Test
    public void requestAvailableScaMethods_noScaMethodsInResponseTO() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(null);
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, true)).thenReturn(scaPaymentResponseTO);

        when(authorisationService.validateToken(ACCESS_TOKEN)).thenReturn(scaPaymentResponseTO.getBearerToken());
        byte[] responseBytes = "response_byte".getBytes();
        when(consentDataService.store(scaPaymentResponseTO)).thenReturn(responseBytes);
        doNothing().when(spiAspspConsentDataProvider).updateAspspConsentData(responseBytes);

        SpiResponse<SpiAvailableScaMethodsResponse> actual = authorisationSpi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                                         businessObject, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, true);
        verify(authorisationService, times(1)).validateToken(ACCESS_TOKEN);
        verify(consentDataService, times(1)).store(scaPaymentResponseTO);
        verify(spiAspspConsentDataProvider, times(1)).updateAspspConsentData(responseBytes);
    }

    @Test
    public void requestAvailableScaMethods_feignException() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAPaymentResponseTO scaPaymentResponseTO = getScaPaymentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaPaymentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAPaymentResponseTO.class, true))
                .thenReturn(scaPaymentResponseTO);

        when(authorisationService.validateToken(ACCESS_TOKEN))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message"));

        SpiResponse<SpiAvailableScaMethodsResponse> actual = authorisationSpi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
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
    public void startScaDecoupled_success() {
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
    public void startScaDecoupled_errorOnReturningScaMethodSelection() {
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
    public void startScaDecoupled_scaSelected() {
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
    public void requestAvailableScaMethods_authenticationMethodIdIsNull() {
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