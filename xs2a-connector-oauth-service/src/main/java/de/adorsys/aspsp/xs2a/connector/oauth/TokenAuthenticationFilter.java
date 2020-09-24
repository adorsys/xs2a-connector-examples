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

import de.adorsys.psd2.aspsp.profile.domain.AspspSettings;
import de.adorsys.psd2.aspsp.profile.service.AspspProfileService;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.profile.ScaApproach;
import de.adorsys.psd2.xs2a.core.profile.ScaRedirectFlow;
import de.adorsys.psd2.xs2a.web.Xs2aEndpointChecker;
import de.adorsys.psd2.xs2a.web.error.TppErrorMessageWriter;
import de.adorsys.psd2.xs2a.web.filter.AbstractXs2aFilter;
import de.adorsys.psd2.xs2a.web.filter.TppErrorMessage;
import de.adorsys.psd2.xs2a.web.request.RequestPathResolver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static de.adorsys.psd2.xs2a.core.domain.MessageCategory.ERROR;
import static de.adorsys.psd2.xs2a.core.error.MessageErrorCode.UNAUTHORIZED_NO_TOKEN;

@Slf4j
@Component
public class TokenAuthenticationFilter extends AbstractXs2aFilter {
    private static final String BEARER_TOKEN_PREFIX = "Bearer ";
    private static final String INSTANCE_ID = "instance-id";
    private static final String CONSENT_ENP_ENDING = "consents";
    private static final String FUNDS_CONF_ENP_ENDING = "funds-confirmations";

    private final TokenValidationService tokenValidationService;
    private final AspspProfileService aspspProfileService;
    private final TppErrorMessageWriter tppErrorMessageWriter;
    private final OauthDataHolder oauthDataHolder;
    private final RequestPathResolver requestPathResolver;

    public TokenAuthenticationFilter(TokenValidationService tokenValidationService,
                                     Xs2aEndpointChecker xs2aEndpointChecker,
                                     AspspProfileService aspspProfileService,
                                     TppErrorMessageWriter tppErrorMessageWriter,
                                     OauthDataHolder oauthDataHolder, RequestPathResolver requestPathResolver) {
        super(tppErrorMessageWriter, xs2aEndpointChecker);
        this.tokenValidationService = tokenValidationService;
        this.aspspProfileService = aspspProfileService;
        this.tppErrorMessageWriter = tppErrorMessageWriter;
        this.oauthDataHolder = oauthDataHolder;
        this.requestPathResolver = requestPathResolver;
    }

    @Override
    protected void doFilterInternalCustom(HttpServletRequest request, @NotNull HttpServletResponse response,
                                          @NotNull FilterChain chain) throws IOException, ServletException {
        String instanceId = request.getHeader(INSTANCE_ID);
        List<ScaApproach> scaApproaches = aspspProfileService.getScaApproaches(instanceId);
        AspspSettings aspspSettings = aspspProfileService.getAspspSettings(instanceId);
        ScaRedirectFlow scaRedirectFlow = aspspSettings.getCommon().getScaRedirectFlow();

        if (!isOAuthRequest(scaApproaches, scaRedirectFlow)) {
            chain.doFilter(request, response);
            return;
        }

        String bearerToken = getBearerToken(request);
        if (isInvalidOauthRequest(request, response, bearerToken, instanceId, aspspSettings)) {
            return;
        }
        oauthDataHolder.setToken(bearerToken);

        chain.doFilter(request, response);
    }

    private boolean isInvalidOauthRequest(HttpServletRequest request, @NotNull HttpServletResponse response,
                                          String bearerToken, String instanceId, AspspSettings aspspSettings) throws IOException {
        ScaRedirectFlow scaRedirectFlow = aspspSettings.getCommon().getScaRedirectFlow();
        String requestPath = requestPathResolver.resolveRequestPath(request);

        boolean tokenRequired = isTokenRequired(scaRedirectFlow, requestPath, instanceId);
        if (tokenRequired && StringUtils.isBlank(bearerToken)) {
            log.info("Token authentication error: token is absent in redirect OAuth pre-step.");
            String oauthConfigurationUrl = aspspSettings.getCommon().getOauthConfigurationUrl();
            tppErrorMessageWriter.writeError(response, buildTppErrorMessage(UNAUTHORIZED_NO_TOKEN, oauthConfigurationUrl));
            return true;
        }

        if (tokenRequired && isTokenInvalid(bearerToken)) {
            log.info("Token authentication error: token is invalid");
            tppErrorMessageWriter.writeError(response, buildTppErrorMessage(MessageErrorCode.TOKEN_INVALID));
            return true;
        }
        return false;
    }

    private boolean isOAuthRequest(List<ScaApproach> scaApproaches, ScaRedirectFlow scaRedirectFlow) {
        return scaApproaches.contains(ScaApproach.REDIRECT)
                       && EnumSet.of(ScaRedirectFlow.OAUTH_PRE_STEP, ScaRedirectFlow.OAUTH).contains(scaRedirectFlow);
    }

    private String getBearerToken(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION))
                       .filter(StringUtils::isNotBlank)
                       .filter(t -> StringUtils.startsWithIgnoreCase(t, BEARER_TOKEN_PREFIX))
                       .map(t -> StringUtils.substringAfter(t, BEARER_TOKEN_PREFIX))
                       .orElse(null);
    }

    private boolean isTokenInvalid(String bearerToken) {
        return tokenValidationService.validate(bearerToken) == null;
    }

    private TppErrorMessage buildTppErrorMessage(MessageErrorCode messageErrorCode, Object... params) {
        return new TppErrorMessage(ERROR, messageErrorCode, params);
    }

    private boolean isTokenRequired(ScaRedirectFlow oauthType, String requestPath, String instanceId) {
        if (oauthType == ScaRedirectFlow.OAUTH_PRE_STEP) {
            return true;
        }

        String trimmedRequestPath = trimEndingSlash(requestPath);
        if (trimmedRequestPath.endsWith(CONSENT_ENP_ENDING) || trimmedRequestPath.endsWith(FUNDS_CONF_ENP_ENDING)) {
            return false;
        } else {
            Set<String> supportedProducts = aspspProfileService.getAspspSettings(instanceId).getPis().getSupportedPaymentTypeAndProductMatrix().values().stream()
                                                    .flatMap(Collection::stream).collect(Collectors.toSet());
            return supportedProducts.stream().noneMatch(trimmedRequestPath::endsWith);
        }
    }

    private String trimEndingSlash(String input) {
        String result = input;

        while (StringUtils.endsWith(result, "/")) {
            result = StringUtils.removeEnd(result, "/");
        }

        return result;
    }

}