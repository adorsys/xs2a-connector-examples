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

package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.aspsp.xs2a.connector.account.IbanAccountReference;
import de.adorsys.aspsp.xs2a.connector.account.OwnerNameService;
import de.adorsys.aspsp.xs2a.connector.mock.IbanResolverMockService;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapperImpl;
import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.aspsp.xs2a.util.TestSpiDataProvider;
import de.adorsys.ledgers.middleware.api.domain.account.AccountDetailsTO;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.middleware.api.domain.um.AccessTokenTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.rest.client.AccountRestClient;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.psd2.xs2a.core.ais.BookingStatus;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.*;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiAccountAccess;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.quality.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
class CardAccountSpiImplTest {

    private static final String CONSENT_ID = "c966f143-f6a2-41db-9036-8abaeeef3af7";
    private static final byte[] BYTES = "data".getBytes();

    private static final SpiContextData SPI_CONTEXT_DATA = TestSpiDataProvider.getSpiContextData();
    private static final AspspConsentData ASPSP_CONSENT_DATA = new AspspConsentData(BYTES, CONSENT_ID);
    private static final String RESOURCE_ID = "11111-999999999";

    private final static LocalDate DATE_FROM = LocalDate.of(2019, 1, 1);
    private final static LocalDate DATE_TO = LocalDate.of(2020, 1, 1);

    private static final String RESPONSE_STATUS_200_WITH_EMPTY_BODY = "Response status was 200, but the body was empty!";

    private static final String ACCOUNT_OWNER_NAME = "account owner name";
    private static final String IBAN_FIRST_ACCOUNT = "DE89370400440532013000";
    private static final String IBAN_SECOND_ACCOUNT = "DE32760700240271232100";
    private static final String MASKED_PAN_FIRST_ACCOUNT = "493702******0836";
    private static final String MASKED_PAN_SECOND_ACCOUNT = "525412******3241";
    private static final Currency CURRENCY_EUR = Currency.getInstance("EUR");

    @InjectMocks
    private CardAccountSpiImpl cardAccountSpi;

    @Mock
    private AccountRestClient accountRestClient;
    @Spy
    private LedgersSpiAccountMapper accountMapper = new LedgersSpiAccountMapperImpl();
    @Mock
    private AuthRequestInterceptor authRequestInterceptor;
    @Mock
    private AspspConsentDataService tokenService;
    @Mock
    private SpiAspspConsentDataProvider aspspConsentDataProvider;
    @Mock
    private GlobalScaResponseTO scaResponseTO;
    @Mock
    private FeignExceptionReader feignExceptionReader;
    @Mock
    private IbanResolverMockService ibanResolverMockService;
    @Mock
    private OwnerNameService ownerNameService;

    private JsonReader jsonReader = new JsonReader();
    private SpiAccountConsent spiAccountConsent;
    private AccountDetailsTO accountDetailsTO;
    private SpiAccountReference accountReference;

    @BeforeEach
    void setUp() {
        spiAccountConsent = jsonReader.getObjectFromFile("json/spi/impl/card-account/spi-account-consent.json", SpiAccountConsent.class);
        accountDetailsTO = jsonReader.getObjectFromFile("json/spi/impl/account-details.json", AccountDetailsTO.class);
        accountReference = jsonReader.getObjectFromFile("json/spi/impl/account-reference.json", SpiAccountReference.class);

        GlobalScaResponseTO sca = new GlobalScaResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        token.setExpires_in(100);
        token.setAccessTokenObject(new AccessTokenTO());
        token.setRefresh_token("refresh_token");
        token.setAccess_token("access_token");
        sca.setBearerToken(token);
        when(tokenService.response(ASPSP_CONSENT_DATA.getAspspConsentDataBytes())).thenReturn(sca);
        when(aspspConsentDataProvider.loadAspspConsentData()).thenReturn(BYTES);
    }

