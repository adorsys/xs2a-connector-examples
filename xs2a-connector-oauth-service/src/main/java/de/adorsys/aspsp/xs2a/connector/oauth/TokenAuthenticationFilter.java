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
import de.adorsys.psd2.aspsp.profile.service.AspspProfileService;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.profile.ScaApproach;
import de.adorsys.psd2.xs2a.web.Xs2aEndpointChecker;
import de.adorsys.psd2.xs2a.web.error.TppErrorMessageWriter;
import de.adorsys.psd2.xs2a.web.filter.AbstractXs2aFilter;
import de.adorsys.psd2.xs2a.web.filter.TppErrorMessage;
import de.adorsys.psd2.xs2a.web.request.RequestPathResolver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static de.adorsys.psd2.xs2a.core.error.MessageErrorCode.UNAUTHORIZED_NO_TOKEN;
import static de.adorsys.psd2.xs2a.core.domain.MessageCategory.ERROR;

@Slf4j
@Component
public class TokenAuthenticationFilter extends AbstractXs2aFilter {
    private static final String BEARER_TOKEN_PREFIX = "Bearer ";
    private static final String CONSENT_ENP_ENDING = "consents";
    private static final String FUNDS_CONF_ENP_ENDING = "funds-confirmations";

    private final RequestPathResolver requestPathResolver;
    private final String oauthModeHeaderName;
    private final TokenValidationService tokenValidationService;
    private final AspspProfileService aspspProfileService;
    private final OauthDataHolder oauthDataHolder;
    private final TppErrorMessageWriter tppErrorMessageWriter;

    public TokenAuthenticationFilter(RequestPathResolver requestPathResolver,
                                     @Value("${oauth.header-name:X-OAUTH-PREFERRED}") String oauthModeHeaderName,
                                     Xs2aEndpointChecker xs2aEndpointChecker,
                                     TokenValidationService tokenValidationService,
                                     AspspProfileService aspspProfileService,
                                     OauthDataHolder oauthDataHolder,
                                     TppErrorMessageWriter tppErrorMessageWriter) {
        super(tppErrorMessageWriter, xs2aEndpointChecker);
        this.requestPathResolver = requestPathResolver;
        this.oauthModeHeaderName = oauthModeHeaderName;
        this.tokenValidationService = tokenValidationService;
        this.aspspProfileService = aspspProfileService;
        this.oauthDataHolder = oauthDataHolder;
        this.tppErrorMessageWriter = tppErrorMessageWriter;
    }

    @Override
    protected void doFilterInternalCustom(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain chain) throws IOException, ServletException {
        String oauthHeader = request.getHeader(oauthModeHeaderName);
        boolean isOauthMode = StringUtils.isNotBlank(oauthHeader);

        if (!isOauthMode) {
            chain.doFilter(request, response);
            return;
        }

        Optional<OauthType> oauthTypeOptional = OauthType.getByValue(oauthHeader);

        if (!oauthTypeOptional.isPresent()) {
            log.info("Token authentication error: unknown OAuth type {}", oauthHeader);
            tppErrorMessageWriter.writeError(response, HttpServletResponse.SC_BAD_REQUEST, buildTppErrorMessage(MessageErrorCode.FORMAT_ERROR));
            return;
        }

        OauthType oauthType = oauthTypeOptional.get();
        String bearerToken = resolveBearerToken(request);

        if (isInvalidOauthRequest(request, response, oauthType, bearerToken)) {
            return;
        }

        oauthDataHolder.setOauthTypeAndToken(oauthType, bearerToken);

        chain.doFilter(request, response);
    }

    private boolean isInvalidOauthRequest(HttpServletRequest request, @NotNull HttpServletResponse response, OauthType oauthType, String bearerToken) throws IOException {
        if (!aspspProfileService.getScaApproaches().contains(ScaApproach.OAUTH)) {
            log.info("Token authentication error: OAUTH SCA approach is not supported in the profile");
            tppErrorMessageWriter.writeError(response, HttpServletResponse.SC_BAD_REQUEST, buildTppErrorMessage(MessageErrorCode.FORMAT_ERROR));
            return true;
        }

        if (oauthType == OauthType.PRE_STEP && StringUtils.isBlank(bearerToken)) {
            log.info("Token authentication error: token is absent in pre-step OAuth");
            String oauthConfigurationUrl = aspspProfileService.getAspspSettings().getCommon().getOauthConfigurationUrl();
            tppErrorMessageWriter.writeError(response, HttpServletResponse.SC_FORBIDDEN, buildTppErrorMessage(UNAUTHORIZED_NO_TOKEN, oauthConfigurationUrl));
            return true;
        }

        String requestPath = requestPathResolver.resolveRequestPath(request);
        boolean tokenRequired = isTokenRequired(oauthType, requestPath);
        if (tokenRequired && isTokenInvalid(bearerToken)) {
            log.info("Token authentication error: token is invalid");
            tppErrorMessageWriter.writeError(response, HttpServletResponse.SC_FORBIDDEN, buildTppErrorMessage(MessageErrorCode.TOKEN_INVALID));
            return true;
        }

        return false;
    }

    private boolean isTokenRequired(OauthType oauthType, String requestPath) {
        if (oauthType == OauthType.PRE_STEP) {
            return true;
        }

        String trimmedRequestPath = trimEndingSlash(requestPath);
        if (trimmedRequestPath.endsWith(CONSENT_ENP_ENDING) || trimmedRequestPath.endsWith(FUNDS_CONF_ENP_ENDING)) {
            return false;
        } else {
            Set<String> supportedProducts = aspspProfileService.getAspspSettings().getPis().getSupportedPaymentTypeAndProductMatrix().values().stream()
                                                    .flatMap(Collection::stream).collect(Collectors.toSet());
            return supportedProducts.stream().noneMatch(trimmedRequestPath::endsWith);
        }
    }

    private boolean isTokenInvalid(String bearerToken) {
        BearerTokenTO token = tokenValidationService.validate(bearerToken);
        return token == null;
    }

    private String resolveBearerToken(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION))
                       .filter(StringUtils::isNotBlank)
                       .filter(t -> StringUtils.startsWithIgnoreCase(t, BEARER_TOKEN_PREFIX))
                       .map(t -> StringUtils.substringAfter(t, BEARER_TOKEN_PREFIX))
                       .orElse(null);
    }

    private String trimEndingSlash(String input) {
        String result = input;

        while (StringUtils.endsWith(result, "/")) {
            result = StringUtils.removeEnd(result, "/");
        }

        return result;
    }

    private TppErrorMessage buildTppErrorMessage(MessageErrorCode messageErrorCode, Object... params) {
        return new TppErrorMessage(ERROR, messageErrorCode, params);
    }
}