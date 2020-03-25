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

package de.adorsys.aspsp.xs2a.connector.account;

import de.adorsys.aspsp.xs2a.connector.mock.IbanResolverMockService;
import de.adorsys.ledgers.middleware.api.domain.account.AccountIdentifierTypeTO;
import de.adorsys.ledgers.middleware.api.domain.account.AdditionalAccountInformationTO;
import de.adorsys.ledgers.rest.client.AccountRestClient;
import de.adorsys.psd2.xs2a.core.ais.AccountAccessType;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountDetails;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAdditionalInformationAccess;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiCardAccountDetails;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiAccountAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerNameService {
    private final AccountRestClient accountRestClient;
    private final IbanResolverMockService ibanResolverMockService;

    public boolean shouldContainOwnerName(IbanAccountReference ibanAccountReference, SpiAccountAccess accountAccess) {
        SpiAdditionalInformationAccess spiAdditionalInformationAccess = accountAccess.getSpiAdditionalInformationAccess();
        if (spiAdditionalInformationAccess != null && spiAdditionalInformationAccess.getOwnerName() != null) {
            List<SpiAccountReference> ownerName = spiAdditionalInformationAccess.getOwnerName();
            return ownerName.isEmpty() || containsAccountReferenceWithIban(ownerName, ibanAccountReference.getIban(), ibanAccountReference.getCurrency());
        }

        AccountAccessType allAccountsWithOwnerName = AccountAccessType.ALL_ACCOUNTS_WITH_OWNER_NAME;
        List<AccountAccessType> accountAccessTypes = Arrays.asList(accountAccess.getAvailableAccounts(), accountAccess.getAvailableAccountsWithBalance(), accountAccess.getAllPsd2());
        return accountAccessTypes.contains(allAccountsWithOwnerName);
    }

    public SpiAccountDetails enrichAccountDetailsWithOwnerName(SpiAccountDetails accountDetails) {
        String accountOwnerNameFromLedgers = getAccountOwnerNameFromLedgers(accountDetails.getResourceId());
        accountDetails.setOwnerName(accountOwnerNameFromLedgers);
        return accountDetails;
    }

    public SpiCardAccountDetails enrichCardAccountDetailsWithOwnerName(SpiCardAccountDetails cardAccountDetails) {
        String accountOwnerNameFromLedgers = getAccountOwnerNameFromLedgers(cardAccountDetails.getResourceId());
        cardAccountDetails.setOwnerName(accountOwnerNameFromLedgers);
        return cardAccountDetails;
    }

    private boolean containsAccountReferenceWithIban(List<SpiAccountReference> references, @NotNull String iban, Currency currency) {
        return references.stream()
                       .filter(reference -> iban.equals(getIbanFromAccessReference(reference)))
                       .anyMatch(reference -> reference.getCurrency() == null || reference.getCurrency().equals(currency));
    }

    private String getAccountOwnerNameFromLedgers(String resourceId) {
        ResponseEntity<List<AdditionalAccountInformationTO>> additionalAccountInfo = accountRestClient.getAdditionalAccountInfo(AccountIdentifierTypeTO.ACCOUNT_ID, resourceId);

        List<AdditionalAccountInformationTO> additionalAccountInformationList = additionalAccountInfo.getBody();
        if (CollectionUtils.isEmpty(additionalAccountInformationList)) {
            return null;
        }

        return additionalAccountInformationList.stream()
                       .map(AdditionalAccountInformationTO::getAccountOwnerName)
                       .collect(Collectors.joining(", "));
    }

    private String getIbanFromAccessReference(SpiAccountReference reference) {
        String iban = reference.getIban();

        return iban != null
                       ? iban
                       : ibanResolverMockService.handleIbanByAccountReference(reference);
    }
}
