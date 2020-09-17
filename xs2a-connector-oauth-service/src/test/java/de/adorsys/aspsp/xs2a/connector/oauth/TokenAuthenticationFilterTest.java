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
import de.adorsys.psd2.aspsp.profile.domain.common.CommonAspspProfileSetting;
import de.adorsys.psd2.aspsp.profile.service.AspspProfileService;
import de.adorsys.psd2.xs2a.core.domain.MessageCategory;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.profile.ScaApproach;
import de.adorsys.psd2.xs2a.core.profile.ScaRedirectFlow;
import de.adorsys.psd2.xs2a.web.Xs2aEndpointChecker;
import de.adorsys.psd2.xs2a.web.error.TppErrorMessageWriter;
import de.adorsys.psd2.xs2a.web.filter.TppErrorMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenAuthenticationFilterTest {
    private static final String BEARER_TOKEN_VALUE = "some_token";
    private static final String IDP_CONFIGURATION_LINK = "http://localhost:4200/idp";

    private static final String INSTANCE_ID_HEADER = "instance-id";
    private static final String INSTANCE_ID = "bank1";

    @Mock
    private TokenValidationService tokenValidationService;
    @Mock
    private HttpServletRequest httpServletRequest;
    @Mock
    private HttpServletResponse httpServletResponse;
    @Mock
    private FilterChain filterChain;
    @Mock
    private AspspProfileService aspspProfileService;
    @Mock
    private TppErrorMessageWriter tppErrorMessageWriter;
    @Mock
    private Xs2aEndpointChecker xs2aEndpointChecker;
    @Mock
    private CommonAspspProfileSetting commonAspspProfileSetting;

    @Captor
    private ArgumentCaptor<TppErrorMessage> tppErrorMessageArgumentCaptor;

    private TokenAuthenticationFilter tokenAuthenticationFilter;

    @BeforeEach
    void setUp() {
        AspspSettings aspspSettings = new AspspSettings(null, null, null, commonAspspProfileSetting);
        tokenAuthenticationFilter = new TokenAuthenticationFilter(tokenValidationService,
                                                                  xs2aEndpointChecker,
                                                                  aspspProfileService,
                                                                  tppErrorMessageWriter);

        when(xs2aEndpointChecker.isXs2aEndpoint(httpServletRequest)).thenReturn(true);
        when(aspspProfileService.getAspspSettings(INSTANCE_ID)).thenReturn(aspspSettings);
    }

    @Test
    void doFilter_withValidToken_preStep() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + BEARER_TOKEN_VALUE);
        when(httpServletRequest.getHeader(INSTANCE_ID_HEADER)).thenReturn(INSTANCE_ID);

        when(aspspProfileService.getScaApproaches(INSTANCE_ID)).thenReturn(Collections.singletonList(ScaApproach.REDIRECT));
        when(commonAspspProfileSetting.getScaRedirectFlow()).thenReturn(ScaRedirectFlow.OAUTH_PRE_STEP);

        when(tokenValidationService.validate(BEARER_TOKEN_VALUE)).thenReturn(new BearerTokenTO());

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(tokenValidationService).validate(BEARER_TOKEN_VALUE);
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);

        verify(httpServletResponse, never()).setStatus(ArgumentMatchers.anyInt());
    }

    @Test
    void doFilter_withValidToken_notRedirect() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + BEARER_TOKEN_VALUE);
        when(httpServletRequest.getHeader(INSTANCE_ID_HEADER)).thenReturn(INSTANCE_ID);

        when(aspspProfileService.getScaApproaches(INSTANCE_ID)).thenReturn(Collections.singletonList(ScaApproach.EMBEDDED));
        when(commonAspspProfileSetting.getScaRedirectFlow()).thenReturn(ScaRedirectFlow.OAUTH_PRE_STEP);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);

        verify(tokenValidationService, never()).validate(BEARER_TOKEN_VALUE);
        verify(httpServletResponse, never()).setStatus(ArgumentMatchers.anyInt());
    }


    @Test
    void doFilterInternalTest_withoutTokenInHeader_shouldReturnError() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);
        when(httpServletRequest.getHeader(INSTANCE_ID_HEADER)).thenReturn(INSTANCE_ID);

        when(aspspProfileService.getScaApproaches(INSTANCE_ID)).thenReturn(Collections.singletonList(ScaApproach.REDIRECT));
        when(commonAspspProfileSetting.getScaRedirectFlow()).thenReturn(ScaRedirectFlow.OAUTH_PRE_STEP);
        when(commonAspspProfileSetting.getOauthConfigurationUrl()).thenReturn(IDP_CONFIGURATION_LINK);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(tppErrorMessageWriter).writeError(eq(httpServletResponse), tppErrorMessageArgumentCaptor.capture());

        verify(filterChain, never()).doFilter(httpServletRequest, httpServletResponse);
        verify(tokenValidationService, never()).validate(BEARER_TOKEN_VALUE);

        TppErrorMessage tppErrorMessage = new TppErrorMessage(MessageCategory.ERROR, MessageErrorCode.UNAUTHORIZED_NO_TOKEN, IDP_CONFIGURATION_LINK);
        assertEquals(tppErrorMessage, tppErrorMessageArgumentCaptor.getValue());
    }

    @Test
    void doFilterInternalTest_withBlankTokenInHeader_shouldReturnError() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("");
        when(httpServletRequest.getHeader(INSTANCE_ID_HEADER)).thenReturn(INSTANCE_ID);

        when(aspspProfileService.getScaApproaches(INSTANCE_ID)).thenReturn(Collections.singletonList(ScaApproach.REDIRECT));
        when(commonAspspProfileSetting.getScaRedirectFlow()).thenReturn(ScaRedirectFlow.OAUTH_PRE_STEP);
        when(commonAspspProfileSetting.getOauthConfigurationUrl()).thenReturn(IDP_CONFIGURATION_LINK);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(tppErrorMessageWriter).writeError(eq(httpServletResponse), tppErrorMessageArgumentCaptor.capture());

        verify(filterChain, never()).doFilter(httpServletRequest, httpServletResponse);
        verify(tokenValidationService, never()).validate(BEARER_TOKEN_VALUE);

        TppErrorMessage tppErrorMessage = new TppErrorMessage(MessageCategory.ERROR, MessageErrorCode.UNAUTHORIZED_NO_TOKEN, IDP_CONFIGURATION_LINK);
        assertEquals(tppErrorMessage, tppErrorMessageArgumentCaptor.getValue());
    }

    @Test
    void doFilterInternalTest_withNoBearerPrefixInHeader_shouldReturnError() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(BEARER_TOKEN_VALUE);
        when(httpServletRequest.getHeader(INSTANCE_ID_HEADER)).thenReturn(INSTANCE_ID);

        when(aspspProfileService.getScaApproaches(INSTANCE_ID)).thenReturn(Collections.singletonList(ScaApproach.REDIRECT));
        when(commonAspspProfileSetting.getScaRedirectFlow()).thenReturn(ScaRedirectFlow.OAUTH_PRE_STEP);
        when(commonAspspProfileSetting.getOauthConfigurationUrl()).thenReturn(IDP_CONFIGURATION_LINK);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(tppErrorMessageWriter).writeError(eq(httpServletResponse), tppErrorMessageArgumentCaptor.capture());

        verify(filterChain, never()).doFilter(httpServletRequest, httpServletResponse);
        verify(tokenValidationService, never()).validate(BEARER_TOKEN_VALUE);

        TppErrorMessage tppErrorMessage = new TppErrorMessage(MessageCategory.ERROR, MessageErrorCode.UNAUTHORIZED_NO_TOKEN, IDP_CONFIGURATION_LINK);
        assertEquals(tppErrorMessage, tppErrorMessageArgumentCaptor.getValue());
    }

    @Test
    void doFilter_withInvalidToken_shouldReturnError() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + BEARER_TOKEN_VALUE);
        when(httpServletRequest.getHeader(INSTANCE_ID_HEADER)).thenReturn(INSTANCE_ID);

        when(aspspProfileService.getScaApproaches(INSTANCE_ID)).thenReturn(Collections.singletonList(ScaApproach.REDIRECT));
        when(commonAspspProfileSetting.getScaRedirectFlow()).thenReturn(ScaRedirectFlow.OAUTH_PRE_STEP);

        when(tokenValidationService.validate(BEARER_TOKEN_VALUE)).thenReturn(null);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(tppErrorMessageWriter).writeError(eq(httpServletResponse), tppErrorMessageArgumentCaptor.capture());

        verify(tokenValidationService).validate(BEARER_TOKEN_VALUE);

        verify(filterChain, never()).doFilter(httpServletRequest, httpServletResponse);

        assertEquals(MessageCategory.ERROR, tppErrorMessageArgumentCaptor.getValue().getCategory());
        assertEquals(MessageErrorCode.TOKEN_INVALID, tppErrorMessageArgumentCaptor.getValue().getCode());
    }
}
