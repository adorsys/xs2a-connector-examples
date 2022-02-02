package de.adorsys.ledgers.rest.client;

import de.adorsys.ledgers.middleware.rest.utils.Constants;
import feign.RequestInterceptor;
import feign.RequestTemplate;

public class AuthRequestInterceptor implements RequestInterceptor {

    private static final String BEARER_CONSTANT = "Bearer ";

    private ThreadLocal<String> accessToken = new ThreadLocal<>();

    @Override
    public void apply(RequestTemplate template) {
        if (accessToken.get() != null) {
            template.header(Constants.AUTH_HEADER_NAME, BEARER_CONSTANT + accessToken.get());
        }
    }

    public void setAccessToken(String accessToken) {
        this.accessToken.set(accessToken);
    }
}
