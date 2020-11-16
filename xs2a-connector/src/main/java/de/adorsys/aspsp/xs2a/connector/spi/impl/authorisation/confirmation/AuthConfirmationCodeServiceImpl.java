/*
 * Copyright 2018-2020 adorsys GmbH & Co KG
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

package de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation.confirmation;

import de.adorsys.aspsp.xs2a.connector.oauth.OauthProfileServiceWrapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionHandler;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.ledgers.middleware.api.domain.sca.AuthConfirmationTO;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.UserMgmtRestClient;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.profile.ScaRedirectFlow;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiCheckConfirmationCodeRequest;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import feign.FeignException;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;

@AllArgsConstructor
public abstract class AuthConfirmationCodeServiceImpl<T> {

    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final FeignExceptionReader feignExceptionReader;
    private final UserMgmtRestClient userMgmtRestClient;
    private final OauthProfileServiceWrapper oauthProfileServiceWrapper;

    public @NotNull SpiResponse<T> checkConfirmationCode(@NotNull SpiCheckConfirmationCodeRequest spiCheckConfirmationCodeRequest,
                                                         @NotNull SpiAspspConsentDataProvider spiAspspConsentDataProvider) {
        try {
            GlobalScaResponseTO sca = consentDataService.response(spiAspspConsentDataProvider.loadAspspConsentData());
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            String confirmationCodeToCheck = isOAuthRedirectFlow() ?
                                                     sca.getAuthConfirmationCode() :
                                                     spiCheckConfirmationCodeRequest.getConfirmationCode();

            ResponseEntity<AuthConfirmationTO> authConfirmationTOResponse =
                    userMgmtRestClient.verifyAuthConfirmationCode(spiCheckConfirmationCodeRequest.getAuthorisationId(), confirmationCodeToCheck);

            return handleAuthConfirmationResponse(authConfirmationTOResponse);
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            return SpiResponse.<T>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PSU_CREDENTIALS_INVALID, devMessage))
                           .build();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    public SpiResponse<T> completeAuthConfirmation(boolean authCodeConfirmed,
                                                   @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        GlobalScaResponseTO sca = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData());
        authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());
        try {
            ResponseEntity<AuthConfirmationTO> authConfirmationTOResponse = userMgmtRestClient.completeAuthConfirmation(sca.getAuthorisationId(), authCodeConfirmed);

            return handleAuthConfirmationResponse(authConfirmationTOResponse);
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            return SpiResponse.<T>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PSU_CREDENTIALS_INVALID, devMessage))
                           .build();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    public boolean checkConfirmationCodeInternally(String authorisationId, String confirmationCode, String scaAuthenticationData,
                                                   @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        //todo: (think about) pass `authorisationId` through XS2A notifyConfirmationCodeValidation spi method
        GlobalScaResponseTO sca = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData());
        sca.setAuthorisationId(authorisationId);
        aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(sca));
        String codeToCheck = confirmationCode;
        if (oauthProfileServiceWrapper.getScaRedirectFlow() == ScaRedirectFlow.OAUTH) {
            codeToCheck = sca.getAuthConfirmationCode();
        }
        return StringUtils.equals(codeToCheck, scaAuthenticationData);
    }

    protected boolean isOAuthRedirectFlow() {
        return oauthProfileServiceWrapper.getScaRedirectFlow() == ScaRedirectFlow.OAUTH;
    }

    protected abstract SpiResponse<T> handleAuthConfirmationResponse(ResponseEntity<AuthConfirmationTO> authConfirmationResponse);

}