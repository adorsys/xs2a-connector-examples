package de.adorsys.aspsp.xs2a.remote.connector.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TokenAuthenticationFilterTest {
    private static final String OAUTH_MODE_HEADER_NAME = "X-OAUTH-PREFERRED";
    private static final String BEARER_TOKEN_PREFIX = "Bearer ";
    private static final String BEARER_TOKEN_VALUE = "some_token";

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

    private TokenAuthenticationFilter tokenAuthenticationFilter;

    @Before
    public void setUp() {
        tokenAuthenticationFilter = new TokenAuthenticationFilter(OAUTH_MODE_HEADER_NAME, new ObjectMapper(), tppErrorMessageBuilder, tokenValidationService);
    }

    @Test
    public void doFilterInternalTest_withValidToken() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn("true");
        when(httpServletRequest.getServletPath()).thenReturn("/v1/accounts");

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
        when(httpServletRequest.getServletPath()).thenReturn("/v1/accounts");

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        verify(tokenValidationService, never()).validate(anyString());
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
    }

    @Test
    public void doFilter_withoutInvalidOauthHeader_shouldSkipValidation() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn("invalid value");
        when(httpServletRequest.getServletPath()).thenReturn("/v1/accounts");

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        verify(tokenValidationService, never()).validate(anyString());
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
    }

    @Test
    public void doFilterInternalTest_withNoTokenInHeader_shouldReturnError() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn("true");
        when(httpServletRequest.getServletPath()).thenReturn("/v1/accounts");

        PrintWriter mockWriter = Mockito.mock(PrintWriter.class);
        when(httpServletResponse.getWriter()).thenReturn(mockWriter);

        TppErrorMessage tppErrorMessage = new TppErrorMessage(MessageCategory.ERROR, MessageErrorCode.TOKEN_INVALID, "some message");
        when(tppErrorMessageBuilder.buildTppErrorMessage(MessageCategory.ERROR, MessageErrorCode.TOKEN_INVALID))
                .thenReturn(tppErrorMessage);

        String errorMessage = new ObjectMapper().writeValueAsString(tppErrorMessage);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(httpServletResponse).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(httpServletResponse).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        verify(mockWriter).print(errorMessage);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void doFilterInternalTest_withBlankTokenInHeader_shouldReturnError() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn("true");
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn("");
        when(httpServletRequest.getServletPath()).thenReturn("/v1/accounts");

        PrintWriter mockWriter = Mockito.mock(PrintWriter.class);
        when(httpServletResponse.getWriter()).thenReturn(mockWriter);

        TppErrorMessage tppErrorMessage = new TppErrorMessage(MessageCategory.ERROR, MessageErrorCode.TOKEN_INVALID, "some message");
        when(tppErrorMessageBuilder.buildTppErrorMessage(MessageCategory.ERROR, MessageErrorCode.TOKEN_INVALID))
                .thenReturn(tppErrorMessage);

        String errorMessage = new ObjectMapper().writeValueAsString(tppErrorMessage);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(httpServletResponse).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(httpServletResponse).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        verify(mockWriter).print(errorMessage);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    public void doFilterInternalTest_withNoBearerPrefixInHeader_shouldReturnError() throws ServletException, IOException {
        // Given
        when(httpServletRequest.getHeader(OAUTH_MODE_HEADER_NAME)).thenReturn("true");
        when(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn(BEARER_TOKEN_VALUE);
        when(httpServletRequest.getServletPath()).thenReturn("/v1/accounts");

        PrintWriter mockWriter = Mockito.mock(PrintWriter.class);
        when(httpServletResponse.getWriter()).thenReturn(mockWriter);

        TppErrorMessage tppErrorMessage = new TppErrorMessage(MessageCategory.ERROR, MessageErrorCode.TOKEN_INVALID, "some message");
        when(tppErrorMessageBuilder.buildTppErrorMessage(MessageCategory.ERROR, MessageErrorCode.TOKEN_INVALID))
                .thenReturn(tppErrorMessage);

        String errorMessage = new ObjectMapper().writeValueAsString(tppErrorMessage);

        // When
        tokenAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(httpServletResponse).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(httpServletResponse).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        verify(mockWriter).print(errorMessage);
        verify(filterChain, never()).doFilter(any(), any());
    }
}
