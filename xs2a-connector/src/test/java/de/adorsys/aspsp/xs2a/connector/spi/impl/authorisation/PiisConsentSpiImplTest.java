/*
 * Copyright 2018-2020 adorsys GmbH & Co KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation;

import de.adorsys.aspsp.xs2a.connector.spi.converter.AisConsentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaResponseMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.aspsp.xs2a.connector.spi.impl.LedgersErrorCode;
import de.adorsys.aspsp.xs2a.connector.spi.impl.MultilevelScaService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation.confirmation.ConsentAuthConfirmationCodeService;
import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.aspsp.xs2a.util.TestSpiDataProvider;
import de.adorsys.ledgers.middleware.api.domain.sca.*;
import de.adorsys.ledgers.middleware.api.domain.um.AisConsentTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.middleware.api.domain.um.ScaMethodTypeTO;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.ConsentRestClient;
import de.adorsys.ledgers.rest.client.RedirectScaRestClient;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAvailableScaMethodsResponse;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiCheckConfirmationCodeRequest;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiConsentConfirmationCodeValidationResponse;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiConsentStatusResponse;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiInitiatePiisConsentResponse;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiVerifyScaAuthorisationResponse;
import de.adorsys.psd2.xs2a.spi.domain.piis.SpiPiisConsent;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PiisConsentSpiImplTest {

    private static final SpiContextData SPI_CONTEXT_DATA = TestSpiDataProvider.getSpiContextData();
    private static final String AUTHORISATION_ID = "authorisation id";
    private static final String AUTHENTICATION_METHOD_ID = "authentication method id";
    private static final byte[] CONSENT_DATA_BYTES = "consent_data".getBytes();
    private static final String CONFIRMATION_CODE = "12345";
    private static final String SCA_AUTHENTICATION_DATA = "54321";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String TAN_NUMBER = "123456";

    private final JsonReader jsonReader = new JsonReader();
    private SpiPiisConsent spiPiisConsent;

    @InjectMocks
    private PiisConsentSpiImpl piisConsentSpi;

    @Mock
    private MultilevelScaService multilevelScaService;
    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;
    @Mock
    private RedirectScaRestClient redirectScaRestClient;
    @Mock
    private AspspConsentDataService consentDataService;
    @Mock
    private ConsentAuthConfirmationCodeService authConfirmationCodeService;
    @Mock
    private ConsentRestClient consentRestClient;
    @Mock
    private AuthRequestInterceptor authRequestInterceptor;
    @Mock
    private FeignExceptionReader feignExceptionReader;

    @Spy
    private AisConsentMapper aisConsentMapper = Mappers.getMapper(AisConsentMapper.class);
    @Spy
    private ScaResponseMapper scaResponseMapper = Mappers.getMapper(ScaResponseMapper.class);
    @Spy
    private ScaMethodConverter scaMethodConverter = Mappers.getMapper(ScaMethodConverter.class);


    @Captor
    private ArgumentCaptor<StartScaOprTO> startScaOprTOCaptor;

    @BeforeEach
    void setUp() {
        spiPiisConsent = jsonReader.getObjectFromFile("json/spi/impl/spi-piis-consent.json", SpiPiisConsent.class);
    }

    @Test
    void getSelectMethodResponse_success() {
        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setAuthorisationId(AUTHORISATION_ID);

        when(redirectScaRestClient.selectMethod(AUTHORISATION_ID, AUTHENTICATION_METHOD_ID)).thenReturn(ResponseEntity.ok(globalScaResponseTO));

        ResponseEntity<GlobalScaResponseTO> actual = piisConsentSpi.getSelectMethodResponse(AUTHENTICATION_METHOD_ID, globalScaResponseTO);
        assertEquals(HttpStatus.OK, actual.getStatusCode());
        assertEquals(globalScaResponseTO, actual.getBody());
    }

    @Test
    void getSelectMethodResponse_badRequest() {
        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setAuthorisationId(AUTHORISATION_ID);

        when(redirectScaRestClient.selectMethod(AUTHORISATION_ID, AUTHENTICATION_METHOD_ID)).thenReturn(ResponseEntity.notFound().build());

        ResponseEntity<GlobalScaResponseTO> actual = piisConsentSpi.getSelectMethodResponse(AUTHENTICATION_METHOD_ID, globalScaResponseTO);
        assertEquals(HttpStatus.BAD_REQUEST, actual.getStatusCode());
        assertNull(actual.getBody());
    }

    @Test
    void getScaObjectResponse() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        piisConsentSpi.getScaObjectResponse(spiAspspConsentDataProvider, true);

        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, true);
    }

    @Test
    void getBusinessObjectId() {
        spiPiisConsent.setId(UUID.randomUUID().toString());

        assertEquals(spiPiisConsent.getId(), piisConsentSpi.getBusinessObjectId(spiPiisConsent));
    }

    @Test
    void getAuthorisePsuFailureMessage() {
        TppMessage actual = piisConsentSpi.getAuthorisePsuFailureMessage(spiPiisConsent);
        assertEquals(MessageErrorCode.FORMAT_ERROR_UNKNOWN_ACCOUNT, actual.getErrorCode());
    }

    @Test
    void isFirstInitiationOfMultilevelSca() {
        SpiPiisConsent businessObject = new SpiPiisConsent();
        GlobalScaResponseTO scaConsentResponseTO = new GlobalScaResponseTO();
        SpiPsuData spiPsuData = SpiPsuData.builder().psuId("psuId").build();

        assertTrue(piisConsentSpi.isFirstInitiationOfMultilevelSca(businessObject, scaConsentResponseTO));

        businessObject.setPsuData(Collections.singletonList(spiPsuData));
        assertTrue(piisConsentSpi.isFirstInitiationOfMultilevelSca(businessObject, scaConsentResponseTO));

        businessObject.setPsuData(Arrays.asList(spiPsuData, spiPsuData));
        assertTrue(piisConsentSpi.isFirstInitiationOfMultilevelSca(businessObject, scaConsentResponseTO));

        businessObject.setPsuData(Arrays.asList(spiPsuData, spiPsuData));
        scaConsentResponseTO.setMultilevelScaRequired(true);
        assertFalse(piisConsentSpi.isFirstInitiationOfMultilevelSca(businessObject, scaConsentResponseTO));
    }

    @Test
    void initiatePiisConsent_success() {
        ArgumentCaptor<GlobalScaResponseTO> globalScaResponseTOCaptor = ArgumentCaptor.forClass(GlobalScaResponseTO.class);
        //Given
        when(multilevelScaService.isMultilevelScaRequired(SPI_CONTEXT_DATA.getPsuData(), Collections.singleton(spiPiisConsent.getAccount())))
                .thenReturn(Boolean.TRUE);

        when(consentDataService.store(globalScaResponseTOCaptor.capture(), eq(false))).thenReturn(CONSENT_DATA_BYTES);
        doNothing().when(spiAspspConsentDataProvider).updateAspspConsentData(CONSENT_DATA_BYTES);

        //When
        SpiResponse<SpiInitiatePiisConsentResponse> actual = piisConsentSpi.initiatePiisConsent(SPI_CONTEXT_DATA, spiPiisConsent, spiAspspConsentDataProvider);
        //Then
        assertTrue(actual.isSuccessful());
        assertTrue(actual.getPayload().isMultilevelScaRequired());
        assertEquals(OpTypeTO.CONSENT, globalScaResponseTOCaptor.getValue().getOpType());
        assertEquals(ScaStatusTO.STARTED, globalScaResponseTOCaptor.getValue().getScaStatus());
        assertTrue(globalScaResponseTOCaptor.getValue().isMultilevelScaRequired());
    }

    @Test
    void initiatePiisConsent_insufficientPermissionException() {
        //Given
        when(multilevelScaService.isMultilevelScaRequired(SPI_CONTEXT_DATA.getPsuData(), Collections.singleton(spiPiisConsent.getAccount()))).thenThrow(getFeignException());
        //When
        SpiResponse<SpiInitiatePiisConsentResponse> spiInitiatePiisConsentResponseSpiResponse = piisConsentSpi.initiatePiisConsent(SPI_CONTEXT_DATA, spiPiisConsent, spiAspspConsentDataProvider);
        //Then
        assertFalse(spiInitiatePiisConsentResponseSpiResponse.getErrors().isEmpty());
        assertNull(spiInitiatePiisConsentResponseSpiResponse.getPayload());
        assertTrue(spiInitiatePiisConsentResponseSpiResponse.getErrors().contains(new TppMessage(MessageErrorCode.FORMAT_ERROR_UNKNOWN_ACCOUNT)));
    }

    @Test
    void getOpType() {
        assertEquals(OpTypeTO.CONSENT, piisConsentSpi.getOpType());
    }

    @Test
    void executeBusinessObject() {
        assertNull(piisConsentSpi.executeBusinessObject(spiPiisConsent));
    }

    @Test
    void requestTrustedBeneficiaryFlag() {
        SpiResponse<Boolean> actual = piisConsentSpi.requestTrustedBeneficiaryFlag(SPI_CONTEXT_DATA, spiPiisConsent, AUTHORISATION_ID, spiAspspConsentDataProvider);
        assertTrue(actual.isSuccessful());
        assertTrue(actual.getPayload());
    }

    @Test
    void revokePiisConsent() {
        SpiResponse<SpiResponse.VoidResponse> actual = piisConsentSpi.revokePiisConsent(SPI_CONTEXT_DATA, spiPiisConsent, spiAspspConsentDataProvider);
        assertTrue(actual.isSuccessful());
        assertEquals(SpiResponse.voidResponse(), actual.getPayload());
    }

    @Test
    void getConsentStatus() {
        spiPiisConsent.setConsentStatus(ConsentStatus.VALID);

        SpiResponse<SpiConsentStatusResponse> actual = piisConsentSpi.getConsentStatus(SPI_CONTEXT_DATA, spiPiisConsent, spiAspspConsentDataProvider);
        assertTrue(actual.isSuccessful());
        assertEquals(ConsentStatus.VALID, actual.getPayload().getConsentStatus());
        assertNull(actual.getPayload().getPsuMessage());
    }

    @Test
    void checkConfirmationCode() {
        SpiCheckConfirmationCodeRequest request = new SpiCheckConfirmationCodeRequest(CONFIRMATION_CODE, AUTHORISATION_ID);

        when(authConfirmationCodeService.checkConfirmationCode(request, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiConsentConfirmationCodeValidationResponse>builder().build());

        piisConsentSpi.checkConfirmationCode(SPI_CONTEXT_DATA, request, spiAspspConsentDataProvider);
        verify(authConfirmationCodeService, times(1)).checkConfirmationCode(request, spiAspspConsentDataProvider);
    }

    @Test
    void notifyConfirmationCodeValidation() {
        when(authConfirmationCodeService.completeAuthConfirmation(true, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiConsentConfirmationCodeValidationResponse>builder().build());

        piisConsentSpi.notifyConfirmationCodeValidation(SPI_CONTEXT_DATA, true, spiPiisConsent, spiAspspConsentDataProvider);
        verify(authConfirmationCodeService, times(1)).completeAuthConfirmation(true, spiAspspConsentDataProvider);
    }

    @Test
    void checkConfirmationCodeInternally() {
        piisConsentSpi.checkConfirmationCodeInternally(AUTHORISATION_ID, CONFIRMATION_CODE, SCA_AUTHENTICATION_DATA, spiAspspConsentDataProvider);
        verify(authConfirmationCodeService, times(1)).checkConfirmationCodeInternally(AUTHORISATION_ID, CONFIRMATION_CODE, SCA_AUTHENTICATION_DATA, spiAspspConsentDataProvider);
    }

    @Test
    void initiateBusinessObject() {
        String consentId = UUID.randomUUID().toString();
        spiPiisConsent.setId(consentId);

        SCAConsentResponseTO scaConsentResponseTO = new SCAConsentResponseTO();
        scaConsentResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        scaConsentResponseTO.setBearerToken(bearerToken);

        when(consentRestClient.initiateAisConsent(eq(consentId), any(AisConsentTO.class)))
                .thenReturn(ResponseEntity.ok(scaConsentResponseTO));
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        when(redirectScaRestClient.startSca(startScaOprTOCaptor.capture()))
                .thenReturn(ResponseEntity.ok(new GlobalScaResponseTO()));

        GlobalScaResponseTO actual = piisConsentSpi.initiateBusinessObject(spiPiisConsent, spiAspspConsentDataProvider, AUTHORISATION_ID);

        assertNotNull(actual);

        assertEquals(consentId, startScaOprTOCaptor.getValue().getOprId());
        assertEquals(AUTHORISATION_ID, startScaOprTOCaptor.getValue().getAuthorisationId());
        assertEquals(OpTypeTO.CONSENT, startScaOprTOCaptor.getValue().getOpType());

        verify(authRequestInterceptor, times(1)).setAccessToken(null);
    }

    @Test
    void initiateBusinessObject_exempted() {
        String consentId = UUID.randomUUID().toString();
        spiPiisConsent.setId(consentId);

        SCAConsentResponseTO scaConsentResponseTO = new SCAConsentResponseTO();
        scaConsentResponseTO.setScaStatus(ScaStatusTO.EXEMPTED);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        scaConsentResponseTO.setBearerToken(bearerToken);

        when(consentRestClient.initiateAisConsent(eq(consentId), any(AisConsentTO.class)))
                .thenReturn(ResponseEntity.ok(scaConsentResponseTO));
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        GlobalScaResponseTO globalScaResponseTO = piisConsentSpi.initiateBusinessObject(spiPiisConsent, spiAspspConsentDataProvider, AUTHORISATION_ID);

        assertEquals(ScaStatusTO.EXEMPTED, globalScaResponseTO.getScaStatus());
        assertEquals(AUTHORISATION_ID, globalScaResponseTO.getAuthorisationId());
    }

    @Test
    void initiateBusinessObject_initiateAisConsentNull() {
        String consentId = UUID.randomUUID().toString();
        spiPiisConsent.setId(consentId);

        when(consentRestClient.initiateAisConsent(eq(consentId), any(AisConsentTO.class)))
                .thenReturn(null);

        assertNull(piisConsentSpi.initiateBusinessObject(spiPiisConsent, spiAspspConsentDataProvider, AUTHORISATION_ID));
    }

    @Test
    void requestAvailableScaMethods_success() {
        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setAuthorisationId(AUTHORISATION_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        globalScaResponseTO.setBearerToken(bearerToken);
        globalScaResponseTO.setScaMethods(Collections.singletonList(
                getScaUserDataTO(AUTHENTICATION_METHOD_ID)
        ));

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(globalScaResponseTO);

        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        when(redirectScaRestClient.getSCA(AUTHORISATION_ID)).thenReturn(ResponseEntity.ok(globalScaResponseTO));

        SpiResponse<SpiAvailableScaMethodsResponse> actual = piisConsentSpi.requestAvailableScaMethods(SPI_CONTEXT_DATA, spiPiisConsent, spiAspspConsentDataProvider);

        assertTrue(actual.isSuccessful());
        assertFalse(actual.getPayload().isScaExempted());
        assertEquals(1, actual.getPayload().getAvailableScaMethods().size());
        assertEquals(AUTHENTICATION_METHOD_ID, actual.getPayload().getAvailableScaMethods().get(0).getAuthenticationMethodId());
    }

    @Test
    void requestAvailableScaMethods_feignException() {
        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setAuthorisationId(AUTHORISATION_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        globalScaResponseTO.setBearerToken(bearerToken);
        globalScaResponseTO.setScaMethods(Collections.singletonList(
                getScaUserDataTO(AUTHENTICATION_METHOD_ID)
        ));

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(globalScaResponseTO);

        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        FeignException feignException = getFeignException();
        when(redirectScaRestClient.getSCA(AUTHORISATION_ID)).thenThrow(feignException);
        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn("error message");

        SpiResponse<SpiAvailableScaMethodsResponse> actual = piisConsentSpi.requestAvailableScaMethods(SPI_CONTEXT_DATA, spiPiisConsent, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.FORMAT_ERROR_SCA_METHODS, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void verifyScaAuthorisation_success() {
        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        spiScaConfirmation.setTanNumber(TAN_NUMBER);

        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setAuthorisationId(AUTHORISATION_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        globalScaResponseTO.setBearerToken(bearerToken);
        globalScaResponseTO.setScaStatus(ScaStatusTO.FINALISED);
        globalScaResponseTO.setPartiallyAuthorised(true);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(globalScaResponseTO);

        when(redirectScaRestClient.validateScaCode(AUTHORISATION_ID, TAN_NUMBER)).thenReturn(ResponseEntity.ok(globalScaResponseTO));

        when(consentDataService.store(globalScaResponseTO, false)).thenReturn(CONSENT_DATA_BYTES);
        doNothing().when(spiAspspConsentDataProvider).updateAspspConsentData(CONSENT_DATA_BYTES);

        SpiResponse<SpiVerifyScaAuthorisationResponse> actual = piisConsentSpi.verifyScaAuthorisation(SPI_CONTEXT_DATA, spiScaConfirmation, spiPiisConsent, spiAspspConsentDataProvider);

        assertTrue(actual.isSuccessful());
        assertEquals(ConsentStatus.PARTIALLY_AUTHORISED, actual.getPayload().getConsentStatus());
    }

    @Test
    void verifyScaAuthorisation_feignException() {
        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        spiScaConfirmation.setTanNumber(TAN_NUMBER);

        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setAuthorisationId(AUTHORISATION_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        globalScaResponseTO.setBearerToken(bearerToken);
        globalScaResponseTO.setScaStatus(ScaStatusTO.FINALISED);
        globalScaResponseTO.setPartiallyAuthorised(true);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(globalScaResponseTO);

        FeignException feignException = getFeignException();
        when(redirectScaRestClient.validateScaCode(AUTHORISATION_ID, TAN_NUMBER)).thenThrow(feignException);
        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn("error message");
        when(feignExceptionReader.getLedgersErrorCode(feignException)).thenReturn(LedgersErrorCode.REQUEST_VALIDATION_FAILURE);

        SpiResponse<SpiVerifyScaAuthorisationResponse> actual = piisConsentSpi.verifyScaAuthorisation(SPI_CONTEXT_DATA, spiScaConfirmation, spiPiisConsent, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertNull(actual.getPayload());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void verifyScaAuthorisation_attemptFailedFeignException() {
        spiPiisConsent.setConsentStatus(ConsentStatus.RECEIVED);

        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        spiScaConfirmation.setTanNumber(TAN_NUMBER);

        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setAuthorisationId(AUTHORISATION_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        globalScaResponseTO.setBearerToken(bearerToken);
        globalScaResponseTO.setScaStatus(ScaStatusTO.FINALISED);
        globalScaResponseTO.setPartiallyAuthorised(false);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(globalScaResponseTO);

        FeignException feignException = getFeignException();
        when(redirectScaRestClient.validateScaCode(AUTHORISATION_ID, TAN_NUMBER)).thenThrow(feignException);
        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn("error message");
        when(feignExceptionReader.getLedgersErrorCode(feignException)).thenReturn(LedgersErrorCode.SCA_VALIDATION_ATTEMPT_FAILED);

        SpiResponse<SpiVerifyScaAuthorisationResponse> actual = piisConsentSpi.verifyScaAuthorisation(SPI_CONTEXT_DATA, spiScaConfirmation, spiPiisConsent, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertNotNull(actual.getPayload());
        assertEquals(SpiAuthorisationStatus.ATTEMPT_FAILURE, actual.getPayload().getSpiAuthorisationStatus());
        assertEquals(ConsentStatus.RECEIVED, actual.getPayload().getConsentStatus());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void verifyScaAuthorisation_emptyResponseOnValidationTanNumber() {
        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        spiScaConfirmation.setTanNumber(TAN_NUMBER);

        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setAuthorisationId(AUTHORISATION_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        globalScaResponseTO.setBearerToken(bearerToken);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(globalScaResponseTO);

        when(redirectScaRestClient.validateScaCode(AUTHORISATION_ID, TAN_NUMBER)).thenReturn(null);

        SpiResponse<SpiVerifyScaAuthorisationResponse> actual = piisConsentSpi.verifyScaAuthorisation(SPI_CONTEXT_DATA, spiScaConfirmation, spiPiisConsent, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.FORMAT_ERROR, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void mapToConsentStatus() {
        assertEquals(ConsentStatus.VALID, piisConsentSpi.mapToConsentStatus(null));

        GlobalScaResponseTO globalScaResponse = new GlobalScaResponseTO();
        assertEquals(ConsentStatus.VALID, piisConsentSpi.mapToConsentStatus(globalScaResponse));

        globalScaResponse.setPartiallyAuthorised(true);
        assertEquals(ConsentStatus.VALID, piisConsentSpi.mapToConsentStatus(globalScaResponse));

        globalScaResponse.setScaStatus(ScaStatusTO.FINALISED);
        assertEquals(ConsentStatus.PARTIALLY_AUTHORISED, piisConsentSpi.mapToConsentStatus(globalScaResponse));
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

    private ScaUserDataTO getScaUserDataTO(String methodId) {
        ScaUserDataTO scaUserDataTO = new ScaUserDataTO();
        scaUserDataTO.setId(methodId);
        scaUserDataTO.setScaMethod(ScaMethodTypeTO.EMAIL);
        scaUserDataTO.setMethodValue("method value");
        scaUserDataTO.setDecoupled(false);
        return scaUserDataTO;
    }

}
