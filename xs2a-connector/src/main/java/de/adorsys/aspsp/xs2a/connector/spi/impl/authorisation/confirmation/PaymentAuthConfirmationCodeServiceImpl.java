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
import de.adorsys.ledgers.middleware.api.domain.payment.TransactionStatusTO;
import de.adorsys.ledgers.middleware.api.domain.sca.AuthConfirmationTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.UserMgmtRestClient;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentConfirmationCodeValidationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PaymentAuthConfirmationCodeServiceImpl extends AuthConfirmationCodeServiceImpl<SpiPaymentConfirmationCodeValidationResponse>
        implements PaymentAuthConfirmationCodeService {

    public PaymentAuthConfirmationCodeServiceImpl(AuthRequestInterceptor authRequestInterceptor, AspspConsentDataService consentDataService,
                                                  FeignExceptionReader feignExceptionReader, UserMgmtRestClient userMgmtRestClient,
                                                  OauthProfileServiceWrapper oauthProfileServiceWrapper) {
        super(authRequestInterceptor, consentDataService, feignExceptionReader, userMgmtRestClient, oauthProfileServiceWrapper);
    }

    @Override
    protected SpiResponse<SpiPaymentConfirmationCodeValidationResponse> handleAuthConfirmationResponse(ResponseEntity<AuthConfirmationTO> authConfirmationResponse) {
        AuthConfirmationTO authConfirmationTO = authConfirmationResponse.getBody();

        if (authConfirmationTO == null || !authConfirmationTO.isSuccess()) {
            // No response in payload from ASPSP or confirmation code verification failed at ASPSP side.
            return buildFailedConfirmationCodeResponse();
        }

        if (authConfirmationTO.isPartiallyAuthorised()) {
            // This authorisation is finished, but others are left.
            return getConfirmationCodeResponseForXs2a(ScaStatus.FINALISED, TransactionStatus.PATC);
        }

        Optional<TransactionStatus> xs2aTransactionStatus = Optional.ofNullable(authConfirmationTO.getTransactionStatus())
                                                                    .map(TransactionStatusTO::getName)
                                                                    .map(TransactionStatus::getByValue);
        return xs2aTransactionStatus
                       .map(transactionStatus -> getConfirmationCodeResponseForXs2a(ScaStatus.FINALISED, transactionStatus))
                       .orElse(buildFailedConfirmationCodeResponse());
    }

    private SpiResponse<SpiPaymentConfirmationCodeValidationResponse> getConfirmationCodeResponseForXs2a(ScaStatus scaStatus, TransactionStatus transactionStatus) {
        SpiPaymentConfirmationCodeValidationResponse response = new SpiPaymentConfirmationCodeValidationResponse(scaStatus, transactionStatus);

        return SpiResponse.<SpiPaymentConfirmationCodeValidationResponse>builder()
                       .payload(response)
                       .build();
    }

    private SpiResponse<SpiPaymentConfirmationCodeValidationResponse> buildFailedConfirmationCodeResponse() {
        return getConfirmationCodeResponseForXs2a(ScaStatus.FAILED, TransactionStatus.RJCT);
    }
}
