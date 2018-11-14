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
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.PeriodicPaymentSpi;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class PeriodicPaymentSpiImpl implements PeriodicPaymentSpi {
    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> executePaymentWithoutSca(@NotNull SpiPsuData spiPsuData, @NotNull SpiPeriodicPayment spiPeriodicPayment, @NotNull AspspConsentData aspspConsentData) {
        return null;
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> verifyScaAuthorisationAndExecutePayment(@NotNull SpiPsuData spiPsuData, @NotNull SpiScaConfirmation spiScaConfirmation, @NotNull SpiPeriodicPayment spiPeriodicPayment, @NotNull AspspConsentData aspspConsentData) {
        return null;
    }
}
