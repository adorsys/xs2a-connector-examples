/*
 * Copyright 2018-2023 adorsys GmbH & Co KG
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

package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapper;
import de.adorsys.ledgers.keycloak.client.api.KeycloakTokenService;
import de.adorsys.ledgers.middleware.api.domain.account.FundsConfirmationRequestTO;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.rest.client.AccountRestClient;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.error.SpiMessageErrorCode;
import de.adorsys.psd2.xs2a.spi.domain.fund.SpiFundsConfirmationRequest;
import de.adorsys.psd2.xs2a.spi.domain.fund.SpiFundsConfirmationResponse;
import de.adorsys.psd2.xs2a.spi.domain.piis.SpiPiisConsent;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.FundsConfirmationSpi;
import feign.FeignException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class FundsConfirmationSpiImpl implements FundsConfirmationSpi {
    private static final Logger logger = LoggerFactory.getLogger(FundsConfirmationSpiImpl.class);

    private final AccountRestClient restClient;
    private final LedgersSpiAccountMapper accountMapper;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final KeycloakTokenService keycloakTokenService;

    @Value("${xs2a.funds-confirmation-user-login:admin}")
    private String fundsConfirmationUserLogin;
    @Value("${xs2a.funds-confirmation-user-password:admin123}")
    private String fundsConfirmationUserPassword;

    public FundsConfirmationSpiImpl(AccountRestClient restClient, LedgersSpiAccountMapper accountMapper,
                                    AuthRequestInterceptor authRequestInterceptor, AspspConsentDataService consentDataService, KeycloakTokenService keycloakTokenService) {
        this.restClient = restClient;
        this.accountMapper = accountMapper;
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
        this.keycloakTokenService = keycloakTokenService;
    }

    @Override
    public @NotNull SpiResponse<SpiFundsConfirmationResponse> performFundsSufficientCheck(@NotNull SpiContextData contextData,
                                                                                          @Nullable SpiPiisConsent piisConsent,
                                                                                          @NotNull SpiFundsConfirmationRequest spiFundsConfirmationRequest,
                                                                                          @Nullable SpiAspspConsentDataProvider aspspConsentDataProvider) {
        byte[] aspspConsentData = piisConsent == null || aspspConsentDataProvider == null
                                          ? null
                                          : aspspConsentDataProvider.loadAspspConsentData();
        try {
            String tokenForAuthorisation;

            // This flow runs in case of 'piisConsentSupported = false' in ASPSP profile. It results in 'piisConsent == null'
            // in this method and access token should be obtained from the separate REST call to ledgers.
            if (aspspConsentData == null) {
                tokenForAuthorisation = getTokenForFundsConfirmationUser();
            } else {
                // This is normal flow when PIIS consent is supported in ASPSP profile.
                GlobalScaResponseTO response = consentDataService.response(aspspConsentData);
                tokenForAuthorisation = response.getBearerToken().getAccess_token();
            }

            authRequestInterceptor.setAccessToken(tokenForAuthorisation);

            logger.info("Funds confirmation request: {}", spiFundsConfirmationRequest);
            FundsConfirmationRequestTO request = accountMapper.toFundsConfirmationTO(contextData.getPsuData(), spiFundsConfirmationRequest);
            Boolean fundsAvailable = restClient.fundsConfirmation(request).getBody();
            logger.info("Funds confirmation response: {}", fundsAvailable);

            SpiFundsConfirmationResponse spiFundsConfirmationResponse = new SpiFundsConfirmationResponse();
            spiFundsConfirmationResponse.setFundsAvailable(Optional.ofNullable(fundsAvailable).orElse(false));

            if (aspspConsentDataProvider != null) {
                aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(consentDataService.response(aspspConsentData)));
            }

            return SpiResponse.<SpiFundsConfirmationResponse>builder()
                           .payload(spiFundsConfirmationResponse)
                           .build();
        } catch (FeignException e) {
            return SpiResponse.<SpiFundsConfirmationResponse>builder()
                           .error(FeignExceptionHandler.getFailureMessage(e, SpiMessageErrorCode.FUNDS_CONFIRMATION_FAILED))
                           .build();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    /**
     * This method authorises the user with the only functionality: check funds confirmation availability. Hardcoded
     * role is used here, credentials are taken from the application.yml.
     *
     * @return access token string.
     */
    private String getTokenForFundsConfirmationUser() {

        BearerTokenTO bearerTokenBO = keycloakTokenService.login(fundsConfirmationUserLogin, fundsConfirmationUserPassword);

        if (bearerTokenBO != null) {
            return bearerTokenBO.getAccess_token();
        }

        return null;
    }
}
