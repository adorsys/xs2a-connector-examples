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

import de.adorsys.ledgers.middleware.api.domain.payment.TransactionStatusTO;
import de.adorsys.ledgers.middleware.api.domain.sca.AuthConfirmationTO;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentConfirmationCodeValidationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class PaymentAuthConfirmationCodeServiceImplTest {
    @InjectMocks
    private PaymentAuthConfirmationCodeServiceImpl authConfirmationCodeService;

    @Test
    void handleAuthConfirmationResponse_responseBodyIsFalse() {
        AuthConfirmationTO authConfirmation = new AuthConfirmationTO();
        authConfirmation.setSuccess(false);

        SpiResponse<SpiPaymentConfirmationCodeValidationResponse> actual =
                authConfirmationCodeService.handleAuthConfirmationResponse(ResponseEntity.ok(authConfirmation));

        assertEquals(ScaStatus.FAILED, actual.getPayload().getScaStatus());
        assertEquals(TransactionStatus.RJCT, actual.getPayload().getTransactionStatus());
    }

    @Test
    void handleAuthConfirmationResponse_partiallyAuthorised() {
        AuthConfirmationTO authConfirmation = new AuthConfirmationTO();
        authConfirmation.setSuccess(true);
        authConfirmation.setPartiallyAuthorised(true);

        SpiResponse<SpiPaymentConfirmationCodeValidationResponse> actual =
                authConfirmationCodeService.handleAuthConfirmationResponse(ResponseEntity.ok(authConfirmation));

        assertEquals(ScaStatus.FINALISED, actual.getPayload().getScaStatus());
        assertEquals(TransactionStatus.PATC, actual.getPayload().getTransactionStatus());
    }

    @Test
    void handleAuthConfirmationResponse_success() {
        AuthConfirmationTO authConfirmation = new AuthConfirmationTO();
        authConfirmation.setSuccess(true);
        authConfirmation.setPartiallyAuthorised(false);
        authConfirmation.setTransactionStatus(TransactionStatusTO.ACSP);

        SpiResponse<SpiPaymentConfirmationCodeValidationResponse> actual =
                authConfirmationCodeService.handleAuthConfirmationResponse(ResponseEntity.ok(authConfirmation));

        assertEquals(ScaStatus.FINALISED, actual.getPayload().getScaStatus());
        assertEquals(TransactionStatus.ACSP, actual.getPayload().getTransactionStatus());
    }
}