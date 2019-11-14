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
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.profile.ScaApproach;
import de.adorsys.psd2.xs2a.exception.MessageCategory;
import de.adorsys.psd2.xs2a.web.error.TppErrorMessageBuilder;
import de.adorsys.psd2.xs2a.web.filter.TppErrorMessage;
import de.adorsys.xs2a.reader.JsonReader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TokenAuthenticationFilterTest {
    private static final String ASPSP_SETTINGS_JSON_PATH = "json/oauth/aspsp-settings.json";

    private static final String OAUTH_MODE_HEADER_NAME = "X-OAUTH-PREFERRED";
    private static final String OAUTH_MODE_INTEGRATED = "integrated";
    private static final String OAUTH_MODE_PRE_STEP = "pre-step";
    private static final String OAUTH_MODE_INVALID_VALUE = "invalid value";

    private static final String BEARER_TOKEN_PREFIX = "Bearer ";
    private static final String BEARER_TOKEN_VALUE = "some_token";
    private static final String BEARER_TOKEN_INVALID_VALUE = "invalid value";

    private static final String ERROR_MESSAGE_TEXT = "some message";

    private static final String ACCOUNTS_PATH = "/v1/accounts";
    private static final String CONSENTS_PATH = "/v1/consents";
    private static final String FUNDS_CONFIRMATION_PATH = "/v1/funds-confirmations";
    private static final String PAYMENTS_PATH = "/v1/payments/sepa-credits-transfer";
    private static final String IDP_CONFIGURATION_LINK = "http://localhost:4200/idp";

    @Mock
    private TppErrorMessageBuilder tppErrorMessageBuilder;
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

    private TokenAuthenticationFilter tokenAuthenticationFilter;

    @Before
    public void setUp() {
        tokenAuthenticationFilter = new TokenAuthenticationFilter(OAUTH_MODE_HEADER_NAME, tppErrorMessageBuilder, tokenValidationService, aspspProfileService, oauthDataHolder);

        when(aspspProfileService.getScaApproaches())
                .thenReturn(Arrays.asList(ScaApproach.REDIRECT, ScaApproach.OAUTH));
        when(aspspProfileService.getAspspSettings())
                .thenReturn(new JsonReader().getObjectFromFile(ASPSP_SETTINGS_JSON_PATH, AspspSettings.class));
    }

    @Test
    public void doFilter_withValidToken() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn(OAUTH_MODE_INTEGRATED);
        when(httpServletRequest.getServletPath()).thenReturn(ACCOUNTS_PATH);

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
        verify(tppErrorMessageBuilder, never()).buildTppErrorMessage(any(), any());
    }

    @Test
    public void doFilter_withValidToken_preStep() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn(OAUTH_MODE_PRE_STEP);
        when(httpServletRequest.getServletPath()).thenReturn(PAYMENTS_PATH);

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
        verify(tppErrorMessageBuilder, never()).buildTppErrorMessage(any(), any());
    }

    @Test
    public void doFilter_withTrailingSlashesInPath() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn(OAUTH_MODE_INTEGRATED);
        when(httpServletRequest.getServletPath()).thenReturn(ACCOUNTS_PATH + "////");

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
        verify(tppErrorMessageBuilder, never()).buildTppErrorMessage(any(), any());
    }

    @Test
    public void doFilter_withPaymentsPath() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME))
                .thenReturn(OAUTH_MODE_INTEGRATED);
        when(httpServletRequest.getServletPath())
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
        verify(tppErrorMessageBuilder, never()).buildTppErrorMessage(any(), any());
    }

    @Test
    public void doFilter_withoutOauthHeader_shouldSkipValidation() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME))
                .thenReturn(null);
        when(httpServletRequest.getServletPath())
                .thenReturn(ACCOUNTS_PATH);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        verify(tokenValidationService, never()).validate(ArgumentMatchers.anyString());
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
    }

    @Test
    public void doFilter_withConsentsPath_shouldSkipValidation() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME))
                .thenReturn(OAUTH_MODE_INTEGRATED);
        when(httpServletRequest.getServletPath())
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
    public void doFilter_withFundsConfirmationPath_shouldSkipValidation() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME))
                .thenReturn(OAUTH_MODE_INTEGRATED);
        when(httpServletRequest.getServletPath())
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
    public void doFilter_withoutOauthInProfile_shouldReturnError() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME))
                .thenReturn(OAUTH_MODE_INTEGRATED);
        when(httpServletRequest.getServletPath())
                .thenReturn(ACCOUNTS_PATH);

        when(aspspProfileService.getScaApproaches())
                .thenReturn(Collections.singletonList(ScaApproach.REDIRECT));

        PrintWriter mockWriter = Mockito.mock(PrintWriter.class);
        when(httpServletResponse.getWriter())
                .thenReturn(mockWriter);
        TppErrorMessage tppErrorMessage = new TppErrorMessage(MessageCategory.ERROR, MessageErrorCode.FORMAT_ERROR, "some message");
        when(tppErrorMessageBuilder.buildTppErrorMessage(MessageCategory.ERROR, MessageErrorCode.FORMAT_ERROR))
                .thenReturn(tppErrorMessage);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(httpServletResponse).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verify(httpServletResponse).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        verify(mockWriter).print(tppErrorMessage.toString());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void doFilter_withInvalidOauthHeader_shouldReturnError() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME))
                .thenReturn(OAUTH_MODE_INVALID_VALUE);
        when(httpServletRequest.getServletPath()).thenReturn(ACCOUNTS_PATH);

        PrintWriter mockWriter = Mockito.mock(PrintWriter.class);
        when(httpServletResponse.getWriter()).thenReturn(mockWriter);

        TppErrorMessage tppErrorMessage = new TppErrorMessage(MessageCategory.ERROR, MessageErrorCode.FORMAT_ERROR, "some message");
        when(tppErrorMessageBuilder.buildTppErrorMessage(MessageCategory.ERROR, MessageErrorCode.FORMAT_ERROR))
                .thenReturn(tppErrorMessage);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(httpServletResponse).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verify(httpServletResponse).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        verify(mockWriter).print(tppErrorMessage.toString());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void doFilterInternalTest_withNoTokenInHeader_shouldReturnError() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn(OAUTH_MODE_INTEGRATED);
        when(httpServletRequest.getServletPath()).thenReturn(ACCOUNTS_PATH);

        PrintWriter mockWriter = Mockito.mock(PrintWriter.class);
        when(httpServletResponse.getWriter()).thenReturn(mockWriter);

        TppErrorMessage tppErrorMessage = new TppErrorMessage(MessageCategory.ERROR, MessageErrorCode.TOKEN_INVALID, ERROR_MESSAGE_TEXT);
        when(tppErrorMessageBuilder.buildTppErrorMessage(MessageCategory.ERROR, MessageErrorCode.TOKEN_INVALID))
                .thenReturn(tppErrorMessage);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(httpServletResponse).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(httpServletResponse).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        verify(mockWriter).print(tppErrorMessage.toString());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void doFilterInternalTest_withBlankTokenInHeader_shouldReturnError() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn(OAUTH_MODE_INTEGRATED);
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn("");
        when(httpServletRequest.getServletPath()).thenReturn(ACCOUNTS_PATH);

        PrintWriter mockWriter = Mockito.mock(PrintWriter.class);
        when(httpServletResponse.getWriter()).thenReturn(mockWriter);

        TppErrorMessage tppErrorMessage = new TppErrorMessage(MessageCategory.ERROR, MessageErrorCode.TOKEN_INVALID, "some message");
        when(tppErrorMessageBuilder.buildTppErrorMessage(MessageCategory.ERROR, MessageErrorCode.TOKEN_INVALID))
                .thenReturn(tppErrorMessage);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(httpServletResponse).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(httpServletResponse).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        verify(mockWriter).print(tppErrorMessage.toString());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void doFilterInternalTest_withNoBearerPrefixInHeader_shouldReturnError() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn(OAUTH_MODE_INTEGRATED);
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn(BEARER_TOKEN_VALUE);
        when(httpServletRequest.getServletPath()).thenReturn(ACCOUNTS_PATH);

        PrintWriter mockWriter = Mockito.mock(PrintWriter.class);
        when(httpServletResponse.getWriter()).thenReturn(mockWriter);
        TppErrorMessage tppErrorMessage = new TppErrorMessage(MessageCategory.ERROR, MessageErrorCode.TOKEN_INVALID, "some message");
        when(tppErrorMessageBuilder.buildTppErrorMessage(MessageCategory.ERROR, MessageErrorCode.TOKEN_INVALID))
                .thenReturn(tppErrorMessage);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(httpServletResponse).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(httpServletResponse).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        verify(mockWriter).print(tppErrorMessage.toString());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void doFilterInternalTest_withBlankToken_preStepOauth_shouldReturnError() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn(OAUTH_MODE_PRE_STEP);
        when(httpServletRequest.getServletPath()).thenReturn(ACCOUNTS_PATH);

        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn(null);

        PrintWriter mockWriter = Mockito.mock(PrintWriter.class);
        when(httpServletResponse.getWriter()).thenReturn(mockWriter);
        TppErrorMessage tppErrorMessage = new TppErrorMessage(MessageCategory.ERROR, MessageErrorCode.UNAUTHORIZED_NO_TOKEN, IDP_CONFIGURATION_LINK);
        when(tppErrorMessageBuilder.buildTppErrorMessageWithPlaceholder(MessageCategory.ERROR, MessageErrorCode.UNAUTHORIZED_NO_TOKEN, IDP_CONFIGURATION_LINK))
                .thenReturn(tppErrorMessage);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(httpServletResponse).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(httpServletResponse).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        verify(mockWriter).print(tppErrorMessage.toString());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void doFilter_withInvalidToken_shouldReturnError() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn(OAUTH_MODE_INTEGRATED);
        when(httpServletRequest.getServletPath()).thenReturn(ACCOUNTS_PATH);

        String authorisationTokenValue = BEARER_TOKEN_PREFIX + BEARER_TOKEN_INVALID_VALUE;
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn(authorisationTokenValue);
        when(tokenValidationService.validate(BEARER_TOKEN_INVALID_VALUE))
                .thenReturn(null);

        PrintWriter mockWriter = Mockito.mock(PrintWriter.class);
        when(httpServletResponse.getWriter()).thenReturn(mockWriter);
        TppErrorMessage tppErrorMessage = new TppErrorMessage(MessageCategory.ERROR, MessageErrorCode.TOKEN_INVALID, "some message");
        when(tppErrorMessageBuilder.buildTppErrorMessage(MessageCategory.ERROR, MessageErrorCode.TOKEN_INVALID))
                .thenReturn(tppErrorMessage);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(tokenValidationService).validate(BEARER_TOKEN_INVALID_VALUE);

        verify(httpServletResponse).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(httpServletResponse).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        verify(mockWriter).print(tppErrorMessage.toString());
        verify(filterChain, never()).doFilter(any(), any());
    }
}
