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

package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.type;

import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.SpiMockData;
import de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation.confirmation.PaymentAuthConfirmationCodeService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPaymentInfo;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiTransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiGetPaymentStatusResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.CommonPaymentSpi;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.HashSet;

@Service
public class CommonPaymentSpiImpl extends AbstractPaymentSpi<SpiPaymentInfo, SpiPaymentInitiationResponse> implements CommonPaymentSpi {

    @Autowired
    public CommonPaymentSpiImpl(GeneralPaymentService generalPaymentService, AspspConsentDataService consentDataService,
                                PaymentAuthConfirmationCodeService paymentAuthConfirmationCodeService) {
        super(generalPaymentService, consentDataService, paymentAuthConfirmationCodeService);
    }

    @Override
    public @NotNull SpiResponse<SpiPaymentExecutionResponse> verifyScaAuthorisationAndExecutePaymentWithPaymentResponse(@NotNull SpiContextData contextData, @NotNull SpiScaConfirmation spiScaConfirmation, @NotNull SpiPaymentInfo payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return paymentService.verifyScaAuthorisationAndExecutePaymentWithPaymentResponse(spiScaConfirmation, aspspConsentDataProvider);
    }

    @Override
    protected SpiResponse<SpiPaymentInitiationResponse> processEmptyAspspConsentData(@NotNull SpiPaymentInfo payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider, @NotNull SpiPsuData spiPsuData) {
        return paymentService.firstCallInstantiatingPayment(PaymentTypeTO.valueOf(payment.getPaymentType().name()), payment, aspspConsentDataProvider, new SpiSinglePaymentInitiationResponse(), spiPsuData, new HashSet<>());
    }

    @Override
    public @NotNull SpiResponse<SpiPaymentInfo> getPaymentById(@NotNull SpiContextData contextData, @NotNull String acceptMediaType, @NotNull SpiPaymentInfo payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return SpiResponse.<SpiPaymentInfo>builder().payload(payment).build();
    }

    @Override
    public @NotNull SpiResponse<SpiGetPaymentStatusResponse> getPaymentStatusById(@NotNull SpiContextData contextData, @NotNull String acceptMediaType, @NotNull SpiPaymentInfo payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        if (MediaType.APPLICATION_XML_VALUE.equals(acceptMediaType)) {
            return super.getPaymentStatusById(contextData, acceptMediaType, payment, aspspConsentDataProvider);
        }

        return SpiResponse.<SpiGetPaymentStatusResponse>builder()
                       .payload(new SpiGetPaymentStatusResponse(SpiTransactionStatus.ACSP, null, MediaType.APPLICATION_JSON_VALUE, null, SpiMockData.PSU_MESSAGE,
                                                                SpiMockData.SPI_LINKS,
                                                                SpiMockData.TPP_MESSAGES))
                       .build();
    }
}
