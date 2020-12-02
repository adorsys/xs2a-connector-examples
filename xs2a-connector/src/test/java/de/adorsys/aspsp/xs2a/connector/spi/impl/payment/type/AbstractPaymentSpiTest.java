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

package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.type;

import de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation.confirmation.PaymentAuthConfirmationCodeService;
import de.adorsys.aspsp.xs2a.util.TestSpiDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiCheckConfirmationCodeRequest;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentConfirmationCodeValidationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class})
class AbstractPaymentSpiTest {

    private static final SpiContextData SPI_CONTEXT_DATA = TestSpiDataProvider.getSpiContextData();
    private static final String AUTHORISATION_ID = "authorisation id";
    private static final String CONFIRMATION_CODE = "12345";
    private static final String SCA_AUTHENTICATION_DATA = "54321";

    @InjectMocks
    private SinglePaymentSpiImpl singlePaymentSpi;

    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;
    @Mock
    private PaymentAuthConfirmationCodeService paymentAuthConfirmationCodeService;

    @Test
    void checkConfirmationCode() {
        SpiCheckConfirmationCodeRequest request = new SpiCheckConfirmationCodeRequest(CONFIRMATION_CODE, AUTHORISATION_ID);

        when(paymentAuthConfirmationCodeService.checkConfirmationCode(request, spiAspspConsentDataProvider)).thenReturn(SpiResponse.<SpiPaymentConfirmationCodeValidationResponse>builder().build());

        singlePaymentSpi.checkConfirmationCode(SPI_CONTEXT_DATA, request, spiAspspConsentDataProvider);

        verify(paymentAuthConfirmationCodeService, times(1)).checkConfirmationCode(request, spiAspspConsentDataProvider);
    }

    @Test
    void notifyConfirmationCodeValidation() {
        when(paymentAuthConfirmationCodeService.completeAuthConfirmation(true, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPaymentConfirmationCodeValidationResponse>builder().build());

        singlePaymentSpi.notifyConfirmationCodeValidation(SPI_CONTEXT_DATA, true, new SpiSinglePayment("payments"), false, spiAspspConsentDataProvider);

        verify(paymentAuthConfirmationCodeService, times(1)).completeAuthConfirmation(true, spiAspspConsentDataProvider);
    }

    @Test
    void checkConfirmationCodeInternally() {
        when(paymentAuthConfirmationCodeService.checkConfirmationCodeInternally(AUTHORISATION_ID, CONFIRMATION_CODE, SCA_AUTHENTICATION_DATA, spiAspspConsentDataProvider))
                .thenReturn(true);

        singlePaymentSpi.checkConfirmationCodeInternally(AUTHORISATION_ID, CONFIRMATION_CODE, SCA_AUTHENTICATION_DATA, spiAspspConsentDataProvider);
        verify(paymentAuthConfirmationCodeService).checkConfirmationCodeInternally(AUTHORISATION_ID, CONFIRMATION_CODE, SCA_AUTHENTICATION_DATA, spiAspspConsentDataProvider);
    }
}