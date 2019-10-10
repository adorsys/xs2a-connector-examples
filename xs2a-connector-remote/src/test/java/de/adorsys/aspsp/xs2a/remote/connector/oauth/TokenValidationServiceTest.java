package de.adorsys.aspsp.xs2a.remote.connector.oauth;

import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionHandler;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.UserMgmtRestClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TokenValidationServiceTest {
    @Mock
    private AuthRequestInterceptor authRequestInterceptor;
    @Mock
    private UserMgmtRestClient userMgmtRestClient;

    @InjectMocks
    private TokenValidationService tokenValidationService;

    @Test
    public void validate_withValidToken_shouldReturnValidToken() {
        // Given
        String token = "some token";
        BearerTokenTO expected = new BearerTokenTO();
        when(userMgmtRestClient.validate(token))
                .thenReturn(ResponseEntity.ok(expected));

        // When
        BearerTokenTO actual = tokenValidationService.validate(token);

        // Then
        assertEquals(expected, actual);

        InOrder inOrder = Mockito.inOrder(authRequestInterceptor, userMgmtRestClient);
        inOrder.verify(authRequestInterceptor).setAccessToken(token);
        inOrder.verify(userMgmtRestClient).validate(token);
        inOrder.verify(authRequestInterceptor).setAccessToken(null);
    }

    @Test
    public void validate_onFeignException_shouldReturnNull() {
        // Given
        String token = "some token";
        when(userMgmtRestClient.validate(token))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "some message"));

        // When
        BearerTokenTO actual = tokenValidationService.validate(token);

        // Then
        assertNull(actual);

        InOrder inOrder = Mockito.inOrder(authRequestInterceptor, userMgmtRestClient);
        inOrder.verify(authRequestInterceptor).setAccessToken(token);
        inOrder.verify(userMgmtRestClient).validate(token);
        inOrder.verify(authRequestInterceptor).setAccessToken(null);
    }
}