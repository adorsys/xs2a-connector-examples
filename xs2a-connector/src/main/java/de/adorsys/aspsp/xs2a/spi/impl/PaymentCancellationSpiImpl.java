/*
 * Copyright 2018-2018 adorsys GmbH & Co KG
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

package de.adorsys.aspsp.xs2a.spi.impl;

import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentCancellationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.PaymentCancellationSpi;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentCancellationSpiImpl implements PaymentCancellationSpi {
    @Override
    public @NotNull SpiResponse<SpiPaymentCancellationResponse> initiatePaymentCancellation(@NotNull SpiPsuData spiPsuData, @NotNull SpiPayment spiPayment, @NotNull AspspConsentData aspspConsentData) {
        return null;
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> executePaymentCancellationWithoutSca(@NotNull SpiPsuData spiPsuData, @NotNull SpiPayment spiPayment, @NotNull AspspConsentData aspspConsentData) {
        return null;
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> cancelPaymentWithoutSca(@NotNull SpiPsuData spiPsuData, @NotNull SpiPayment spiPayment, @NotNull AspspConsentData aspspConsentData) {
        return null;
    }

    @Override
    public SpiResponse<SpiAuthorisationStatus> authorisePsu(@NotNull SpiPsuData spiPsuData, String s, SpiPayment spiPayment, AspspConsentData aspspConsentData) {
        return null;
    }

    @Override
    public SpiResponse<List<SpiAuthenticationObject>> requestAvailableScaMethods(@NotNull SpiPsuData spiPsuData, SpiPayment spiPayment, AspspConsentData aspspConsentData) {
        return null;
    }

    @Override
    public @NotNull SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(@NotNull SpiPsuData spiPsuData, @NotNull String s, @NotNull SpiPayment spiPayment, @NotNull AspspConsentData aspspConsentData) {
        return null;
    }
}
