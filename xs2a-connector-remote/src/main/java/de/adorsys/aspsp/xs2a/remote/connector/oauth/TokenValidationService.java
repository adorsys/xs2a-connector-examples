package de.adorsys.aspsp.xs2a.remote.connector.oauth;

import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.UserMgmtRestClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenValidationService {
    private final AuthRequestInterceptor authInterceptor;
    private final UserMgmtRestClient ledgersUserMgmt;

    public BearerTokenTO validate(String bearerToken) {
        try {
            authInterceptor.setAccessToken(bearerToken);
            return ledgersUserMgmt.validate(bearerToken).getBody();
        } catch (FeignException e) {
            log.error("Token validation is failed");
        } finally {
            authInterceptor.setAccessToken(null);
        }
        return null;
    }
}
