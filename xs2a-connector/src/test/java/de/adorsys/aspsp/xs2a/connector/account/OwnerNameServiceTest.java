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
import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.account.AccountIdentifierTypeTO;
import de.adorsys.ledgers.middleware.api.domain.account.AdditionalAccountInformationTO;
import de.adorsys.ledgers.rest.client.AccountRestClient;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountDetails;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiCardAccountDetails;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiAccountAccess;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerNameServiceTest {
    private static final String RESOURCE_ID = "11111-999999999";
    private static final String PAN = "4937023494670836";
    private static final String ACCOUNT_OWNER_NAME = "account owner name";
    private static final String ACCOUNT_OWNER_NAME_2 = "different account owner name";
    private static final String IBAN_FIRST_ACCOUNT = "DE89370400440532013000";
    private static final String IBAN_SECOND_ACCOUNT = "DE32760700240271232100";
    private static final Currency CURRENCY_EUR = Currency.getInstance("EUR");

    @Mock
    private AccountRestClient accountRestClient;
    @Mock
    private IbanResolverMockService ibanResolverMockService;
    @InjectMocks
    private OwnerNameService ownerNameService;

    private JsonReader jsonReader = new JsonReader();

    @Test
    void shouldContainOwnerName_dedicatedAccessWithAllAccountsOwnerName_shouldReturnTrue() {
        IbanAccountReference accountReference = new IbanAccountReference(IBAN_FIRST_ACCOUNT, CURRENCY_EUR);
        SpiAccountAccess accountAccess = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-account-access-dedicated-owner-name-all.json", SpiAccountAccess.class);

        boolean actualResult = ownerNameService.shouldContainOwnerName(accountReference, accountAccess);

        assertTrue(actualResult);
    }

    @Test
    void shouldContainOwnerName__dedicatedAccessWithOneOwnerName_correctIbanInAccountDetails_shouldReturnTrue() {
        IbanAccountReference accountReference = new IbanAccountReference(IBAN_FIRST_ACCOUNT, CURRENCY_EUR);
        SpiAccountAccess accountAccessWithOwnerNameFirstAccount = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-account-access-dedicated-owner-name-first-account.json", SpiAccountAccess.class);

        boolean actualResult = ownerNameService.shouldContainOwnerName(accountReference, accountAccessWithOwnerNameFirstAccount);

        assertTrue(actualResult);
    }

    @Test
    void shouldContainOwnerName_dedicatedAccessWithOneOwnerName_incorrectIban_shouldReturnFalse() {
        IbanAccountReference accountReferenceSecondAccount = new IbanAccountReference(IBAN_SECOND_ACCOUNT, CURRENCY_EUR);
        SpiAccountAccess accountAccessWithOwnerNameFirstAccount = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-account-access-dedicated-owner-name-first-account.json", SpiAccountAccess.class);

        boolean actualResult = ownerNameService.shouldContainOwnerName(accountReferenceSecondAccount, accountAccessWithOwnerNameFirstAccount);

        assertFalse(actualResult);
    }

    @Test
    void shouldContainOwnerName_dedicatedAccessWithOneOwnerName_incorrectCurrency_shouldReturnFalse() {
        IbanAccountReference accountReference = new IbanAccountReference(IBAN_FIRST_ACCOUNT, CURRENCY_EUR);
        SpiAccountAccess accountAccessWithUsdAccount = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-account-access-dedicated-owner-name-first-account-usd.json", SpiAccountAccess.class);

        boolean actualResult = ownerNameService.shouldContainOwnerName(accountReference, accountAccessWithUsdAccount);

        assertFalse(actualResult);
    }

    @Test
    void shouldContainOwnerName_dedicatedAccessWithOneOwnerName_noIbanValue_shouldReturnFalse() {
        IbanAccountReference accountReference = new IbanAccountReference(IBAN_FIRST_ACCOUNT, CURRENCY_EUR);
        SpiAccountAccess accountAccessWithCardAccounts = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-account-access-dedicated-owner-name-bban-first-account.json", SpiAccountAccess.class);

        boolean actualResult = ownerNameService.shouldContainOwnerName(accountReference, accountAccessWithCardAccounts);

        assertFalse(actualResult);
    }

    @Test
    void shouldContainOwnerName_dedicatedAccessWithOneOwnerName_noIbanValueInAccess_mockedIbanExists_shouldReturnTrue() {
        IbanAccountReference accountReference = new IbanAccountReference(IBAN_FIRST_ACCOUNT, CURRENCY_EUR);
        SpiAccountAccess accountAccessWithCardAccounts = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-account-access-dedicated-owner-name-card-first-account.json", SpiAccountAccess.class);
        SpiAccountReference cardAccountReference = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-account-reference-card.json", SpiAccountReference.class);
        when(ibanResolverMockService.handleIbanByAccountReference(cardAccountReference)).thenReturn(IBAN_FIRST_ACCOUNT);

        boolean actualResult = ownerNameService.shouldContainOwnerName(accountReference, accountAccessWithCardAccounts);

        assertTrue(actualResult);
    }

    @Test
    void shouldContainOwnerName_dedicatedAccessWithOneOwnerName_noIbanValueInAccess_noMockedIban_shouldReturnFalse() {
        IbanAccountReference accountReference = new IbanAccountReference(IBAN_FIRST_ACCOUNT, CURRENCY_EUR);
        SpiAccountAccess accountAccessWithCardAccounts = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-account-access-dedicated-owner-name-card-first-account.json", SpiAccountAccess.class);

        boolean actualResult = ownerNameService.shouldContainOwnerName(accountReference, accountAccessWithCardAccounts);

        assertFalse(actualResult);
    }

    @Test
    void shouldContainOwnerName_availableAccountAccessWithOwnerName_shouldReturnTrue() {
        IbanAccountReference accountReference = new IbanAccountReference(IBAN_FIRST_ACCOUNT, CURRENCY_EUR);
        SpiAccountAccess accountAccessAvailableAccountsWithOwnerName = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-account-access-available-accounts-owner-name.json", SpiAccountAccess.class);

        boolean actualResult = ownerNameService.shouldContainOwnerName(accountReference, accountAccessAvailableAccountsWithOwnerName);

        assertTrue(actualResult);
    }

    @Test
    void shouldContainOwnerName_availableAccountAccess_shouldReturnFalse() {
        IbanAccountReference accountReference = new IbanAccountReference(IBAN_FIRST_ACCOUNT, CURRENCY_EUR);
        SpiAccountAccess accountAccessAvailableAccounts = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-account-access-available-accounts.json", SpiAccountAccess.class);

        boolean actualResult = ownerNameService.shouldContainOwnerName(accountReference, accountAccessAvailableAccounts);

        assertFalse(actualResult);
    }

    @Test
    void shouldContainOwnerName_availableAccountWithBalanceAccessWithOwnerName_shouldReturnTrue() {
        IbanAccountReference accountReference = new IbanAccountReference(IBAN_FIRST_ACCOUNT, CURRENCY_EUR);
        SpiAccountAccess accountAccessAvailableAccountsWithBalanceAndOwnerName = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-account-access-available-accounts-balance-owner-name.json", SpiAccountAccess.class);

        boolean actualResult = ownerNameService.shouldContainOwnerName(accountReference, accountAccessAvailableAccountsWithBalanceAndOwnerName);

        assertTrue(actualResult);
    }

    @Test
    void shouldContainOwnerName_availableAccountAccessWithBalance_shouldReturnFalse() {
        IbanAccountReference accountReference = new IbanAccountReference(IBAN_FIRST_ACCOUNT, CURRENCY_EUR);
        SpiAccountAccess accountAccessAvailableAccountsWithBalance = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-account-access-available-accounts-balance.json", SpiAccountAccess.class);

        boolean actualResult = ownerNameService.shouldContainOwnerName(accountReference, accountAccessAvailableAccountsWithBalance);

        assertFalse(actualResult);
    }

    @Test
    void shouldContainOwnerName_globalAccessWithOwnerName_shouldReturnTrue() {
        IbanAccountReference accountReference = new IbanAccountReference(IBAN_FIRST_ACCOUNT, CURRENCY_EUR);
        SpiAccountAccess accountAccessGlobalWithOwnerName = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-account-access-global-owner-name.json", SpiAccountAccess.class);

        boolean actualResult = ownerNameService.shouldContainOwnerName(accountReference, accountAccessGlobalWithOwnerName);

        assertTrue(actualResult);
    }

    @Test
    void shouldContainOwnerName_globalAccess_shouldReturnFalse() {
        IbanAccountReference accountReference = new IbanAccountReference(IBAN_FIRST_ACCOUNT, CURRENCY_EUR);
        SpiAccountAccess accountAccessGlobal = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-account-access-global.json", SpiAccountAccess.class);

        boolean actualResult = ownerNameService.shouldContainOwnerName(accountReference, accountAccessGlobal);

        assertFalse(actualResult);
    }

    @Test
    void enrichAccountDetailsWithOwnerName_shouldSetOwnerName() {
        AdditionalAccountInformationTO additionalAccountInformationTO = buildAdditionalAccountInformationTO(ACCOUNT_OWNER_NAME);
        when(accountRestClient.getAdditionalAccountInfo(AccountIdentifierTypeTO.ACCOUNT_ID, RESOURCE_ID))
                .thenReturn(ResponseEntity.ok(Collections.singletonList(additionalAccountInformationTO)));
        SpiAccountDetails initialAccountDetails = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-account-details-first-account.json", SpiAccountDetails.class);

        SpiAccountDetails enrichedAccountDetails = ownerNameService.enrichAccountDetailsWithOwnerName(initialAccountDetails);

        assertNotNull(enrichedAccountDetails);
        assertNotNull(enrichedAccountDetails.getOwnerName());
        SpiAccountDetails expectedAccountDetailsWithOwnerName = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-account-details-first-account-owner-name.json", SpiAccountDetails.class);
        assertEquals(expectedAccountDetailsWithOwnerName, enrichedAccountDetails);
    }

    @Test
    void enrichAccountDetailsWithOwnerName_multipleOwners_shouldSetJoinedOwnerName() {
        AdditionalAccountInformationTO firstAdditionalInformation = buildAdditionalAccountInformationTO(ACCOUNT_OWNER_NAME);
        AdditionalAccountInformationTO secondAdditionalInformation = buildAdditionalAccountInformationTO(ACCOUNT_OWNER_NAME_2);
        List<AdditionalAccountInformationTO> additionalInformationList = Arrays.asList(firstAdditionalInformation, secondAdditionalInformation);
        when(accountRestClient.getAdditionalAccountInfo(AccountIdentifierTypeTO.ACCOUNT_ID, RESOURCE_ID))
                .thenReturn(ResponseEntity.ok(additionalInformationList));
        SpiAccountDetails initialAccountDetails = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-account-details-first-account.json", SpiAccountDetails.class);

        SpiAccountDetails enrichedAccountDetails = ownerNameService.enrichAccountDetailsWithOwnerName(initialAccountDetails);

        assertNotNull(enrichedAccountDetails);
        String expectedOwnerName = String.format("%s, %s", ACCOUNT_OWNER_NAME, ACCOUNT_OWNER_NAME_2);
        assertEquals(expectedOwnerName, enrichedAccountDetails.getOwnerName());
    }

    @Test
    void enrichAccountDetailsWithOwnerName_nullAdditionalAccountInfoFromLedgers_shouldSetNullOwnerName() {
        when(accountRestClient.getAdditionalAccountInfo(AccountIdentifierTypeTO.ACCOUNT_ID, RESOURCE_ID))
                .thenReturn(ResponseEntity.ok().build());
        SpiAccountDetails initialAccountDetails = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-account-details-first-account.json", SpiAccountDetails.class);

        SpiAccountDetails enrichedAccountDetails = ownerNameService.enrichAccountDetailsWithOwnerName(initialAccountDetails);

        assertNotNull(enrichedAccountDetails);
        assertNull(enrichedAccountDetails.getOwnerName());
        assertEquals(initialAccountDetails, enrichedAccountDetails);
        verify(accountRestClient).getAdditionalAccountInfo(AccountIdentifierTypeTO.ACCOUNT_ID, RESOURCE_ID);
    }

    @Test
    void enrichAccountDetailsWithOwnerName_emptyAdditionalAccountInfoListFromLedgers_shouldSetNullOwnerName() {
        when(accountRestClient.getAdditionalAccountInfo(AccountIdentifierTypeTO.ACCOUNT_ID, RESOURCE_ID))
                .thenReturn(ResponseEntity.ok(Collections.emptyList()));
        SpiAccountDetails initialAccountDetails = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-account-details-first-account.json", SpiAccountDetails.class);

        SpiAccountDetails enrichedAccountDetails = ownerNameService.enrichAccountDetailsWithOwnerName(initialAccountDetails);

        assertNotNull(enrichedAccountDetails);
        assertNull(enrichedAccountDetails.getOwnerName());
        assertEquals(initialAccountDetails, enrichedAccountDetails);
        verify(accountRestClient).getAdditionalAccountInfo(AccountIdentifierTypeTO.ACCOUNT_ID, RESOURCE_ID);
    }

    @Test
    void enrichCardAccountDetailsWithOwnerName_shouldSetOwnerName() {
        AdditionalAccountInformationTO additionalAccountInformationTO = buildAdditionalAccountInformationTO(ACCOUNT_OWNER_NAME);
        when(accountRestClient.getAdditionalAccountInfo(AccountIdentifierTypeTO.ACCOUNT_ID, RESOURCE_ID))
                .thenReturn(ResponseEntity.ok(Collections.singletonList(additionalAccountInformationTO)));
        SpiCardAccountDetails initialAccountDetails = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-card-account-details-first-account.json", SpiCardAccountDetails.class);

        SpiCardAccountDetails enrichedAccountDetails = ownerNameService.enrichCardAccountDetailsWithOwnerName(initialAccountDetails);

        assertNotNull(enrichedAccountDetails);
        assertNotNull(enrichedAccountDetails.getOwnerName());
        SpiCardAccountDetails expectedAccountDetailsWithOwnerName = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-card-account-details-first-account-owner-name.json", SpiCardAccountDetails.class);
        assertEquals(expectedAccountDetailsWithOwnerName, enrichedAccountDetails);
    }

    @Test
    void enrichCardAccountDetailsWithOwnerName_multipleOwners_shouldSetJoinedOwnerName() {
        AdditionalAccountInformationTO firstAdditionalInformation = buildAdditionalAccountInformationTO(ACCOUNT_OWNER_NAME);
        AdditionalAccountInformationTO secondAdditionalInformation = buildAdditionalAccountInformationTO(ACCOUNT_OWNER_NAME_2);
        List<AdditionalAccountInformationTO> additionalInformationList = Arrays.asList(firstAdditionalInformation, secondAdditionalInformation);
        when(accountRestClient.getAdditionalAccountInfo(AccountIdentifierTypeTO.ACCOUNT_ID, RESOURCE_ID))
                .thenReturn(ResponseEntity.ok(additionalInformationList));
        SpiCardAccountDetails initialAccountDetails = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-card-account-details-first-account.json", SpiCardAccountDetails.class);

        SpiCardAccountDetails enrichedAccountDetails = ownerNameService.enrichCardAccountDetailsWithOwnerName(initialAccountDetails);

        assertNotNull(enrichedAccountDetails);
        String expectedOwnerName = String.format("%s, %s", ACCOUNT_OWNER_NAME, ACCOUNT_OWNER_NAME_2);
        assertEquals(expectedOwnerName, enrichedAccountDetails.getOwnerName());
    }

    @Test
    void enrichCardAccountDetailsWithOwnerName_nullAdditionalAccountInfoFromLedgers_shouldSetNullOwnerName() {
        when(accountRestClient.getAdditionalAccountInfo(AccountIdentifierTypeTO.ACCOUNT_ID, RESOURCE_ID))
                .thenReturn(ResponseEntity.ok().build());
        SpiCardAccountDetails initialAccountDetails = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-card-account-details-first-account.json", SpiCardAccountDetails.class);

        SpiCardAccountDetails enrichedAccountDetails = ownerNameService.enrichCardAccountDetailsWithOwnerName(initialAccountDetails);

        assertNotNull(enrichedAccountDetails);
        assertNull(enrichedAccountDetails.getOwnerName());
        assertEquals(initialAccountDetails, enrichedAccountDetails);
        verify(accountRestClient).getAdditionalAccountInfo(AccountIdentifierTypeTO.ACCOUNT_ID, RESOURCE_ID);
    }

    @Test
    void enrichCardAccountDetailsWithOwnerName_emptyAdditionalAccountInfoListFromLedgers_shouldSetNullOwnerName() {
        when(accountRestClient.getAdditionalAccountInfo(AccountIdentifierTypeTO.ACCOUNT_ID, RESOURCE_ID))
                .thenReturn(ResponseEntity.ok(Collections.emptyList()));
        SpiCardAccountDetails initialAccountDetails = jsonReader.getObjectFromFile("json/account/additional-account-information/spi-card-account-details-first-account.json", SpiCardAccountDetails.class);

        SpiCardAccountDetails enrichedAccountDetails = ownerNameService.enrichCardAccountDetailsWithOwnerName(initialAccountDetails);

        assertNotNull(enrichedAccountDetails);
        assertNull(enrichedAccountDetails.getOwnerName());
        assertEquals(initialAccountDetails, enrichedAccountDetails);
        verify(accountRestClient).getAdditionalAccountInfo(AccountIdentifierTypeTO.ACCOUNT_ID, RESOURCE_ID);
    }

    @NotNull
    private AdditionalAccountInformationTO buildAdditionalAccountInformationTO(String ownerName) {
        AdditionalAccountInformationTO additionalAccountInformationTO = new AdditionalAccountInformationTO();
        additionalAccountInformationTO.setAccountOwnerName(ownerName);
        return additionalAccountInformationTO;
    }

    @NotNull
    private SpiAccountReference buildSpiAccountReferenceForCardAccount() {
        return new SpiAccountReference(null, null, null, PAN, null, null, null);
    }
}