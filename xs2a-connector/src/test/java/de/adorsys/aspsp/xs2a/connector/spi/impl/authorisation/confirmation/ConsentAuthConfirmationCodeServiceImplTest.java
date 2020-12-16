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

package de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation.confirmation;

import de.adorsys.aspsp.xs2a.connector.oauth.OauthProfileServiceWrapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.ledgers.middleware.api.domain.sca.AuthConfirmationTO;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.UserMgmtRestClient;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.profile.ScaRedirectFlow;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiCheckConfirmationCodeRequest;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiConsentConfirmationCodeValidationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.nio.charset.Charset;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsentAuthConfirmationCodeServiceImplTest {

    private static final String AUTHORISATION_ID = "authorisation id";
    private static final String CONFIRMATION_CODE = "12345";
    private static final String SCA_AUTHENTICATION_DATA = "12345";
    private static final String ACCESS_TOKEN = "access_token";
    private static final byte[] CONSENT_DATA_BYTES = "consent_data".getBytes();

    @InjectMocks
    private ConsentAuthConfirmationCodeServiceImpl authConfirmationCodeService;

    @Mock
    private AuthRequestInterceptor authRequestInterceptor;
    @Mock
    private AspspConsentDataService consentDataService;
    @Mock
    private FeignExceptionReader feignExceptionReader;
    @Mock
    private UserMgmtRestClient userMgmtRestClient;
    @Mock
    private OauthProfileServiceWrapper oauthProfileServiceWrapper;
    @Mock
    private SpiAspspConsentDataProvider aspspConsentDataProvider;

    @Test
    void handleAuthConfirmationResponse_responseBodyIsFalse() {
        AuthConfirmationTO authConfirmation = new AuthConfirmationTO();
        authConfirmation.setSuccess(false);

        SpiResponse<SpiConsentConfirmationCodeValidationResponse> actual =
                authConfirmationCodeService.handleAuthConfirmationResponse(ResponseEntity.ok(authConfirmation));

        assertEquals(ScaStatus.FAILED, actual.getPayload().getScaStatus());
        assertEquals(ConsentStatus.REJECTED, actual.getPayload().getConsentStatus());
    }

    @Test
    void handleAuthConfirmationResponse_partiallyAuthorised() {
        AuthConfirmationTO authConfirmation = new AuthConfirmationTO();
        authConfirmation.setSuccess(true);
        authConfirmation.setPartiallyAuthorised(true);

        SpiResponse<SpiConsentConfirmationCodeValidationResponse> actual =
                authConfirmationCodeService.handleAuthConfirmationResponse(ResponseEntity.ok(authConfirmation));

        assertEquals(ScaStatus.FINALISED, actual.getPayload().getScaStatus());
        assertEquals(ConsentStatus.PARTIALLY_AUTHORISED, actual.getPayload().getConsentStatus());
    }

    @Test
    void handleAuthConfirmationResponse_success() {
        AuthConfirmationTO authConfirmation = new AuthConfirmationTO();
        authConfirmation.setSuccess(true);
        authConfirmation.setPartiallyAuthorised(false);

        SpiResponse<SpiConsentConfirmationCodeValidationResponse> actual =
                authConfirmationCodeService.handleAuthConfirmationResponse(ResponseEntity.ok(authConfirmation));

        assertEquals(ScaStatus.FINALISED, actual.getPayload().getScaStatus());
        assertEquals(ConsentStatus.VALID, actual.getPayload().getConsentStatus());
    }

    @Test
    void checkConfirmationCodeInternally_oauth() {
        when(aspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaResponseTO = new GlobalScaResponseTO();
        scaResponseTO.setAuthConfirmationCode(CONFIRMATION_CODE);
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(scaResponseTO);

        when(oauthProfileServiceWrapper.getScaRedirectFlow()).thenReturn(ScaRedirectFlow.OAUTH);

        boolean actual = authConfirmationCodeService.checkConfirmationCodeInternally(AUTHORISATION_ID, "wrong_code", SCA_AUTHENTICATION_DATA, aspspConsentDataProvider);

        assertTrue(actual);
    }

    @Test
    void checkConfirmationCodeInternally_redirect() {
        when(aspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaResponseTO = new GlobalScaResponseTO();
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(scaResponseTO);

        when(oauthProfileServiceWrapper.getScaRedirectFlow()).thenReturn(ScaRedirectFlow.REDIRECT);

        boolean actual = authConfirmationCodeService.checkConfirmationCodeInternally(AUTHORISATION_ID, CONFIRMATION_CODE, SCA_AUTHENTICATION_DATA, aspspConsentDataProvider);

        assertTrue(actual);
    }

    @Test
    void completeAuthConfirmation_success() {
        GlobalScaResponseTO scaResponseTO = new GlobalScaResponseTO();
        scaResponseTO.setAuthorisationId(AUTHORISATION_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        scaResponseTO.setBearerToken(bearerToken);

        when(aspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(scaResponseTO);
        when(userMgmtRestClient.completeAuthConfirmation(AUTHORISATION_ID, true))
                .thenReturn(ResponseEntity.ok(new AuthConfirmationTO()));

        SpiResponse<SpiConsentConfirmationCodeValidationResponse> actual =
                authConfirmationCodeService.completeAuthConfirmation(true, aspspConsentDataProvider);

        assertTrue(actual.isSuccessful());
        assertNotNull(actual.getPayload());

        verify(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
    }

    @Test
    void completeAuthConfirmation_error() {
        GlobalScaResponseTO scaResponseTO = new GlobalScaResponseTO();
        scaResponseTO.setAuthorisationId(AUTHORISATION_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        scaResponseTO.setBearerToken(bearerToken);

        when(aspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(scaResponseTO);
        when(userMgmtRestClient.completeAuthConfirmation(AUTHORISATION_ID, true))
                .thenThrow(getFeignException());

        SpiResponse<SpiConsentConfirmationCodeValidationResponse> actual =
                authConfirmationCodeService.completeAuthConfirmation(true, aspspConsentDataProvider);

        assertFalse(actual.isSuccessful());
        assertNull(actual.getPayload());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());

        verify(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
    }

    @Test
    void checkConfirmationCode_success() {
        SpiCheckConfirmationCodeRequest request = new SpiCheckConfirmationCodeRequest(CONFIRMATION_CODE, AUTHORISATION_ID);
        GlobalScaResponseTO scaResponseTO = new GlobalScaResponseTO();
        scaResponseTO.setAuthorisationId(AUTHORISATION_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        scaResponseTO.setBearerToken(bearerToken);

        when(aspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(scaResponseTO);
        when(userMgmtRestClient.verifyAuthConfirmationCode(AUTHORISATION_ID, CONFIRMATION_CODE))
                .thenReturn(ResponseEntity.ok(new AuthConfirmationTO()));

        SpiResponse<SpiConsentConfirmationCodeValidationResponse> actual =
                authConfirmationCodeService.checkConfirmationCode(request, aspspConsentDataProvider);

        assertTrue(actual.isSuccessful());
        assertNotNull(actual.getPayload());

        verify(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
    }

    @Test
    void checkConfirmationCode_error() {
        SpiCheckConfirmationCodeRequest request = new SpiCheckConfirmationCodeRequest(CONFIRMATION_CODE, AUTHORISATION_ID);
        GlobalScaResponseTO scaResponseTO = new GlobalScaResponseTO();
        scaResponseTO.setAuthorisationId(AUTHORISATION_ID);
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        scaResponseTO.setBearerToken(bearerToken);

        when(aspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(scaResponseTO);
        when(userMgmtRestClient.verifyAuthConfirmationCode(AUTHORISATION_ID, CONFIRMATION_CODE))
                .thenThrow(getFeignException());

        SpiResponse<SpiConsentConfirmationCodeValidationResponse> actual =
                authConfirmationCodeService.checkConfirmationCode(request, aspspConsentDataProvider);

        assertFalse(actual.isSuccessful());
        assertNull(actual.getPayload());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());

        verify(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
    }

    private FeignException getFeignException() {
        return FeignException.errorStatus("message",
                                          buildErrorResponse());
    }

    private Response buildErrorResponse() {
        return Response.builder()
                       .status(404)
                       .request(Request.create(Request.HttpMethod.GET, "", Collections.emptyMap(), null, Charset.defaultCharset(), null))
                       .headers(Collections.emptyMap())
                       .build();
    }
}