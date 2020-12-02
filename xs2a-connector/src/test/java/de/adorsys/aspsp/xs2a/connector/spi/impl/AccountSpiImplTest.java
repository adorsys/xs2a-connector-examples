package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.aspsp.xs2a.connector.account.IbanAccountReference;
import de.adorsys.aspsp.xs2a.connector.account.OwnerNameService;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapperImpl;
import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.aspsp.xs2a.util.TestSpiDataProvider;
import de.adorsys.ledgers.middleware.api.domain.account.AccountDetailsTO;
import de.adorsys.ledgers.middleware.api.domain.account.AdditionalAccountInformationTO;
import de.adorsys.ledgers.middleware.api.domain.account.TransactionTO;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.middleware.api.domain.um.AccessTokenTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.rest.client.AccountRestClient;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.psd2.xs2a.core.ais.BookingStatus;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
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

import javax.validation.constraints.NotNull;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.quality.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
class AccountSpiImplTest {
    private static final String CONSENT_ID = "c966f143-f6a2-41db-9036-8abaeeef3af7";
    private static final byte[] BYTES = "data".getBytes();

    private static final SpiContextData SPI_CONTEXT_DATA = TestSpiDataProvider.getSpiContextData();
    private static final AspspConsentData ASPSP_CONSENT_DATA = new AspspConsentData(BYTES, CONSENT_ID);
    private static final String RESOURCE_ID = "11111-999999999";
    private static final String RESOURCE_ID_SECOND_ACCOUNT = "11111-999999998";

    private final static LocalDate DATE_FROM = LocalDate.of(2019, 1, 1);
    private final static LocalDate DATE_TO = LocalDate.of(2020, 1, 1);

    private static final String RESPONSE_STATUS_200_WITH_EMPTY_BODY = "Response status was 200, but the body was empty!";
    private static final String TRANSACTION_ID = "1234567";
    private static final String DOWNLOAD_ID = "downloadId";
    private static final String ACCOUNT_OWNER_NAME = "account owner name";
    private static final String ACCOUNT_OWNER_NAME_SECOND_ACCOUNT = "account owner name 2";
    private static final String IBAN = "DE89370400440532013000";
    private static final String IBAN_SECOND_ACCOUNT = "DE32760700240271232100";
    private static final Currency CURRENCY_EUR = Currency.getInstance("EUR");

    @InjectMocks
    private AccountSpiImpl accountSpi;

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
    private OwnerNameService ownerNameService;

    private JsonReader jsonReader = new JsonReader();
    private SpiAccountConsent spiAccountConsent;
    private SpiAccountConsent spiAccountConsentWithOwnerName;
    private SpiAccountConsent spiAccountConsentGlobal;
    private AccountDetailsTO accountDetailsTO;
    private SpiAccountReference accountReference;
    private TransactionTO transactionTO;

    @BeforeEach
    void setUp() {
        spiAccountConsent = jsonReader.getObjectFromFile("json/spi/impl/spi-account-consent.json", SpiAccountConsent.class);
        spiAccountConsentWithOwnerName = jsonReader.getObjectFromFile("json/spi/impl/spi-account-consent-with-owner-name.json", SpiAccountConsent.class);
        spiAccountConsentGlobal = jsonReader.getObjectFromFile("json/spi/impl/spi-account-consent-global.json", SpiAccountConsent.class);
        accountDetailsTO = jsonReader.getObjectFromFile("json/spi/impl/account-details.json", AccountDetailsTO.class);
        accountReference = jsonReader.getObjectFromFile("json/spi/impl/account-reference.json", SpiAccountReference.class);
        transactionTO = jsonReader.getObjectFromFile("json/mappers/transaction-to.json", TransactionTO.class);

        GlobalScaResponseTO sca = new GlobalScaResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        token.setExpires_in(100);
        token.setAccessTokenObject(new AccessTokenTO());
        token.setRefresh_token("refresh_token");
        token.setAccess_token("access_token");
        sca.setBearerToken(token);
        when(tokenService.response(ASPSP_CONSENT_DATA.getAspspConsentData())).thenReturn(sca);
        when(aspspConsentDataProvider.loadAspspConsentData()).thenReturn(BYTES);
    }

