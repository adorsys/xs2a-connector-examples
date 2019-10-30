package de.adorsys.aspsp.xs2a.remote.connector.oauth;

import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.psd2.aspsp.profile.domain.AspspSettings;
import de.adorsys.psd2.aspsp.profile.domain.pis.PisAspspProfileSetting;
import de.adorsys.psd2.aspsp.profile.service.AspspProfileService;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.profile.PaymentType;
import de.adorsys.psd2.xs2a.core.profile.ScaApproach;
import de.adorsys.psd2.xs2a.exception.MessageCategory;
import de.adorsys.psd2.xs2a.web.error.TppErrorMessageBuilder;
import de.adorsys.psd2.xs2a.web.filter.TppErrorMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import java.util.*;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TokenAuthenticationFilterTest {
    private static final String OAUTH_MODE_HEADER_NAME = "X-OAUTH-PREFERRED";
    private static final String OAUTH_MODE_INTEGRATED = "integrated";
    private static final String BEARER_TOKEN_PREFIX = "Bearer ";
    private static final String BEARER_TOKEN_VALUE = "some_token";
    private static final String SERVLET_PATH = "/v1/accounts";

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
    }

    @Test
    public void doFilterInternalTest_withValidToken() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn(OAUTH_MODE_INTEGRATED);
        when(httpServletRequest.getServletPath()).thenReturn(SERVLET_PATH);

        when(aspspProfileService.getScaApproaches()).thenReturn(Arrays.asList(ScaApproach.REDIRECT, ScaApproach.OAUTH));

        Map<PaymentType, Set<String>> supportedPaymentTypeAndProductMatrix = new HashMap<>();
        supportedPaymentTypeAndProductMatrix.put(PaymentType.SINGLE, Collections.singleton("sepa-credit-transfers"));
        when(aspspProfileService.getAspspSettings()).thenReturn(new AspspSettings(null, new PisAspspProfileSetting(supportedPaymentTypeAndProductMatrix, 0, 0, false, null, null), null, null));

        String authorisationTokenValue = BEARER_TOKEN_PREFIX + BEARER_TOKEN_VALUE;

        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn(authorisationTokenValue);

        when(tokenValidationService.validate(BEARER_TOKEN_VALUE)).thenReturn(new BearerTokenTO());

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(tokenValidationService).validate(BEARER_TOKEN_VALUE);
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);

        verify(httpServletResponse, never()).setStatus(anyInt());
        verify(tppErrorMessageBuilder, never()).buildTppErrorMessage(any(), any());
    }

    @Test
    public void doFilter_withoutOauthHeader_shouldSkipValidation() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn(null);
        when(httpServletRequest.getServletPath()).thenReturn(SERVLET_PATH);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        verify(tokenValidationService, never()).validate(anyString());
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
    }

    @Test
    public void doFilter_withInvalidOauthHeader_shouldReturnError() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn("invalid value");
        when(httpServletRequest.getServletPath()).thenReturn(SERVLET_PATH);

        when(aspspProfileService.getScaApproaches()).thenReturn(Arrays.asList(ScaApproach.REDIRECT, ScaApproach.OAUTH));

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
        when(httpServletRequest.getServletPath()).thenReturn(SERVLET_PATH);

        when(aspspProfileService.getScaApproaches()).thenReturn(Arrays.asList(ScaApproach.REDIRECT, ScaApproach.OAUTH));

        Map<PaymentType, Set<String>> supportedPaymentTypeAndProductMatrix = new HashMap<>();
        supportedPaymentTypeAndProductMatrix.put(PaymentType.SINGLE, Collections.singleton("sepa-credit-transfers"));
        when(aspspProfileService.getAspspSettings()).thenReturn(new AspspSettings(null, new PisAspspProfileSetting(supportedPaymentTypeAndProductMatrix, 0, 0, false, null, null), null, null));

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
    public void doFilterInternalTest_withBlankTokenInHeader_shouldReturnError() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn(OAUTH_MODE_INTEGRATED);
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn("");
        when(httpServletRequest.getServletPath()).thenReturn(SERVLET_PATH);

        when(aspspProfileService.getScaApproaches()).thenReturn(Arrays.asList(ScaApproach.REDIRECT, ScaApproach.OAUTH));

        Map<PaymentType, Set<String>> supportedPaymentTypeAndProductMatrix = new HashMap<>();
        supportedPaymentTypeAndProductMatrix.put(PaymentType.SINGLE, Collections.singleton("sepa-credit-transfers"));
        when(aspspProfileService.getAspspSettings()).thenReturn(new AspspSettings(null, new PisAspspProfileSetting(supportedPaymentTypeAndProductMatrix, 0, 0, false, null, null), null, null));

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
        when(httpServletRequest.getServletPath()).thenReturn(SERVLET_PATH);

        when(aspspProfileService.getScaApproaches()).thenReturn(Arrays.asList(ScaApproach.REDIRECT, ScaApproach.OAUTH));

        Map<PaymentType, Set<String>> supportedPaymentTypeAndProductMatrix = new HashMap<>();
        supportedPaymentTypeAndProductMatrix.put(PaymentType.SINGLE, Collections.singleton("sepa-credit-transfers"));
        when(aspspProfileService.getAspspSettings()).thenReturn(new AspspSettings(null, new PisAspspProfileSetting(supportedPaymentTypeAndProductMatrix, 0, 0, false, null, null), null, null));

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
}
