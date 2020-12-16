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

import de.adorsys.aspsp.xs2a.connector.spi.converter.ChallengeDataMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.aspsp.xs2a.connector.spi.impl.LedgersErrorCode;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.OpTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.middleware.api.domain.sca.StartScaOprTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.middleware.api.domain.um.ScaMethodTypeTO;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.RedirectScaRestClient;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiPsuAuthorisationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeneralAuthorisationServiceTest {

    private static final String AUTHENTICATION_METHOD_ID = "authentication method id";
    private static final byte[] CONSENT_DATA_BYTES = "consent_data".getBytes();
    private static final String CONSENT_ID = "c966f143-f6a2-41db-9036-8abaeeef3af7";
    private static final String AUTHORISATION_ID = "authorisation id";
    private static final String ACCESS_TOKEN = "access_token";

    @InjectMocks
    private GeneralAuthorisationService generalAuthorisationService;

    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;
    @Mock
    private AspspConsentDataService consentDataService;
    @Mock
    private AuthRequestInterceptor authRequestInterceptor;
    @Mock
    private RedirectScaRestClient redirectScaRestClient;
    @Mock
    private FeignExceptionReader feignExceptionReader;

    @Spy
    private ChallengeDataMapper challengeDataMapper = Mappers.getMapper(ChallengeDataMapper.class);
    @Spy
    private ScaMethodConverter scaMethodConverter = Mappers.getMapper(ScaMethodConverter.class);

    @Test
    void authorisePsuInternal_success() {
        ArgumentCaptor<StartScaOprTO> scaOprTOCaptor = ArgumentCaptor.forClass(StartScaOprTO.class);

        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setAuthorisationId(AUTHORISATION_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        globalScaResponseTO.setBearerToken(bearerToken);

        when(consentDataService.store(globalScaResponseTO)).thenReturn(CONSENT_DATA_BYTES);
        doNothing().when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);

        when(redirectScaRestClient.startSca(scaOprTOCaptor.capture())).thenReturn(ResponseEntity.ok(globalScaResponseTO));

        when(consentDataService.store(globalScaResponseTO)).thenReturn(CONSENT_DATA_BYTES);

        SpiResponse<SpiPsuAuthorisationResponse> actual = generalAuthorisationService.authorisePsuInternal(CONSENT_ID, AUTHORISATION_ID, OpTypeTO.CONSENT, globalScaResponseTO, spiAspspConsentDataProvider);

        assertTrue(actual.isSuccessful());
        assertEquals(SpiAuthorisationStatus.SUCCESS, actual.getPayload().getSpiAuthorisationStatus());

        verify(spiAspspConsentDataProvider, times(2)).updateAspspConsentData(CONSENT_DATA_BYTES);
    }

    @Test
    void authorisePsuInternal_failure() {
        ArgumentCaptor<StartScaOprTO> scaOprTOCaptor = ArgumentCaptor.forClass(StartScaOprTO.class);

        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setAuthorisationId(AUTHORISATION_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(null);
        globalScaResponseTO.setBearerToken(bearerToken);

        when(consentDataService.store(globalScaResponseTO)).thenReturn(CONSENT_DATA_BYTES);
        doNothing().when(authRequestInterceptor).setAccessToken(null);

        when(redirectScaRestClient.startSca(scaOprTOCaptor.capture())).thenReturn(ResponseEntity.ok(globalScaResponseTO));

        when(consentDataService.store(globalScaResponseTO)).thenReturn(CONSENT_DATA_BYTES);

        SpiResponse<SpiPsuAuthorisationResponse> actual = generalAuthorisationService.authorisePsuInternal(CONSENT_ID, AUTHORISATION_ID, OpTypeTO.CONSENT, globalScaResponseTO, spiAspspConsentDataProvider);

        assertTrue(actual.isSuccessful());
        assertEquals(SpiAuthorisationStatus.FAILURE, actual.getPayload().getSpiAuthorisationStatus());

        verify(spiAspspConsentDataProvider, times(2)).updateAspspConsentData(CONSENT_DATA_BYTES);
    }

    @Test
    void authorisePsuInternal_feignException() {
        ArgumentCaptor<StartScaOprTO> scaOprTOCaptor = ArgumentCaptor.forClass(StartScaOprTO.class);

        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setAuthorisationId(AUTHORISATION_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        globalScaResponseTO.setBearerToken(bearerToken);

        when(consentDataService.store(globalScaResponseTO)).thenReturn(CONSENT_DATA_BYTES);
        doNothing().when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);

        FeignException feignException = getFeignException();
        when(redirectScaRestClient.startSca(scaOprTOCaptor.capture())).thenThrow(feignException);
        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn("error message");
        when(feignExceptionReader.getLedgersErrorCode(feignException)).thenReturn(LedgersErrorCode.REQUEST_VALIDATION_FAILURE);

        SpiResponse<SpiPsuAuthorisationResponse> actual = generalAuthorisationService.authorisePsuInternal(CONSENT_ID, AUTHORISATION_ID, OpTypeTO.CONSENT, globalScaResponseTO, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertNull(actual.getPayload());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());

        verify(spiAspspConsentDataProvider, times(1)).updateAspspConsentData(CONSENT_DATA_BYTES);
    }

    @Test
    void authorisePsuInternal_attemptFailedFeignException() {
        ArgumentCaptor<StartScaOprTO> scaOprTOCaptor = ArgumentCaptor.forClass(StartScaOprTO.class);

        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setAuthorisationId(AUTHORISATION_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        globalScaResponseTO.setBearerToken(bearerToken);

        when(consentDataService.store(globalScaResponseTO)).thenReturn(CONSENT_DATA_BYTES);
        doNothing().when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);

        FeignException feignException = getFeignException();
        when(redirectScaRestClient.startSca(scaOprTOCaptor.capture())).thenThrow(feignException);
        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn("error message");
        when(feignExceptionReader.getLedgersErrorCode(feignException)).thenReturn(LedgersErrorCode.PSU_AUTH_ATTEMPT_INVALID);

        SpiResponse<SpiPsuAuthorisationResponse> actual = generalAuthorisationService.authorisePsuInternal(CONSENT_ID, AUTHORISATION_ID, OpTypeTO.CONSENT, globalScaResponseTO, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertNotNull(actual.getPayload());
        assertEquals(SpiAuthorisationStatus.ATTEMPT_FAILURE, actual.getPayload().getSpiAuthorisationStatus());
        assertFalse(actual.getPayload().isScaExempted());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());

        verify(spiAspspConsentDataProvider, times(1)).updateAspspConsentData(CONSENT_DATA_BYTES);
    }

    @Test
    void authorisePsuInternal_startScaResponseNull() {
        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setAuthorisationId(AUTHORISATION_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        globalScaResponseTO.setBearerToken(bearerToken);

        when(consentDataService.store(globalScaResponseTO)).thenReturn(CONSENT_DATA_BYTES);
        doNothing().when(spiAspspConsentDataProvider).updateAspspConsentData(CONSENT_DATA_BYTES);
        doNothing().when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);

        when(redirectScaRestClient.startSca(any(StartScaOprTO.class))).thenReturn(null);

        SpiResponse<SpiPsuAuthorisationResponse> actual = generalAuthorisationService.authorisePsuInternal(CONSENT_ID, AUTHORISATION_ID, OpTypeTO.CONSENT, globalScaResponseTO, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.FORMAT_ERROR, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void getResponseIfScaSelected_scaMethodSelected() {
        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setScaStatus(ScaStatusTO.SCAMETHODSELECTED);
        globalScaResponseTO.setScaMethods(Arrays.asList(
                getScaUserDataTO(AUTHENTICATION_METHOD_ID),
                getScaUserDataTO("another method id")
        ));

        when(consentDataService.store(globalScaResponseTO)).thenReturn(CONSENT_DATA_BYTES);

        SpiResponse<SpiAuthorizationCodeResult> actual = generalAuthorisationService.getResponseIfScaSelected(spiAspspConsentDataProvider, globalScaResponseTO, AUTHENTICATION_METHOD_ID);

        verify(spiAspspConsentDataProvider, times(1)).updateAspspConsentData(CONSENT_DATA_BYTES);

        assertTrue(actual.isSuccessful());
        assertEquals(AUTHENTICATION_METHOD_ID, actual.getPayload().getSelectedScaMethod().getAuthenticationMethodId());
        assertNotNull(actual.getPayload().getChallengeData());
    }

    @Test
    void getResponseIfScaSelected_notScaMethodSelected() {
        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        globalScaResponseTO.setScaStatus(ScaStatusTO.EXEMPTED);

        SpiResponse<SpiAuthorizationCodeResult> actual = generalAuthorisationService.getResponseIfScaSelected(spiAspspConsentDataProvider, globalScaResponseTO, AUTHENTICATION_METHOD_ID);

        verify(consentDataService, never()).store(any(GlobalScaResponseTO.class));
        verify(spiAspspConsentDataProvider, never()).updateAspspConsentData(any());

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.SCA_INVALID, actual.getErrors().get(0).getErrorCode());
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