    @Test
    void requestTransactionsForAccount_success() {
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);
        when(accountRestClient.getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO)).thenReturn(ResponseEntity.ok(Collections.emptyList()));
        when(accountRestClient.getBalances(RESOURCE_ID)).thenReturn(ResponseEntity.ok(Collections.emptyList()));

        SpiResponse<SpiTransactionReport> actualResponse = accountSpi.requestTransactionsForAccount(SPI_CONTEXT_DATA, buildSpiTransactionReportParameters(MediaType.APPLICATION_XML_VALUE),
                                                                                                    accountReference, spiAccountConsent, aspspConsentDataProvider);

        verify(accountRestClient, times(1)).getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO);
        verify(accountRestClient, times(1)).getBalances(RESOURCE_ID);
        verify(tokenService, times(2)).response(ASPSP_CONSENT_DATA.getAspspConsentData());
        verify(authRequestInterceptor, times(2)).setAccessToken("access_token");
        verify(authRequestInterceptor, times(2)).setAccessToken(null);

        assertEquals(MediaType.APPLICATION_XML_VALUE, actualResponse.getPayload().getResponseContentType());
    }

    @Test
    void requestTransactionsForAccount_useDefaultAcceptTypeWhenNull_success() {
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);
        when(accountRestClient.getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO)).thenReturn(ResponseEntity.ok(Collections.emptyList()));
        when(accountRestClient.getBalances(RESOURCE_ID)).thenReturn(ResponseEntity.ok(Collections.emptyList()));

        SpiResponse<SpiTransactionReport> actualResponse = accountSpi.requestTransactionsForAccount(SPI_CONTEXT_DATA, buildSpiTransactionReportParameters(null),
                                                                                                    accountReference, spiAccountConsent, aspspConsentDataProvider);

        verify(accountRestClient, times(1)).getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO);
        verify(accountRestClient, times(1)).getBalances(RESOURCE_ID);
        verify(tokenService, times(2)).response(ASPSP_CONSENT_DATA.getAspspConsentData());
        verify(authRequestInterceptor, times(2)).setAccessToken("access_token");
        verify(authRequestInterceptor, times(2)).setAccessToken(null);

        assertEquals(MediaType.APPLICATION_JSON_VALUE, actualResponse.getPayload().getResponseContentType());
    }

    @Test
    void requestTransactionsForAccount_useDefaultAcceptTypeWhenWildcard_success() {
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);
        when(accountRestClient.getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO)).thenReturn(ResponseEntity.ok(Collections.emptyList()));
        when(accountRestClient.getBalances(RESOURCE_ID)).thenReturn(ResponseEntity.ok(Collections.emptyList()));

        SpiResponse<SpiTransactionReport> actualResponse = accountSpi.requestTransactionsForAccount(SPI_CONTEXT_DATA, buildSpiTransactionReportParameters("*/*"),
                                                                                                    accountReference, spiAccountConsent, aspspConsentDataProvider);

        verify(accountRestClient, times(1)).getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO);
        verify(accountRestClient, times(1)).getBalances(RESOURCE_ID);
        verify(tokenService, times(2)).response(ASPSP_CONSENT_DATA.getAspspConsentData());
        verify(authRequestInterceptor, times(2)).setAccessToken("access_token");
        verify(authRequestInterceptor, times(2)).setAccessToken(null);

        assertEquals(MediaType.APPLICATION_JSON_VALUE, actualResponse.getPayload().getResponseContentType());
    }

    @Test
    void requestTransactionsForAccount_withException() {
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);
        when(accountRestClient.getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO)).thenReturn(ResponseEntity.ok(Collections.emptyList()));
        when(accountRestClient.getBalances(RESOURCE_ID)).thenReturn(ResponseEntity.ok(Collections.emptyList()));

        when(tokenService.store(scaResponseTO)).thenThrow(getFeignException());

        SpiResponse<SpiTransactionReport> actualResponse = accountSpi.requestTransactionsForAccount(SPI_CONTEXT_DATA, buildSpiTransactionReportParameters(MediaType.APPLICATION_XML_VALUE),
                                                                                                    accountReference, spiAccountConsent, aspspConsentDataProvider);

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
    void requestAccountList_withoutBalance_regularConsent() {
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);

        AccountDetailsTO accountDetails_1 = jsonReader.getObjectFromFile("json/spi/impl/account-details.json", AccountDetailsTO.class);
        AccountDetailsTO accountDetails_2 = jsonReader.getObjectFromFile("json/spi/impl/account-details.json", AccountDetailsTO.class);
        accountDetails_2.setCurrency(Currency.getInstance("USD"));
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.ok(Arrays.asList(accountDetails_1, accountDetails_2)));

        SpiResponse<List<SpiAccountDetails>> actualResponse = accountSpi.requestAccountList(SPI_CONTEXT_DATA, false,
                                                                                            spiAccountConsent, aspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verifyGetListOfAccounts();
    }

    @Test
    void requestAccountList_withBalance_regularConsent() {
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);

        AccountDetailsTO accountDetails_1 = jsonReader.getObjectFromFile("json/spi/impl/account-details.json", AccountDetailsTO.class);
        AccountDetailsTO accountDetails_2 = jsonReader.getObjectFromFile("json/spi/impl/account-details.json", AccountDetailsTO.class);
        accountDetails_2.setCurrency(Currency.getInstance("USD"));
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.ok(Arrays.asList(accountDetails_1, accountDetails_2)));

        SpiResponse<List<SpiAccountDetails>> actualResponse = accountSpi.requestAccountList(SPI_CONTEXT_DATA, true,
                                                                                            spiAccountConsent, aspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verifyGetListOfAccounts();
    }

    @Test
    void requestAccountList_withBalance_consentWithOwnerName() {
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);

        AccountDetailsTO accountDetails_1 = jsonReader.getObjectFromFile("json/spi/impl/account-details.json", AccountDetailsTO.class);
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.ok(Collections.singletonList(accountDetails_1)));

        SpiResponse<List<SpiAccountDetails>> actualResponse = accountSpi.requestAccountList(SPI_CONTEXT_DATA, true,
                                                                                            spiAccountConsentWithOwnerName, aspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verifyGetListOfAccounts();
        List<SpiAccountDetails> spiAccountDetailsActual = actualResponse.getPayload();
        assertNotNull(spiAccountDetailsActual);
        assertEquals(1, spiAccountDetailsActual.size());
        assertNotNull(spiAccountDetailsActual.get(0).getBalances());
    }

    @Test
    void requestAccountList_withBalance_globalConsent() {
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);

        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.ok(Collections.singletonList(accountDetailsTO)));

        SpiResponse<List<SpiAccountDetails>> actualResponse = accountSpi.requestAccountList(SPI_CONTEXT_DATA, true,
                                                                                            spiAccountConsentGlobal, aspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verifyGetListOfAccounts();
    }

    @Test
    void requestAccountList_withoutBalanceAndException_regularConsent() {
        when(tokenService.response(BYTES)).thenThrow(getFeignException());

        SpiResponse<List<SpiAccountDetails>> actualResponse = accountSpi.requestAccountList(SPI_CONTEXT_DATA, false,
                                                                                            spiAccountConsent, aspspConsentDataProvider);

        assertFalse(actualResponse.getErrors().isEmpty());
        assertNull(actualResponse.getPayload());
        verify(aspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(tokenService, times(1)).response(BYTES);
        verify(authRequestInterceptor, times(1)).setAccessToken(null);
    }

    @Test
    void requestAccountList_withoutBalance_regularConsent_noCurrency() {
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

        SpiResponse<List<SpiAccountDetails>> actualResponse = accountSpi.requestAccountList(SPI_CONTEXT_DATA, false,
                                                                                            spiAccountConsent, aspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        List<SpiAccountDetails> spiAccountDetails = actualResponse.getPayload();
        assertNotNull(spiAccountDetails);
        assertEquals(2, spiAccountDetails.size());
        verifyGetListOfAccounts();
    }

    @Test
    void requestAccountList_withoutBalance_regularConsent_currencyPresent() {
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);

        AccountDetailsTO accountDetails_1 = jsonReader.getObjectFromFile("json/spi/impl/account-details.json", AccountDetailsTO.class);
        AccountDetailsTO accountDetails_2 = jsonReader.getObjectFromFile("json/spi/impl/account-details.json", AccountDetailsTO.class);
        accountDetails_2.setCurrency(Currency.getInstance("USD"));
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.ok(Arrays.asList(accountDetails_1, accountDetails_2)));

        SpiResponse<List<SpiAccountDetails>> actualResponse = accountSpi.requestAccountList(SPI_CONTEXT_DATA, false,
                                                                                            spiAccountConsent, aspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        List<SpiAccountDetails> spiAccountDetails = actualResponse.getPayload();
        assertNotNull(spiAccountDetails);
        assertEquals(1, spiAccountDetails.size());
        verifyGetListOfAccounts();
    }

    @Test
    void requestAccountList_additionalInformationOwnerName_ownerNameForAllAccounts() {
        //Given
        SpiAccountConsent spiAccountConsent = buildSpiAccountConsent();
        SpiAccountAccess accountAccess = spiAccountConsent.getAccess();
        accountAccess.setSpiAdditionalInformationAccess(new SpiAdditionalInformationAccess(Collections.emptyList(), Collections.emptyList()));

        List<AccountDetailsTO> accountDetailsTOList = accountAccess.getAccounts().stream()
                                                              .map(account -> buildAccountDetailsTO(account.getIban(), account.getResourceId()))
                                                              .collect(Collectors.toList());
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.ok(accountDetailsTOList));

        SpiAccountDetails spiAccountDetailsFirstAccount = buildSpiAccountDetails(IBAN, RESOURCE_ID);
        when(ownerNameService.shouldContainOwnerName(new IbanAccountReference(IBAN, CURRENCY_EUR), accountAccess)).thenReturn(true);
        when(ownerNameService.enrichAccountDetailsWithOwnerName(spiAccountDetailsFirstAccount))
                .thenReturn(buildSpiAccountDetailsWithOwnerName(IBAN, RESOURCE_ID, ACCOUNT_OWNER_NAME));
        SpiAccountDetails spiAccountDetailsSecondAccount = buildSpiAccountDetails(IBAN_SECOND_ACCOUNT, RESOURCE_ID_SECOND_ACCOUNT);
        when(ownerNameService.shouldContainOwnerName(new IbanAccountReference(IBAN_SECOND_ACCOUNT, CURRENCY_EUR), accountAccess)).thenReturn(true);
        when(ownerNameService.enrichAccountDetailsWithOwnerName(spiAccountDetailsSecondAccount))
                .thenReturn(buildSpiAccountDetailsWithOwnerName(IBAN_SECOND_ACCOUNT, RESOURCE_ID_SECOND_ACCOUNT, ACCOUNT_OWNER_NAME_SECOND_ACCOUNT));

        //When
        SpiResponse<List<SpiAccountDetails>> actualResponse = accountSpi.requestAccountList(SPI_CONTEXT_DATA, false,
                                                                                            spiAccountConsent, aspspConsentDataProvider);
        //Then
        assertTrue(actualResponse.getErrors().isEmpty());
        List<SpiAccountDetails> actualSpiAccountDetailsList = actualResponse.getPayload();
        assertNotNull(actualSpiAccountDetailsList);
        actualSpiAccountDetailsList.forEach(ad -> assertNotNull(ad.getOwnerName()));
        assertEquals(ACCOUNT_OWNER_NAME, actualSpiAccountDetailsList.get(0).getOwnerName());
        assertEquals(ACCOUNT_OWNER_NAME_SECOND_ACCOUNT, actualSpiAccountDetailsList.get(1).getOwnerName());
    }

    @Test
    void requestAccountList_additionalInformationOwnerName_withoutEnrichingWithOwnerName() {
        //Given
        SpiAccountConsent spiAccountConsent = buildSpiAccountConsent();
        SpiAccountAccess accountAccess = spiAccountConsent.getAccess();
        List<SpiAccountReference> accounts = accountAccess.getAccounts();
        accountAccess.setSpiAdditionalInformationAccess(new SpiAdditionalInformationAccess(accounts, Collections.emptyList()));

        List<AccountDetailsTO> accountDetailsTOList = accounts.stream()
                                                              .map(account -> buildAccountDetailsTO(account.getIban(), account.getResourceId()))
                                                              .collect(Collectors.toList());
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.ok(accountDetailsTOList));

        when(ownerNameService.shouldContainOwnerName(new IbanAccountReference(IBAN, CURRENCY_EUR), accountAccess))
                .thenReturn(false);
        when(ownerNameService.shouldContainOwnerName(new IbanAccountReference(IBAN_SECOND_ACCOUNT, CURRENCY_EUR), accountAccess))
                .thenReturn(false);

        //When
        SpiResponse<List<SpiAccountDetails>> actualResponse = accountSpi.requestAccountList(SPI_CONTEXT_DATA, false,
                                                                                            spiAccountConsent, aspspConsentDataProvider);
        //Then
        assertTrue(actualResponse.getErrors().isEmpty());
        List<SpiAccountDetails> spiAccountDetails = actualResponse.getPayload();
        assertNotNull(spiAccountDetails);
        assertNull(spiAccountDetails.get(0).getOwnerName());
        assertNull(spiAccountDetails.get(1).getOwnerName());
        verify(ownerNameService, never()).enrichAccountDetailsWithOwnerName(any());
    }

    @Test
    void requestAccountList_additionalInformationOwnerName_ownerNameOneAccount() {
        //Given
        SpiAccountConsent spiAccountConsent = buildSpiAccountConsent();
        SpiAccountAccess accountAccess = spiAccountConsent.getAccess();
        List<SpiAccountReference> accounts = accountAccess.getAccounts();
        SpiAccountReference spiAccountReference = accounts.get(0);
        SpiAdditionalInformationAccess spiAdditionalInformationAccess = new SpiAdditionalInformationAccess(Collections.singletonList(spiAccountReference), Collections.emptyList());
        accountAccess.setSpiAdditionalInformationAccess(spiAdditionalInformationAccess);

        List<AccountDetailsTO> accountDetailsTOList = accountAccess.getAccounts().stream()
                                                              .map(account -> buildAccountDetailsTO(account.getIban(), account.getResourceId()))
                                                              .collect(Collectors.toList());
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.ok(accountDetailsTOList));

        SpiAccountDetails spiAccountDetailsFirstAccount = buildSpiAccountDetails(IBAN, RESOURCE_ID);
        when(ownerNameService.shouldContainOwnerName(new IbanAccountReference(IBAN, CURRENCY_EUR), accountAccess))
                .thenReturn(true);
        when(ownerNameService.enrichAccountDetailsWithOwnerName(spiAccountDetailsFirstAccount))
                .thenReturn(buildSpiAccountDetailsWithOwnerName(IBAN, RESOURCE_ID, ACCOUNT_OWNER_NAME));
        SpiAccountDetails spiAccountDetailsSecondAccount = buildSpiAccountDetails(IBAN_SECOND_ACCOUNT, RESOURCE_ID_SECOND_ACCOUNT);
        when(ownerNameService.shouldContainOwnerName(new IbanAccountReference(IBAN_SECOND_ACCOUNT, CURRENCY_EUR), accountAccess))
                .thenReturn(false);

        //When
        SpiResponse<List<SpiAccountDetails>> actualResponse = accountSpi.requestAccountList(SPI_CONTEXT_DATA, false,
                                                                                            spiAccountConsent, aspspConsentDataProvider);
        //Then
        assertTrue(actualResponse.getErrors().isEmpty());
        List<SpiAccountDetails> spiAccountDetails = actualResponse.getPayload();
        assertNotNull(spiAccountDetails);
        assertNotNull(spiAccountDetails.get(0).getOwnerName());
        assertNull(spiAccountDetails.get(1).getOwnerName());
        verify(ownerNameService, never()).enrichAccountDetailsWithOwnerName(spiAccountDetailsSecondAccount);
    }

    @Test
    void requestAccountList_availableAccountsConsent_withOwnerName() {
        //Given
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.ok(Collections.singletonList(buildAccountDetailsTO(IBAN, RESOURCE_ID))));
        SpiAccountConsent spiAccountConsent = jsonReader.getObjectFromFile("json/spi/impl/account-spi/spi-account-consent-available-accounts-owner-name.json", SpiAccountConsent.class);
        SpiAccountAccess accountAccess = spiAccountConsent.getAccess();

        SpiAccountDetails spiAccountDetailsFirstAccount = buildSpiAccountDetails(IBAN, RESOURCE_ID);
        when(ownerNameService.shouldContainOwnerName(new IbanAccountReference(IBAN, CURRENCY_EUR), accountAccess))
                .thenReturn(true);
        when(ownerNameService.enrichAccountDetailsWithOwnerName(spiAccountDetailsFirstAccount))
                .thenReturn(buildSpiAccountDetailsWithOwnerName(IBAN, RESOURCE_ID, ACCOUNT_OWNER_NAME));

        //When
        SpiResponse<List<SpiAccountDetails>> actualResponse = accountSpi.requestAccountList(SPI_CONTEXT_DATA, false,
                                                                                            spiAccountConsent, aspspConsentDataProvider);
        //Then
        assertTrue(actualResponse.getErrors().isEmpty());
        List<SpiAccountDetails> spiAccountDetails = actualResponse.getPayload();
        assertNotNull(spiAccountDetails);
        assertEquals(ACCOUNT_OWNER_NAME, spiAccountDetails.get(0).getOwnerName());
    }

    @Test
    void requestAccountList_availableAccountsWithBalanceConsent_withOwnerName() {
        //Given
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.ok(Collections.singletonList(buildAccountDetailsTO(IBAN, RESOURCE_ID))));
        SpiAccountConsent spiAccountConsent = jsonReader.getObjectFromFile("json/spi/impl/account-spi/spi-account-consent-available-accounts-balance-owner-name.json", SpiAccountConsent.class);
        SpiAccountAccess accountAccess = spiAccountConsent.getAccess();

        SpiAccountDetails spiAccountDetailsFirstAccount = buildSpiAccountDetails(IBAN, RESOURCE_ID);
        when(ownerNameService.shouldContainOwnerName(new IbanAccountReference(IBAN, CURRENCY_EUR), accountAccess))
                .thenReturn(true);
        when(ownerNameService.enrichAccountDetailsWithOwnerName(spiAccountDetailsFirstAccount))
                .thenReturn(buildSpiAccountDetailsWithOwnerName(IBAN, RESOURCE_ID, ACCOUNT_OWNER_NAME));

        //When
        SpiResponse<List<SpiAccountDetails>> actualResponse = accountSpi.requestAccountList(SPI_CONTEXT_DATA, false,
                                                                                            spiAccountConsent, aspspConsentDataProvider);
        //Then
        assertTrue(actualResponse.getErrors().isEmpty());
        List<SpiAccountDetails> spiAccountDetails = actualResponse.getPayload();
        assertNotNull(spiAccountDetails);
        assertEquals(ACCOUNT_OWNER_NAME, spiAccountDetails.get(0).getOwnerName());
    }

    @Test
    void requestAccountList_globalConsent_withOwnerName() {
        //Given
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.ok(Collections.singletonList(buildAccountDetailsTO(IBAN, RESOURCE_ID))));
        SpiAccountConsent spiAccountConsent = jsonReader.getObjectFromFile("json/spi/impl/account-spi/spi-account-consent-global-owner-name.json", SpiAccountConsent.class);
        SpiAccountAccess accountAccess = spiAccountConsent.getAccess();

        SpiAccountDetails spiAccountDetailsFirstAccount = buildSpiAccountDetails(IBAN, RESOURCE_ID);
        when(ownerNameService.shouldContainOwnerName(new IbanAccountReference(IBAN, CURRENCY_EUR), accountAccess))
                .thenReturn(true);
        when(ownerNameService.enrichAccountDetailsWithOwnerName(spiAccountDetailsFirstAccount))
                .thenReturn(buildSpiAccountDetailsWithOwnerName(IBAN, RESOURCE_ID, ACCOUNT_OWNER_NAME));

        //When
        SpiResponse<List<SpiAccountDetails>> actualResponse = accountSpi.requestAccountList(SPI_CONTEXT_DATA, false,
                                                                                            spiAccountConsent, aspspConsentDataProvider);
        //Then
        assertTrue(actualResponse.getErrors().isEmpty());
        List<SpiAccountDetails> spiAccountDetails = actualResponse.getPayload();
        assertNotNull(spiAccountDetails);
        assertEquals(ACCOUNT_OWNER_NAME, spiAccountDetails.get(0).getOwnerName());
    }

    @Test
    void requestAccountDetailForAccount_additionalInformationOwnerName_withOwnerName() {
        //Given
        SpiAccountConsent spiAccountConsent = buildSpiAccountConsent();
        SpiAccountAccess accountAccess = spiAccountConsent.getAccess();
        accountAccess.setSpiAdditionalInformationAccess(new SpiAdditionalInformationAccess(Collections.emptyList(), Collections.emptyList()));
        when(accountRestClient.getAccountDetailsById(RESOURCE_ID)).thenReturn(ResponseEntity.ok(buildAccountDetailsTO(IBAN, RESOURCE_ID)));
        SpiAccountDetails spiAccountDetailsFirstAccount = buildSpiAccountDetails(IBAN, RESOURCE_ID);
        when(ownerNameService.shouldContainOwnerName(new IbanAccountReference(IBAN, CURRENCY_EUR), accountAccess))
                .thenReturn(true);
        when(ownerNameService.enrichAccountDetailsWithOwnerName(spiAccountDetailsFirstAccount))
                .thenReturn(buildSpiAccountDetailsWithOwnerName(IBAN, RESOURCE_ID, ACCOUNT_OWNER_NAME));

        //when
        SpiResponse<SpiAccountDetails> actualResponse = accountSpi.requestAccountDetailForAccount(SPI_CONTEXT_DATA, false, accountReference,
                                                                                                  spiAccountConsent, aspspConsentDataProvider);
        //Then
        assertTrue(actualResponse.getErrors().isEmpty());
        SpiAccountDetails spiAccountDetails = actualResponse.getPayload();
        assertNotNull(spiAccountDetails);
        assertNotNull(spiAccountDetails.getOwnerName());
    }

    @Test
    void requestAccountDetailForAccount_additionalInformationOwnerName_withoutEnrichingWithOwnerName() {
        //Given
        SpiAccountConsent spiAccountConsent = buildSpiAccountConsent();
        SpiAccountAccess accountAccess = spiAccountConsent.getAccess();
        List<SpiAccountReference> accounts = accountAccess.getAccounts();
        SpiAdditionalInformationAccess spiAdditionalInformationAccess = new SpiAdditionalInformationAccess(Collections.singletonList(accounts.get(1)), Collections.emptyList());
        accountAccess.setSpiAdditionalInformationAccess(spiAdditionalInformationAccess);
        when(accountRestClient.getAccountDetailsById(RESOURCE_ID)).thenReturn(ResponseEntity.ok(this.accountDetailsTO));

        //when
        SpiResponse<SpiAccountDetails> actualResponse = accountSpi.requestAccountDetailForAccount(SPI_CONTEXT_DATA, false, accountReference,
                                                                                                  spiAccountConsent, aspspConsentDataProvider);
        //Then
        assertTrue(actualResponse.getErrors().isEmpty());
        SpiAccountDetails spiAccountDetails = actualResponse.getPayload();
        assertNotNull(spiAccountDetails);
        assertNull(spiAccountDetails.getOwnerName());
        verify(ownerNameService, never()).enrichAccountDetailsWithOwnerName(any());
    }

    private void verifyGetListOfAccounts() {
        verify(accountRestClient).getListOfAccounts();
        verify(tokenService).response(ASPSP_CONSENT_DATA.getAspspConsentData());
        verify(authRequestInterceptor).setAccessToken("access_token");
        verify(authRequestInterceptor).setAccessToken(null);
    }

    @Test
    void requestAccountDetailForAccount_withBalance() {
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);

        when(accountRestClient.getAccountDetailsById(RESOURCE_ID)).thenReturn(ResponseEntity.ok(accountDetailsTO));

        SpiResponse<SpiAccountDetails> actualResponse = accountSpi.requestAccountDetailForAccount(SPI_CONTEXT_DATA, true, accountReference,
                                                                                                  spiAccountConsent, aspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(aspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(aspspConsentDataProvider, times(1)).updateAspspConsentData(BYTES);
        verifyApplyAuthorisationUsedAndInterceptorWithNull();
        verify(accountRestClient, times(1)).getAccountDetailsById(RESOURCE_ID);
        verify(tokenService, times(1)).store(scaResponseTO);
    }

    @Test
    void requestAccountDetailForAccount_withoutBalance() {
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);
        when(accountRestClient.getAccountDetailsById(RESOURCE_ID)).thenReturn(ResponseEntity.ok(accountDetailsTO));

        SpiResponse<SpiAccountDetails> actualResponse = accountSpi.requestAccountDetailForAccount(SPI_CONTEXT_DATA, false, accountReference,
                                                                                                  spiAccountConsent, aspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(aspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(aspspConsentDataProvider, times(1)).updateAspspConsentData(BYTES);
        verifyApplyAuthorisationUsedAndInterceptorWithNull();
        verify(accountRestClient, times(1)).getAccountDetailsById(RESOURCE_ID);
        verify(tokenService, times(1)).store(scaResponseTO);
    }

    @Test
    void requestAccountDetailForAccount_withoutBalanceAndException() {
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);

        when(accountRestClient.getAccountDetailsById(RESOURCE_ID)).thenThrow(getFeignException());

        SpiResponse<SpiAccountDetails> actualResponse = accountSpi
                                                                .requestAccountDetailForAccount(SPI_CONTEXT_DATA, false, accountReference, spiAccountConsent, aspspConsentDataProvider);

        assertFalse(actualResponse.getErrors().isEmpty());
        assertNull(actualResponse.getPayload());
        verify(aspspConsentDataProvider, times(1)).loadAspspConsentData();
        verifyApplyAuthorisationUsedAndInterceptorWithNull();
        verify(accountRestClient, times(1)).getAccountDetailsById(RESOURCE_ID);
    }

    @Test
    void requestTransactionForAccountByTransactionId_success() {
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);
        when(accountRestClient.getTransactionById(accountReference.getResourceId(), TRANSACTION_ID)).thenReturn(ResponseEntity.ok(transactionTO));

        SpiResponse<SpiTransaction> actualResponse = accountSpi
                                                             .requestTransactionForAccountByTransactionId(SPI_CONTEXT_DATA, TRANSACTION_ID, accountReference, spiAccountConsent, aspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(aspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(aspspConsentDataProvider, times(1)).updateAspspConsentData(BYTES);
        verifyApplyAuthorisationUsedAndInterceptorWithNull();
        verify(accountRestClient, times(1)).getTransactionById(accountReference.getResourceId(), TRANSACTION_ID);
        verify(tokenService, times(1)).store(scaResponseTO);
    }

    @Test
    void requestTransactionForAccountByTransactionId_WithException() {
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);

        when(accountRestClient.getTransactionById(accountReference.getResourceId(), TRANSACTION_ID))
                .thenThrow(getFeignException());

        SpiResponse<SpiTransaction> actualResponse = accountSpi
                                                             .requestTransactionForAccountByTransactionId(SPI_CONTEXT_DATA, TRANSACTION_ID, accountReference, spiAccountConsent, aspspConsentDataProvider);

        assertFalse(actualResponse.getErrors().isEmpty());
        assertNull(actualResponse.getPayload());
        verify(aspspConsentDataProvider, times(1)).loadAspspConsentData();
        verifyApplyAuthorisationUsedAndInterceptorWithNull();
        verify(accountRestClient, times(1)).getTransactionById(accountReference.getResourceId(), TRANSACTION_ID);
    }

    @Test
    void requestTransactionsByDownloadLink_success() throws NoSuchFieldException, IllegalAccessException {
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);

        String transactionList = "transactionList";
        Field fieldTransactionList = accountSpi.getClass().getDeclaredField(transactionList);
        fieldTransactionList.setAccessible(true);
        fieldTransactionList.set(accountSpi, transactionList);

        SpiResponse<SpiTransactionsDownloadResponse> actualResponse = accountSpi
                                                                              .requestTransactionsByDownloadLink(SPI_CONTEXT_DATA, spiAccountConsent, DOWNLOAD_ID, aspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verifyApplyAuthorisationUsedAndInterceptorWithNull();
    }

    @Test
    void requestTransactionsByDownloadLink_WithError() {
        when(tokenService.response(BYTES)).thenThrow(getFeignException());

        SpiResponse<SpiTransactionsDownloadResponse> actualResponse = accountSpi
                                                                              .requestTransactionsByDownloadLink(SPI_CONTEXT_DATA, spiAccountConsent, DOWNLOAD_ID, aspspConsentDataProvider);

        assertFalse(actualResponse.getErrors().isEmpty());
        assertNull(actualResponse.getPayload());
        verify(aspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(tokenService, times(1)).response(BYTES);
        verify(authRequestInterceptor, times(1)).setAccessToken(null);
    }

    @Test
    @MockitoSettings(strictness = LENIENT)
    void processAcceptMediaType() {
        assertEquals(MediaType.APPLICATION_JSON_VALUE, accountSpi.processAcceptMediaType(""));
        assertEquals(MediaType.APPLICATION_JSON_VALUE, accountSpi.processAcceptMediaType(null));
        assertEquals(MediaType.APPLICATION_JSON_VALUE, accountSpi.processAcceptMediaType(MediaType.ALL_VALUE));
        assertEquals(MediaType.APPLICATION_JSON_VALUE, accountSpi.processAcceptMediaType("application/xml, application/json"));

        assertEquals(MediaType.APPLICATION_JSON_VALUE, accountSpi.processAcceptMediaType(MediaType.APPLICATION_JSON_VALUE));
        assertEquals(MediaType.APPLICATION_ATOM_XML_VALUE, accountSpi.processAcceptMediaType(MediaType.APPLICATION_ATOM_XML_VALUE));
    }

    @Test
    @MockitoSettings(strictness = LENIENT)
    void requestTrustedBeneficiariesList() {
        SpiResponse<List<SpiTrustedBeneficiaries>> actual = accountSpi.requestTrustedBeneficiariesList(SPI_CONTEXT_DATA, accountReference, spiAccountConsent, aspspConsentDataProvider);

        assertTrue(actual.isSuccessful());
        assertNotNull(actual.getPayload());
        assertEquals(1, actual.getPayload().size());
    }

    private FeignException getFeignException() {
        return FeignException.errorStatus(RESPONSE_STATUS_200_WITH_EMPTY_BODY,
                                          buildErrorResponse());
    }

    private Response buildErrorResponse() {
        return Response.builder()
                       .status(404)
                       .request(Request.create(Request.HttpMethod.GET, "", Collections.emptyMap(), null))
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

    private AccountDetailsTO buildAccountDetailsTO(String iban, String resourceId) {
        AccountDetailsTO accountDetailsTO = jsonReader.getObjectFromFile("json/spi/impl/account-details.json", AccountDetailsTO.class);
        accountDetailsTO.setIban(iban);
        accountDetailsTO.setId(resourceId);
        return accountDetailsTO;
    }

    private SpiAccountDetails buildSpiAccountDetails(String iban, String resourceId) {
        return buildSpiAccountDetailsWithOwnerName(iban, resourceId, null);
    }

    private SpiAccountDetails buildSpiAccountDetailsWithOwnerName(String iban, String resourceId, String ownerName) {
        SpiAccountDetails spiAccountDetails = jsonReader.getObjectFromFile("json/spi/impl/account-spi/spi-account-details.json", SpiAccountDetails.class);
        spiAccountDetails.setAspspAccountId(iban);
        spiAccountDetails.setIban(iban);
        spiAccountDetails.setResourceId(resourceId);
        spiAccountDetails.setOwnerName(ownerName);
        return spiAccountDetails;
    }

    private SpiAccountConsent buildSpiAccountConsent() {
        return jsonReader.getObjectFromFile("json/spi/impl/spi-account-consent-with-2-accounts.json", SpiAccountConsent.class);
    }

    @NotNull
    private AdditionalAccountInformationTO buildAdditionalAccountInformationTO(String ownerName) {
        AdditionalAccountInformationTO additionalAccountInformationTO = new AdditionalAccountInformationTO();
        additionalAccountInformationTO.setAccountOwnerName(ownerName);
        return additionalAccountInformationTO;
    }
}
