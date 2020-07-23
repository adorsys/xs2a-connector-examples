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

import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiCurrencyConversionInfo;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiAmount;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.CurrencyConversionInfoSpi;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Currency;

@Service
public class CurrencyConversionInfoSpiImpl implements CurrencyConversionInfoSpi {
    @NotNull
    @Override
    public SpiResponse<SpiCurrencyConversionInfo> getCurrencyConversionInfo(@NotNull SpiContextData contextData, @NotNull SpiPayment payment, @NotNull String authorisationId, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        // TODO replace mock data with real response from ledgers when it starts support https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/-/issues/1315
        return SpiResponse.<SpiCurrencyConversionInfo>builder()
                       .payload(new SpiCurrencyConversionInfo(
                               new SpiAmount(Currency.getInstance("EUR"), BigDecimal.valueOf(500)),
                               new SpiAmount(Currency.getInstance("EUR"), BigDecimal.valueOf(300)),
                               new SpiAmount(Currency.getInstance("EUR"), BigDecimal.valueOf(900)),
                               new SpiAmount(Currency.getInstance("EUR"), BigDecimal.valueOf(250))
                       ))
                       .build();
    }
}