    @Test
    void requestCardTransactionsForAccount_success() {
        // Given
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);
        when(accountRestClient.getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO))
                .thenReturn(ResponseEntity.ok(Collections.emptyList()));
        when(accountRestClient.getBalances(RESOURCE_ID))
                .thenReturn(ResponseEntity.ok(Collections.emptyList()));

        // When
        SpiResponse<SpiCardTransactionReport> actualResponse = cardAccountSpi.requestCardTransactionsForAccount(SPI_CONTEXT_DATA, buildSpiTransactionReportParameters(MediaType.APPLICATION_XML_VALUE),
                                                                                                                accountReference, spiAccountConsent, aspspConsentDataProvider);
        // Then
        verify(accountRestClient, times(1)).getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO);
        verify(accountRestClient, times(1)).getBalances(RESOURCE_ID);
        verify(tokenService, times(2)).response(ASPSP_CONSENT_DATA.getAspspConsentDataBytes());
        verify(authRequestInterceptor, times(2)).setAccessToken("access_token");
        verify(authRequestInterceptor, times(2)).setAccessToken(null);

        assertEquals(MediaType.APPLICATION_XML_VALUE, actualResponse.getPayload().getResponseContentType());
    }

    @Test
    void requestCardTransactionsForAccount_useDefaultAcceptTypeWhenWildcard_success() {
        // Given
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);
        when(accountRestClient.getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO))
                .thenReturn(ResponseEntity.ok(Collections.emptyList()));
        when(accountRestClient.getBalances(RESOURCE_ID))
                .thenReturn(ResponseEntity.ok(Collections.emptyList()));

        // When
        SpiResponse<SpiCardTransactionReport> actualResponse = cardAccountSpi.requestCardTransactionsForAccount(SPI_CONTEXT_DATA, buildSpiTransactionReportParameters("*/*"),
                                                                                                                accountReference, spiAccountConsent, aspspConsentDataProvider);
        // Then
        verify(accountRestClient, times(1)).getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO);
        verify(accountRestClient, times(1)).getBalances(RESOURCE_ID);
        verify(tokenService, times(2)).response(ASPSP_CONSENT_DATA.getAspspConsentDataBytes());
        verify(authRequestInterceptor, times(2)).setAccessToken("access_token");
        verify(authRequestInterceptor, times(2)).setAccessToken(null);

        assertEquals(MediaType.APPLICATION_JSON_VALUE, actualResponse.getPayload().getResponseContentType());
    }

    @Test
    void requestCardTransactionsForAccount_withException() {
        // Given
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);
        when(accountRestClient.getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO))
                .thenReturn(ResponseEntity.ok(Collections.emptyList()));
        when(accountRestClient.getBalances(RESOURCE_ID))
                .thenReturn(ResponseEntity.ok(Collections.emptyList()));

        when(tokenService.store(scaResponseTO)).thenThrow(getFeignException());

        // When
        SpiResponse<SpiCardTransactionReport> actualResponse = cardAccountSpi.requestCardTransactionsForAccount(SPI_CONTEXT_DATA, buildSpiTransactionReportParameters(MediaType.APPLICATION_XML_VALUE),
                                                                                                                accountReference, spiAccountConsent, aspspConsentDataProvider);
        // Then
        assertFalse(actualResponse.getErrors().isEmpty());
        assertNull(actualResponse.getPayload());
        verify(aspspConsentDataProvider, times(2)).loadAspspConsentData();
        verify(tokenService, times(2)).response(BYTES);
        verify(authRequestInterceptor, times(2)).setAccessToken(scaResponseTO.getBearerToken().getAccess_token());
        verify(authRequestInterceptor, times(2)).setAccessToken(null);
        verify(accountRestClient, times(1)).getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO);
        verify(tokenService, times(1)).store(scaResponseTO);
    }

    @Test
    void requestCardAccountList_withoutBalance_regularConsent_ok() {
        // Given
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);

        AccountDetailsTO accountDetails_1 = jsonReader.getObjectFromFile("json/spi/impl/account-details.json", AccountDetailsTO.class);
        AccountDetailsTO accountDetails_2 = jsonReader.getObjectFromFile("json/spi/impl/account-details.json", AccountDetailsTO.class);
        accountDetails_2.setCurrency(Currency.getInstance("USD"));
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.ok(Arrays.asList(accountDetails_1, accountDetails_2)));
        SpiAccountReference cardAccountReference = jsonReader.getObjectFromFile("json/spi/impl/card-account/account-reference.json", SpiAccountReference.class);
        when(ibanResolverMockService.handleIbanByAccountReference(cardAccountReference)).thenReturn(IBAN_FIRST_ACCOUNT);

        // When
        SpiResponse<List<SpiCardAccountDetails>> actualResponse = cardAccountSpi.requestCardAccountList(SPI_CONTEXT_DATA, spiAccountConsent, aspspConsentDataProvider);

        // Then
        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verifyGetListOfAccounts();
    }

    @Test
    void requestCardAccountList_withoutBalanceAndException_regularConsent_ok() {
        // Given
        when(tokenService.response(BYTES)).thenThrow(getFeignException());

        // When
        SpiResponse<List<SpiCardAccountDetails>> actualResponse = cardAccountSpi.requestCardAccountList(SPI_CONTEXT_DATA,
                                                                                                        spiAccountConsent, aspspConsentDataProvider);
        // Then
        assertFalse(actualResponse.getErrors().isEmpty());
        assertNull(actualResponse.getPayload());
        verify(aspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(tokenService, times(1)).response(BYTES);
        verify(authRequestInterceptor, times(1)).setAccessToken(null);
    }

    @Test
    void requestCardAccountList_regularConsent_noCurrency_ok() {
        // Given
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);

        AccountDetailsTO accountDetails_1 = jsonReader.getObjectFromFile("json/spi/impl/account-details.json", AccountDetailsTO.class);
        AccountDetailsTO accountDetails_2 = jsonReader.getObjectFromFile("json/spi/impl/account-details.json", AccountDetailsTO.class);
        accountDetails_2.setCurrency(Currency.getInstance("USD"));
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.ok(Arrays.asList(accountDetails_1, accountDetails_2)));


        spiAccountConsent = jsonReader.getObjectFromFile("json/spi/impl/spi-account-consent-no-currency.json", SpiAccountConsent.class);

        // When
        SpiResponse<List<SpiCardAccountDetails>> actualResponse = cardAccountSpi.requestCardAccountList(SPI_CONTEXT_DATA,
                                                                                                        spiAccountConsent, aspspConsentDataProvider);
        // Then
        assertTrue(actualResponse.getErrors().isEmpty());
        List<SpiCardAccountDetails> spiAccountDetails = actualResponse.getPayload();
        assertNotNull(spiAccountDetails);
        assertEquals(2, spiAccountDetails.size());
        verifyGetListOfAccounts();
    }

    @Test
    void requestCardAccountList_additionalInformationOwnerName_withOwnerName() {
        // Given
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);

        AccountDetailsTO accountDetailsFirst = jsonReader.getObjectFromFile("json/spi/impl/card-account/account-details-first.json", AccountDetailsTO.class);
        AccountDetailsTO accountDetailsSecond = jsonReader.getObjectFromFile("json/spi/impl/card-account/account-details-second.json", AccountDetailsTO.class);
        SpiCardAccountDetails cardAccountDetailsFirstAccount = jsonReader.getObjectFromFile("json/spi/impl/card-account/spi-card-account-details-first.json", SpiCardAccountDetails.class);
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.ok(Arrays.asList(accountDetailsFirst, accountDetailsSecond)));
        SpiAccountAccess accountAccess = spiAccountConsent.getAccess();
        SpiAccountReference cardAccountReference = jsonReader.getObjectFromFile("json/spi/impl/card-account/account-reference.json", SpiAccountReference.class);
        when(ibanResolverMockService.handleIbanByAccountReference(cardAccountReference)).thenReturn(IBAN_FIRST_ACCOUNT);
        when(ibanResolverMockService.getMaskedPanByIban(IBAN_FIRST_ACCOUNT)).thenReturn(MASKED_PAN_FIRST_ACCOUNT);
        when(ibanResolverMockService.getIbanByMaskedPan(MASKED_PAN_FIRST_ACCOUNT)).thenReturn(Optional.of(IBAN_FIRST_ACCOUNT));
        when(ownerNameService.shouldContainOwnerName(new IbanAccountReference(IBAN_FIRST_ACCOUNT, CURRENCY_EUR), accountAccess)).thenReturn(true);
        when(ownerNameService.enrichCardAccountDetailsWithOwnerName(cardAccountDetailsFirstAccount))
                .thenReturn(jsonReader.getObjectFromFile("json/spi/impl/card-account/spi-card-account-details-first-owner-name.json", SpiCardAccountDetails.class));

        // When
        SpiResponse<List<SpiCardAccountDetails>> actualResponse = cardAccountSpi.requestCardAccountList(SPI_CONTEXT_DATA, spiAccountConsent, aspspConsentDataProvider);

        // Then
        assertTrue(actualResponse.isSuccessful());
        List<SpiCardAccountDetails> actualPayload = actualResponse.getPayload();
        assertNotNull(actualPayload);
        assertEquals(1, actualPayload.size());
        assertEquals(ACCOUNT_OWNER_NAME, actualPayload.get(0).getOwnerName());
        verifyGetListOfAccounts();
    }

    @Test
    void requestCardAccountList_additionalInformationOwnerName_withOwnerNameForOneAccounts() {
        // Given
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);

        AccountDetailsTO accountDetailsFirst = jsonReader.getObjectFromFile("json/spi/impl/card-account/account-details-first.json", AccountDetailsTO.class);
        AccountDetailsTO accountDetailsSecond = jsonReader.getObjectFromFile("json/spi/impl/card-account/account-details-second.json", AccountDetailsTO.class);
        SpiCardAccountDetails cardAccountDetailsFirstAccount = jsonReader.getObjectFromFile("json/spi/impl/card-account/spi-card-account-details-first.json", SpiCardAccountDetails.class);
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.ok(Arrays.asList(accountDetailsFirst, accountDetailsSecond)));
        SpiAccountConsent accountConsentWithTwoAccounts = jsonReader.getObjectFromFile("json/spi/impl/card-account/spi-account-consent-two-accounts.json", SpiAccountConsent.class);
        SpiAccountAccess accountAccess = accountConsentWithTwoAccounts.getAccess();
        SpiAccountReference cardAccountReference = jsonReader.getObjectFromFile("json/spi/impl/card-account/account-reference.json", SpiAccountReference.class);
        when(ibanResolverMockService.handleIbanByAccountReference(cardAccountReference)).thenReturn(IBAN_FIRST_ACCOUNT);
        SpiAccountReference cardAccountReferenceSecondAccount = jsonReader.getObjectFromFile("json/spi/impl/card-account/account-reference-second.json", SpiAccountReference.class);
        when(ibanResolverMockService.handleIbanByAccountReference(cardAccountReferenceSecondAccount)).thenReturn(IBAN_SECOND_ACCOUNT);
        when(ibanResolverMockService.getMaskedPanByIban(IBAN_FIRST_ACCOUNT)).thenReturn(MASKED_PAN_FIRST_ACCOUNT);
        when(ibanResolverMockService.getIbanByMaskedPan(MASKED_PAN_FIRST_ACCOUNT)).thenReturn(Optional.of(IBAN_FIRST_ACCOUNT));
        when(ownerNameService.shouldContainOwnerName(new IbanAccountReference(IBAN_FIRST_ACCOUNT, CURRENCY_EUR), accountAccess)).thenReturn(true);
        when(ownerNameService.enrichCardAccountDetailsWithOwnerName(cardAccountDetailsFirstAccount))
                .thenReturn(jsonReader.getObjectFromFile("json/spi/impl/card-account/spi-card-account-details-first-owner-name.json", SpiCardAccountDetails.class));

        // When
        SpiResponse<List<SpiCardAccountDetails>> actualResponse = cardAccountSpi.requestCardAccountList(SPI_CONTEXT_DATA, accountConsentWithTwoAccounts, aspspConsentDataProvider);

        // Then
        assertTrue(actualResponse.isSuccessful());
        List<SpiCardAccountDetails> actualPayload = actualResponse.getPayload();
        assertNotNull(actualPayload);
        assertEquals(2, actualPayload.size());
        assertEquals(ACCOUNT_OWNER_NAME, actualPayload.get(0).getOwnerName());
        assertNull(actualPayload.get(1).getOwnerName());
        verifyGetListOfAccounts();
    }

    @Test
    void requestCardAccountList_additionalInformationOwnerName_ownerNameForFirstAccount_shouldNotSetOwnerNameForSecond() {
        // Given
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);

        AccountDetailsTO accountDetailsFirst = jsonReader.getObjectFromFile("json/spi/impl/card-account/account-details-first.json", AccountDetailsTO.class);
        AccountDetailsTO accountDetailsSecond = jsonReader.getObjectFromFile("json/spi/impl/card-account/account-details-second.json", AccountDetailsTO.class);
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.ok(Arrays.asList(accountDetailsFirst, accountDetailsSecond)));
        SpiAccountReference cardAccountReference = jsonReader.getObjectFromFile("json/spi/impl/card-account/account-reference.json", SpiAccountReference.class);
        when(ibanResolverMockService.handleIbanByAccountReference(cardAccountReference)).thenReturn(IBAN_SECOND_ACCOUNT);
        when(ibanResolverMockService.getMaskedPanByIban(IBAN_SECOND_ACCOUNT)).thenReturn(MASKED_PAN_SECOND_ACCOUNT);
        when(ibanResolverMockService.getIbanByMaskedPan(MASKED_PAN_SECOND_ACCOUNT)).thenReturn(Optional.of(IBAN_SECOND_ACCOUNT));

        // When
        SpiResponse<List<SpiCardAccountDetails>> actualResponse = cardAccountSpi.requestCardAccountList(SPI_CONTEXT_DATA, spiAccountConsent, aspspConsentDataProvider);

        // Then
        assertTrue(actualResponse.isSuccessful());
        List<SpiCardAccountDetails> actualPayload = actualResponse.getPayload();
        assertNotNull(actualPayload);
        assertEquals(1, actualPayload.size());
        assertNull(actualPayload.get(0).getOwnerName());
        verifyGetListOfAccounts();
        verify(ownerNameService, never()).enrichCardAccountDetailsWithOwnerName(any());
    }

    @Test
    void requestCardAccountList_additionalInformationOwnerName_ownerNameForOneAccount_unknownMaskedPan() {
        // Given
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);

        AccountDetailsTO accountDetailsFirst = jsonReader.getObjectFromFile("json/spi/impl/card-account/account-details-first.json", AccountDetailsTO.class);
        AccountDetailsTO accountDetailsSecond = jsonReader.getObjectFromFile("json/spi/impl/card-account/account-details-second.json", AccountDetailsTO.class);
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.ok(Arrays.asList(accountDetailsFirst, accountDetailsSecond)));
        SpiAccountReference cardAccountReference = jsonReader.getObjectFromFile("json/spi/impl/card-account/account-reference.json", SpiAccountReference.class);
        when(ibanResolverMockService.handleIbanByAccountReference(cardAccountReference)).thenReturn(IBAN_SECOND_ACCOUNT);

        // When
        SpiResponse<List<SpiCardAccountDetails>> actualResponse = cardAccountSpi.requestCardAccountList(SPI_CONTEXT_DATA, spiAccountConsent, aspspConsentDataProvider);

        // Then
        assertTrue(actualResponse.isSuccessful());
        List<SpiCardAccountDetails> actualPayload = actualResponse.getPayload();
        assertNotNull(actualPayload);
        assertEquals(1, actualPayload.size());
        assertNull(actualPayload.get(0).getOwnerName());
        verifyGetListOfAccounts();
        verify(ownerNameService, never()).shouldContainOwnerName(any(), any());
        verify(ownerNameService, never()).enrichCardAccountDetailsWithOwnerName(any());
    }

    @Test
    void requestCardAccountDetailForAccount_ok() {
        // Given
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);

        when(accountRestClient.getAccountDetailsById(RESOURCE_ID)).thenReturn(ResponseEntity.ok(accountDetailsTO));

        // When
        SpiResponse<SpiCardAccountDetails> actualResponse = cardAccountSpi.requestCardAccountDetailsForAccount(SPI_CONTEXT_DATA, accountReference,
                                                                                                               spiAccountConsent, aspspConsentDataProvider);
        // Then
        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(aspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(aspspConsentDataProvider, times(1)).updateAspspConsentData(BYTES);
        verifyApplyAuthorisationUsedAndInterceptorWithNull();
        verify(accountRestClient, times(1)).getAccountDetailsById(RESOURCE_ID);
        verify(tokenService, times(1)).store(scaResponseTO);
    }

    @Test
    void requestCardAccountDetailForAccount_feignException() {
        // Given
        FeignException feignException = getFeignException();

        when(accountRestClient.getAccountDetailsById(RESOURCE_ID)).thenThrow(feignException);
        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn("dev message");

        // When
        SpiResponse<SpiCardAccountDetails> actualResponse = cardAccountSpi.requestCardAccountDetailsForAccount(SPI_CONTEXT_DATA, accountReference,
                                                                                                               spiAccountConsent, aspspConsentDataProvider);
        // Then
        assertTrue(actualResponse.hasError());
        assertEquals(MessageErrorCode.CONSENT_UNKNOWN_400, actualResponse.getErrors().get(0).getErrorCode());

        verify(authRequestInterceptor, times(1)).setAccessToken(null);
    }

    @Test
    void requestCardAccountDetailForAccount_withOwnerName() {
        // Given
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);

        when(accountRestClient.getAccountDetailsById(RESOURCE_ID)).thenReturn(ResponseEntity.ok(accountDetailsTO));

        SpiAccountAccess accountAccess = spiAccountConsent.getAccess();
        when(ibanResolverMockService.getMaskedPanByIban(IBAN_FIRST_ACCOUNT)).thenReturn(MASKED_PAN_FIRST_ACCOUNT);
        when(ibanResolverMockService.getIbanByMaskedPan(MASKED_PAN_FIRST_ACCOUNT)).thenReturn(Optional.of(IBAN_FIRST_ACCOUNT));
        when(ownerNameService.shouldContainOwnerName(new IbanAccountReference(IBAN_FIRST_ACCOUNT, CURRENCY_EUR), accountAccess)).thenReturn(true);
        SpiCardAccountDetails cardAccountDetailsFirstAccount = jsonReader.getObjectFromFile("json/spi/impl/card-account/spi-card-account-details-first.json", SpiCardAccountDetails.class);
        when(ownerNameService.enrichCardAccountDetailsWithOwnerName(cardAccountDetailsFirstAccount))
                .thenReturn(jsonReader.getObjectFromFile("json/spi/impl/card-account/spi-card-account-details-first-owner-name.json", SpiCardAccountDetails.class));

        // When
        SpiResponse<SpiCardAccountDetails> actualResponse = cardAccountSpi.requestCardAccountDetailsForAccount(SPI_CONTEXT_DATA, accountReference,
                                                                                                               spiAccountConsent, aspspConsentDataProvider);

        // Then
        assertTrue(actualResponse.getErrors().isEmpty());
        SpiCardAccountDetails actualPayload = actualResponse.getPayload();
        assertNotNull(actualPayload);
        assertEquals(ACCOUNT_OWNER_NAME, actualPayload.getOwnerName());
    }

    @Test
    void requestCardAccountDetailForAccount_withOwnerName_unknownMaskedPan() {
        // Given
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);

        when(accountRestClient.getAccountDetailsById(RESOURCE_ID)).thenReturn(ResponseEntity.ok(accountDetailsTO));

        when(ibanResolverMockService.getMaskedPanByIban(IBAN_FIRST_ACCOUNT)).thenReturn(MASKED_PAN_SECOND_ACCOUNT);
        when(ibanResolverMockService.getIbanByMaskedPan(MASKED_PAN_SECOND_ACCOUNT)).thenReturn(Optional.empty());

        // When
        SpiResponse<SpiCardAccountDetails> actualResponse = cardAccountSpi.requestCardAccountDetailsForAccount(SPI_CONTEXT_DATA, accountReference,
                                                                                                               spiAccountConsent, aspspConsentDataProvider);

        // Then
        assertTrue(actualResponse.getErrors().isEmpty());
        SpiCardAccountDetails actualPayload = actualResponse.getPayload();
        assertNotNull(actualPayload);
        assertNull(actualPayload.getOwnerName());
        verify(ownerNameService, never()).shouldContainOwnerName(any(), any());
        verify(ownerNameService, never()).enrichCardAccountDetailsWithOwnerName(any());
    }

    @Test
    void requestCardAccountDetailForAccount_withOwnerName_withoutEnriching() {
        // Given
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);

        when(accountRestClient.getAccountDetailsById(RESOURCE_ID)).thenReturn(ResponseEntity.ok(accountDetailsTO));

        SpiAccountAccess accountAccess = spiAccountConsent.getAccess();
        when(ibanResolverMockService.getMaskedPanByIban(IBAN_FIRST_ACCOUNT)).thenReturn(MASKED_PAN_FIRST_ACCOUNT);
        when(ibanResolverMockService.getIbanByMaskedPan(MASKED_PAN_FIRST_ACCOUNT)).thenReturn(Optional.of(IBAN_FIRST_ACCOUNT));
        when(ownerNameService.shouldContainOwnerName(new IbanAccountReference(IBAN_FIRST_ACCOUNT, CURRENCY_EUR), accountAccess)).thenReturn(false);

        // When
        SpiResponse<SpiCardAccountDetails> actualResponse = cardAccountSpi.requestCardAccountDetailsForAccount(SPI_CONTEXT_DATA, accountReference,
                                                                                                               spiAccountConsent, aspspConsentDataProvider);

        // Then
        assertTrue(actualResponse.getErrors().isEmpty());
        SpiCardAccountDetails actualPayload = actualResponse.getPayload();
        assertNotNull(actualPayload);
        assertNull(actualPayload.getOwnerName());
        verify(ownerNameService, never()).enrichCardAccountDetailsWithOwnerName(any());
    }

    @Test
    @MockitoSettings(strictness = LENIENT)
    void processAcceptMediaType() {
        assertEquals(MediaType.APPLICATION_JSON_VALUE, cardAccountSpi.processAcceptMediaType(""));
        assertEquals(MediaType.APPLICATION_JSON_VALUE, cardAccountSpi.processAcceptMediaType(null));
        assertEquals(MediaType.APPLICATION_JSON_VALUE, cardAccountSpi.processAcceptMediaType(MediaType.ALL_VALUE));
        assertEquals(MediaType.APPLICATION_JSON_VALUE, cardAccountSpi.processAcceptMediaType("application/xml, application/json"));

        assertEquals(MediaType.APPLICATION_JSON_VALUE, cardAccountSpi.processAcceptMediaType(MediaType.APPLICATION_JSON_VALUE));
        assertEquals(MediaType.APPLICATION_ATOM_XML_VALUE, cardAccountSpi.processAcceptMediaType(MediaType.APPLICATION_ATOM_XML_VALUE));
    }

    private void verifyGetListOfAccounts() {
        verify(accountRestClient, times(1)).getListOfAccounts();
        verify(tokenService, times(2)).response(ASPSP_CONSENT_DATA.getAspspConsentDataBytes());
        verify(authRequestInterceptor, times(2)).setAccessToken("access_token");
        verify(authRequestInterceptor).setAccessToken(null);
    }

    private FeignException getFeignException() {
        return FeignException.errorStatus(RESPONSE_STATUS_200_WITH_EMPTY_BODY, buildErrorResponse());
    }

    private Response buildErrorResponse() {
        return Response.builder()
                       .status(404)
                       .request(Request.create(Request.HttpMethod.GET, "", Collections.emptyMap(), null, Charset.defaultCharset(), null))
                       .headers(Collections.emptyMap())
                       .build();
    }

    private void verifyApplyAuthorisationUsedAndInterceptorWithNull() {
        verify(tokenService, times(1)).response(BYTES);
        verify(authRequestInterceptor, times(1)).setAccessToken(scaResponseTO.getBearerToken().getAccess_token());
        verify(authRequestInterceptor, times(1)).setAccessToken(null);
    }

    private SpiTransactionReportParameters buildSpiTransactionReportParameters(String mediaType) {
        return new SpiTransactionReportParameters(mediaType, true, DATE_FROM, DATE_TO, BookingStatus.BOOKED, null, null, null, null);
    }

}