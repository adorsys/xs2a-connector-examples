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
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.ledgers.middleware.api.domain.sca.AuthConfirmationTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.UserMgmtRestClient;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiConsentConfirmationCodeValidationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class ConsentAuthConfirmationCodeServiceImpl extends AuthConfirmationCodeServiceImpl<SpiConsentConfirmationCodeValidationResponse>
        implements ConsentAuthConfirmationCodeService {

    public ConsentAuthConfirmationCodeServiceImpl(AuthRequestInterceptor authRequestInterceptor, AspspConsentDataService consentDataService,
                                                  FeignExceptionReader feignExceptionReader, UserMgmtRestClient userMgmtRestClient,
                                                  OauthProfileServiceWrapper oauthProfileServiceWrapper) {
        super(authRequestInterceptor, consentDataService, feignExceptionReader, userMgmtRestClient, oauthProfileServiceWrapper);
    }

    @Override
    protected SpiResponse<SpiConsentConfirmationCodeValidationResponse> handleAuthConfirmationResponse(ResponseEntity<AuthConfirmationTO> authConfirmationResponse) {
        AuthConfirmationTO authConfirmationTO = authConfirmationResponse.getBody();

        if (authConfirmationTO == null || !authConfirmationTO.isSuccess()) {
            // No response in payload from ASPSP or confirmation code verification failed at ASPSP side.
            return getConfirmationCodeResponseForXs2a(ScaStatus.FAILED, ConsentStatus.REJECTED);
        }

        if (authConfirmationTO.isPartiallyAuthorised()) {
            // This authorisation is finished, but others are left.
            return getConfirmationCodeResponseForXs2a(ScaStatus.FINALISED, ConsentStatus.PARTIALLY_AUTHORISED);
        }

        // Authorisation is finalised and consent becomes valid.
        return getConfirmationCodeResponseForXs2a(ScaStatus.FINALISED, ConsentStatus.VALID);
    }

    private SpiResponse<SpiConsentConfirmationCodeValidationResponse> getConfirmationCodeResponseForXs2a(ScaStatus scaStatus, ConsentStatus consentStatus) {
        SpiConsentConfirmationCodeValidationResponse response = new SpiConsentConfirmationCodeValidationResponse(scaStatus, consentStatus);

        return SpiResponse.<SpiConsentConfirmationCodeValidationResponse>builder()
                       .payload(response)
                       .build();
    }
}
