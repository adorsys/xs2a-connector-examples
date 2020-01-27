/*
 * Copyright 2018-2019 adorsys GmbH & Co KG
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

package de.adorsys.aspsp.xs2a.connector.oauth;

import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.psd2.aspsp.profile.domain.AspspSettings;
import de.adorsys.psd2.aspsp.profile.service.AspspProfileService;
import de.adorsys.psd2.xs2a.core.domain.MessageCategory;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.profile.ScaApproach;
import de.adorsys.psd2.xs2a.web.Xs2aEndpointChecker;
import de.adorsys.psd2.xs2a.web.error.TppErrorMessageWriter;
import de.adorsys.psd2.xs2a.web.filter.TppErrorMessage;
import de.adorsys.psd2.xs2a.web.request.RequestPathResolver;
import de.adorsys.xs2a.reader.JsonReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenAuthenticationFilterTest {
    private static final String ASPSP_SETTINGS_JSON_PATH = "json/oauth/aspsp-settings.json";

    private static final String OAUTH_MODE_HEADER_NAME = "X-OAUTH-PREFERRED";
    private static final String OAUTH_MODE_INTEGRATED = "integrated";
    private static final String OAUTH_MODE_PRE_STEP = "pre-step";
    private static final String OAUTH_MODE_INVALID_VALUE = "invalid value";

    private static final String BEARER_TOKEN_PREFIX = "Bearer ";
    private static final String BEARER_TOKEN_VALUE = "some_token";
    private static final String BEARER_TOKEN_INVALID_VALUE = "invalid value";

    private static final String ACCOUNTS_PATH = "/v1/accounts";
    private static final String CONSENTS_PATH = "/v1/consents";
    private static final String FUNDS_CONFIRMATION_PATH = "/v1/funds-confirmations";
    private static final String PAYMENTS_PATH = "/v1/payments/sepa-credits-transfer";
    private static final String IDP_CONFIGURATION_LINK = "http://localhost:4200/idp";

    @Mock
    private TokenValidationService tokenValidationService;
    @Mock
    private HttpServletRequest httpServletRequest;
    @Mock
    private HttpServletResponse httpServletResponse;
    @Mock
    private FilterChain filterChain;
    @Mock
    private OauthDataHolder oauthDataHolder;
    @Mock
    private AspspProfileService aspspProfileService;
    @Mock
    private TppErrorMessageWriter tppErrorMessageWriter;
    @Mock
    private RequestPathResolver requestPathResolver;
    @Mock
    private Xs2aEndpointChecker xs2aEndpointChecker;

    private TokenAuthenticationFilter tokenAuthenticationFilter;

    @BeforeEach
    void setUp() {
        tokenAuthenticationFilter = new TokenAuthenticationFilter(requestPathResolver,
                                                                  OAUTH_MODE_HEADER_NAME,
                                                                  xs2aEndpointChecker,
                                                                  tokenValidationService,
                                                                  aspspProfileService,
                                                                  oauthDataHolder,
                                                                  tppErrorMessageWriter);

        when(xs2aEndpointChecker.isXs2aEndpoint(httpServletRequest)).thenReturn(true);
    }

    @Test
    void doFilter_withValidToken() throws ServletException, IOException {
        // Given
        when(aspspProfileService.getScaApproaches())
                .thenReturn(Arrays.asList(ScaApproach.REDIRECT, ScaApproach.OAUTH));

        when(aspspProfileService.getAspspSettings())
                .thenReturn(new JsonReader().getObjectFromFile(ASPSP_SETTINGS_JSON_PATH, AspspSettings.class));

        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn(OAUTH_MODE_INTEGRATED);
        when(requestPathResolver.resolveRequestPath(httpServletRequest)).thenReturn(ACCOUNTS_PATH);

        String authorisationTokenValue = BEARER_TOKEN_PREFIX + BEARER_TOKEN_VALUE;
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn(authorisationTokenValue);
        when(tokenValidationService.validate(BEARER_TOKEN_VALUE))
                .thenReturn(new BearerTokenTO());

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(tokenValidationService).validate(BEARER_TOKEN_VALUE);
        verify(oauthDataHolder).setOauthTypeAndToken(OauthType.INTEGRATED, BEARER_TOKEN_VALUE);
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);

        verify(httpServletResponse, never()).setStatus(ArgumentMatchers.anyInt());
    }

    @Test
    void doFilter_withValidToken_preStep() throws ServletException, IOException {
        // Given
        when(aspspProfileService.getScaApproaches())
                .thenReturn(Arrays.asList(ScaApproach.REDIRECT, ScaApproach.OAUTH));

        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn(OAUTH_MODE_PRE_STEP);
        when(requestPathResolver.resolveRequestPath(httpServletRequest)).thenReturn(PAYMENTS_PATH);

        String authorisationTokenValue = BEARER_TOKEN_PREFIX + BEARER_TOKEN_VALUE;
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn(authorisationTokenValue);
        when(tokenValidationService.validate(BEARER_TOKEN_VALUE))
                .thenReturn(new BearerTokenTO());

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(tokenValidationService).validate(BEARER_TOKEN_VALUE);
        verify(oauthDataHolder).setOauthTypeAndToken(OauthType.PRE_STEP, BEARER_TOKEN_VALUE);
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);

        verify(httpServletResponse, never()).setStatus(ArgumentMatchers.anyInt());
    }

    @Test
    void doFilter_withTrailingSlashInPath() throws ServletException, IOException {
        // Given
        when(aspspProfileService.getScaApproaches())
                .thenReturn(Arrays.asList(ScaApproach.REDIRECT, ScaApproach.OAUTH));

        when(aspspProfileService.getAspspSettings())
                .thenReturn(new JsonReader().getObjectFromFile(ASPSP_SETTINGS_JSON_PATH, AspspSettings.class));

        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn(OAUTH_MODE_INTEGRATED);
        when(requestPathResolver.resolveRequestPath(httpServletRequest)).thenReturn(ACCOUNTS_PATH + "/");

        String authorisationTokenValue = BEARER_TOKEN_PREFIX + BEARER_TOKEN_VALUE;
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn(authorisationTokenValue);
        when(tokenValidationService.validate(BEARER_TOKEN_VALUE))
                .thenReturn(new BearerTokenTO());

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(tokenValidationService).validate(BEARER_TOKEN_VALUE);
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);

        verify(httpServletResponse, never()).setStatus(ArgumentMatchers.anyInt());
    }

    @Test
    void doFilter_withPaymentsPath() throws ServletException, IOException {
        // Given
        when(aspspProfileService.getScaApproaches())
                .thenReturn(Arrays.asList(ScaApproach.REDIRECT, ScaApproach.OAUTH));

        when(aspspProfileService.getAspspSettings())
                .thenReturn(new JsonReader().getObjectFromFile(ASPSP_SETTINGS_JSON_PATH, AspspSettings.class));

        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME))
                .thenReturn(OAUTH_MODE_INTEGRATED);
        when(requestPathResolver.resolveRequestPath(httpServletRequest))
                .thenReturn(PAYMENTS_PATH);

        String authorisationTokenValue = BEARER_TOKEN_PREFIX + BEARER_TOKEN_VALUE;
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn(authorisationTokenValue);
        when(tokenValidationService.validate(BEARER_TOKEN_VALUE))
                .thenReturn(new BearerTokenTO());

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(tokenValidationService).validate(BEARER_TOKEN_VALUE);
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);

        verify(httpServletResponse, never()).setStatus(ArgumentMatchers.anyInt());
    }

    @Test
    void doFilter_withoutOauthHeader_shouldSkipValidation() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME))
                .thenReturn(null);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        verify(tokenValidationService, never()).validate(ArgumentMatchers.anyString());
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
    }

    @Test
    void doFilter_withConsentsPath_shouldSkipValidation() throws ServletException, IOException {
        // Given
        when(aspspProfileService.getScaApproaches())
                .thenReturn(Arrays.asList(ScaApproach.REDIRECT, ScaApproach.OAUTH));

        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME))
                .thenReturn(OAUTH_MODE_INTEGRATED);
        when(requestPathResolver.resolveRequestPath(httpServletRequest))
                .thenReturn(CONSENTS_PATH);

        String authorisationTokenValue = BEARER_TOKEN_PREFIX + BEARER_TOKEN_VALUE;
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn(authorisationTokenValue);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(tokenValidationService, never()).validate(ArgumentMatchers.anyString());
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
    }

    @Test
    void doFilter_withFundsConfirmationPath_shouldSkipValidation() throws ServletException, IOException {
        // Given
        when(aspspProfileService.getScaApproaches())
                .thenReturn(Arrays.asList(ScaApproach.REDIRECT, ScaApproach.OAUTH));

        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME))
                .thenReturn(OAUTH_MODE_INTEGRATED);
        when(requestPathResolver.resolveRequestPath(httpServletRequest))
                .thenReturn(FUNDS_CONFIRMATION_PATH);

        String authorisationTokenValue = BEARER_TOKEN_PREFIX + BEARER_TOKEN_VALUE;

        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn(authorisationTokenValue);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(tokenValidationService, never()).validate(ArgumentMatchers.anyString());
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
    }

    @Test
    void doFilter_withoutOauthInProfile_shouldReturnError() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME))
                .thenReturn(OAUTH_MODE_INTEGRATED);

        when(aspspProfileService.getScaApproaches())
                .thenReturn(Collections.singletonList(ScaApproach.REDIRECT));

        TppErrorMessage tppErrorMessage = new TppErrorMessage(MessageCategory.ERROR, MessageErrorCode.FORMAT_ERROR);

        ArgumentCaptor<Integer> integerArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<TppErrorMessage> tppErrorMessageArgumentCaptor = ArgumentCaptor.forClass(TppErrorMessage.class);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(filterChain, never()).doFilter(any(), any());
        verify(tppErrorMessageWriter).writeError(eq(httpServletResponse), integerArgumentCaptor.capture(), tppErrorMessageArgumentCaptor.capture());
        assertEquals((int) integerArgumentCaptor.getValue(), HttpServletResponse.SC_BAD_REQUEST);
        assertEquals(tppErrorMessageArgumentCaptor.getValue(), tppErrorMessage);
    }

    @Test
    void doFilter_withInvalidOauthHeader_shouldReturnError() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME))
                .thenReturn(OAUTH_MODE_INVALID_VALUE);

        TppErrorMessage tppErrorMessage = new TppErrorMessage(MessageCategory.ERROR, MessageErrorCode.FORMAT_ERROR);

        ArgumentCaptor<Integer> integerArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<TppErrorMessage> tppErrorMessageArgumentCaptor = ArgumentCaptor.forClass(TppErrorMessage.class);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(filterChain, never()).doFilter(any(), any());
        verify(tppErrorMessageWriter).writeError(eq(httpServletResponse), integerArgumentCaptor.capture(), tppErrorMessageArgumentCaptor.capture());
        assertEquals((int) integerArgumentCaptor.getValue(), HttpServletResponse.SC_BAD_REQUEST);
        assertEquals(tppErrorMessageArgumentCaptor.getValue(), tppErrorMessage);
    }

    @Test
    void doFilterInternalTest_withNoTokenInHeader_shouldReturnError() throws ServletException, IOException {
        // Given
        when(aspspProfileService.getScaApproaches())
                .thenReturn(Arrays.asList(ScaApproach.REDIRECT, ScaApproach.OAUTH));

        when(aspspProfileService.getAspspSettings())
                .thenReturn(new JsonReader().getObjectFromFile(ASPSP_SETTINGS_JSON_PATH, AspspSettings.class));

        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME))
                .thenReturn(OAUTH_MODE_INTEGRATED);
        when(requestPathResolver.resolveRequestPath(httpServletRequest))
                .thenReturn(ACCOUNTS_PATH);

        TppErrorMessage tppErrorMessage = new TppErrorMessage(MessageCategory.ERROR, MessageErrorCode.TOKEN_INVALID);

        ArgumentCaptor<Integer> integerArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<TppErrorMessage> tppErrorMessageArgumentCaptor = ArgumentCaptor.forClass(TppErrorMessage.class);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(filterChain, never()).doFilter(any(), any());
        verify(tppErrorMessageWriter).writeError(eq(httpServletResponse), integerArgumentCaptor.capture(), tppErrorMessageArgumentCaptor.capture());
        assertEquals((int) integerArgumentCaptor.getValue(), HttpServletResponse.SC_FORBIDDEN);
        assertEquals(tppErrorMessageArgumentCaptor.getValue(), tppErrorMessage);
    }

    @Test
    void doFilterInternalTest_withBlankTokenInHeader_shouldReturnError() throws ServletException, IOException {
        // Given
        when(aspspProfileService.getScaApproaches())
                .thenReturn(Arrays.asList(ScaApproach.REDIRECT, ScaApproach.OAUTH));

        when(aspspProfileService.getAspspSettings())
                .thenReturn(new JsonReader().getObjectFromFile(ASPSP_SETTINGS_JSON_PATH, AspspSettings.class));

        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn(OAUTH_MODE_INTEGRATED);
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn("");
        when(requestPathResolver.resolveRequestPath(httpServletRequest)).thenReturn(ACCOUNTS_PATH);

        TppErrorMessage tppErrorMessage = new TppErrorMessage(MessageCategory.ERROR, MessageErrorCode.TOKEN_INVALID);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        ArgumentCaptor<Integer> integerArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<TppErrorMessage> tppErrorMessageArgumentCaptor = ArgumentCaptor.forClass(TppErrorMessage.class);

        // Then
        verify(filterChain, never()).doFilter(any(), any());
        verify(tppErrorMessageWriter).writeError(eq(httpServletResponse), integerArgumentCaptor.capture(), tppErrorMessageArgumentCaptor.capture());
        assertEquals((int) integerArgumentCaptor.getValue(), HttpServletResponse.SC_FORBIDDEN);
        assertEquals(tppErrorMessageArgumentCaptor.getValue(), tppErrorMessage);
    }

    @Test
    void doFilterInternalTest_withNoBearerPrefixInHeader_shouldReturnError() throws ServletException, IOException {
        // Given
        when(aspspProfileService.getScaApproaches())
                .thenReturn(Arrays.asList(ScaApproach.REDIRECT, ScaApproach.OAUTH));

        when(aspspProfileService.getAspspSettings())
                .thenReturn(new JsonReader().getObjectFromFile(ASPSP_SETTINGS_JSON_PATH, AspspSettings.class));

        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn(OAUTH_MODE_INTEGRATED);
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn(BEARER_TOKEN_VALUE);
        when(requestPathResolver.resolveRequestPath(httpServletRequest)).thenReturn(ACCOUNTS_PATH);

        TppErrorMessage tppErrorMessage = new TppErrorMessage(MessageCategory.ERROR, MessageErrorCode.TOKEN_INVALID);

        ArgumentCaptor<Integer> integerArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<TppErrorMessage> tppErrorMessageArgumentCaptor = ArgumentCaptor.forClass(TppErrorMessage.class);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(filterChain, never()).doFilter(any(), any());
        verify(tppErrorMessageWriter).writeError(eq(httpServletResponse), integerArgumentCaptor.capture(), tppErrorMessageArgumentCaptor.capture());
        assertEquals((int) integerArgumentCaptor.getValue(), HttpServletResponse.SC_FORBIDDEN);
        assertEquals(tppErrorMessageArgumentCaptor.getValue(), tppErrorMessage);
    }

    @Test
    void doFilterInternalTest_withBlankToken_preStepOauth_shouldReturnError() throws ServletException, IOException {
        // Given
        when(aspspProfileService.getScaApproaches())
                .thenReturn(Arrays.asList(ScaApproach.REDIRECT, ScaApproach.OAUTH));

        when(aspspProfileService.getAspspSettings())
                .thenReturn(new JsonReader().getObjectFromFile(ASPSP_SETTINGS_JSON_PATH, AspspSettings.class));

        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn(OAUTH_MODE_PRE_STEP);

        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn(null);

        TppErrorMessage tppErrorMessage = new TppErrorMessage(MessageCategory.ERROR, MessageErrorCode.UNAUTHORIZED_NO_TOKEN, IDP_CONFIGURATION_LINK);

        ArgumentCaptor<Integer> integerArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<TppErrorMessage> tppErrorMessageArgumentCaptor = ArgumentCaptor.forClass(TppErrorMessage.class);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(filterChain, never()).doFilter(any(), any());
        verify(tppErrorMessageWriter).writeError(eq(httpServletResponse), integerArgumentCaptor.capture(), tppErrorMessageArgumentCaptor.capture());
        assertEquals((int) integerArgumentCaptor.getValue(), HttpServletResponse.SC_FORBIDDEN);
        assertEquals(tppErrorMessageArgumentCaptor.getValue(), tppErrorMessage);
    }

    @Test
    void doFilter_withInvalidToken_shouldReturnError() throws ServletException, IOException {
        // Given
        when(aspspProfileService.getScaApproaches())
                .thenReturn(Arrays.asList(ScaApproach.REDIRECT, ScaApproach.OAUTH));

        when(aspspProfileService.getAspspSettings())
                .thenReturn(new JsonReader().getObjectFromFile(ASPSP_SETTINGS_JSON_PATH, AspspSettings.class));

        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn(OAUTH_MODE_INTEGRATED);
        when(requestPathResolver.resolveRequestPath(httpServletRequest)).thenReturn(ACCOUNTS_PATH);

        String authorisationTokenValue = BEARER_TOKEN_PREFIX + BEARER_TOKEN_INVALID_VALUE;
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn(authorisationTokenValue);
        when(tokenValidationService.validate(BEARER_TOKEN_INVALID_VALUE))
                .thenReturn(null);

        TppErrorMessage tppErrorMessage = new TppErrorMessage(MessageCategory.ERROR, MessageErrorCode.TOKEN_INVALID);

        ArgumentCaptor<Integer> integerArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<TppErrorMessage> tppErrorMessageArgumentCaptor = ArgumentCaptor.forClass(TppErrorMessage.class);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(tokenValidationService).validate(BEARER_TOKEN_INVALID_VALUE);

        verify(filterChain, never()).doFilter(any(), any());
        verify(tppErrorMessageWriter).writeError(eq(httpServletResponse), integerArgumentCaptor.capture(), tppErrorMessageArgumentCaptor.capture());
        assertEquals((int) integerArgumentCaptor.getValue(), HttpServletResponse.SC_FORBIDDEN);
        assertEquals(tppErrorMessageArgumentCaptor.getValue(), tppErrorMessage);
    }

    @Test
    void doFilter_onCustomEndpoint_shouldSkipFilter() throws ServletException, IOException {
        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        verifyNoMoreInteractions(aspspProfileService, tppErrorMessageWriter, tokenValidationService, oauthDataHolder);
    }
}
