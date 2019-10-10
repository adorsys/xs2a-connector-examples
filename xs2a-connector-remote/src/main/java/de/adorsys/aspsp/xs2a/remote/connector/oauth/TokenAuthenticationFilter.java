package de.adorsys.aspsp.xs2a.remote.connector.oauth;

import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.psd2.mapper.Xs2aObjectMapper;
import de.adorsys.psd2.xs2a.web.error.TppErrorMessageBuilder;
import de.adorsys.psd2.xs2a.web.filter.AbstractXs2aFilter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

import static de.adorsys.psd2.xs2a.core.error.MessageErrorCode.TOKEN_INVALID;
import static de.adorsys.psd2.xs2a.exception.MessageCategory.ERROR;

@Slf4j
@Component
public class TokenAuthenticationFilter extends AbstractXs2aFilter {
    private static final String BEARER_TOKEN_PREFIX = "Bearer ";

    private final String oauthModeHeaderName;
    private final Xs2aObjectMapper mapper;
    private final TppErrorMessageBuilder tppErrorMessageBuilder;
    private final TokenValidationService tokenValidationService;

    public TokenAuthenticationFilter(@Value("${oauth.header-name:X-OAUTH-PREFERRED}") String oauthModeHeaderName,
                                     Xs2aObjectMapper mapper,
                                     TppErrorMessageBuilder tppErrorMessageBuilder,
                                     TokenValidationService tokenValidationService) {
        this.oauthModeHeaderName = oauthModeHeaderName;
        this.mapper = mapper;
        this.tppErrorMessageBuilder = tppErrorMessageBuilder;
        this.tokenValidationService = tokenValidationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        boolean isOauthMode = Boolean.parseBoolean(request.getHeader(oauthModeHeaderName));
        if (!isOauthMode) {
            chain.doFilter(request, response);
            return;
        }

        BearerTokenTO token = tokenValidationService.validate(resolveBearerToken(request));
        if (token == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().print(mapper.writeValueAsString(tppErrorMessageBuilder.buildTppErrorMessage(ERROR, TOKEN_INVALID)));
            return;
        }
        chain.doFilter(request, response);
    }

    private String resolveBearerToken(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION))
                       .filter(StringUtils::isNotBlank)
                       .filter(t -> StringUtils.startsWithIgnoreCase(t, BEARER_TOKEN_PREFIX))
                       .map(t -> StringUtils.substringAfter(t, BEARER_TOKEN_PREFIX))
                       .orElse(null);
    }
}