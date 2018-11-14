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
import de.adorsys.psd2.xs2a.spi.domain.account.*;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.AccountSpi;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class AccountSpiImpl implements AccountSpi {
    @Override
    public SpiResponse<List<SpiAccountDetails>> requestAccountList(boolean b, @NotNull SpiAccountConsent spiAccountConsent, @NotNull AspspConsentData aspspConsentData) {
        return null;
    }

    @Override
    public SpiResponse<SpiAccountDetails> requestAccountDetailForAccount(boolean b, @NotNull SpiAccountReference spiAccountReference, @NotNull SpiAccountConsent spiAccountConsent, @NotNull AspspConsentData aspspConsentData) {
        return null;
    }

    @Override
    public SpiResponse<SpiTransactionReport> requestTransactionsForAccount(boolean b, @NotNull LocalDate localDate, @NotNull LocalDate localDate1, @NotNull SpiAccountReference spiAccountReference, @NotNull SpiAccountConsent spiAccountConsent, @NotNull AspspConsentData aspspConsentData) {
        return null;
    }

    @Override
    public SpiResponse<SpiTransaction> requestTransactionForAccountByTransactionId(@NotNull String s, @NotNull SpiAccountReference spiAccountReference, @NotNull SpiAccountConsent spiAccountConsent, @NotNull AspspConsentData aspspConsentData) {
        return null;
    }

    @Override
    public SpiResponse<List<SpiAccountBalance>> requestBalancesForAccount(@NotNull SpiAccountReference spiAccountReference, @NotNull SpiAccountConsent spiAccountConsent, @NotNull AspspConsentData aspspConsentData) {
        return null;
    }
}
