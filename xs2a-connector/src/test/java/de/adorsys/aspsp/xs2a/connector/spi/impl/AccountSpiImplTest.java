package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapperImpl;
import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.account.AccountDetailsTO;
import de.adorsys.ledgers.middleware.api.domain.account.TransactionTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAConsentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import de.adorsys.ledgers.middleware.api.domain.um.AccessTokenTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.rest.client.AccountRestClient;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.psd2.xs2a.core.ais.BookingStatus;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.core.tpp.TppInfo;
import de.adorsys.psd2.xs2a.core.tpp.TppRole;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.*;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AccountSpiImplTest {
    private static final TppInfo TPP_INFO = buildTppInfo();
    private final static UUID X_REQUEST_ID = UUID.randomUUID();
    private final static UUID INTERNAL_REQUEST_ID = UUID.randomUUID();
    private static final String CONSENT_ID = "c966f143-f6a2-41db-9036-8abaeeef3af7";
    private static final byte[] BYTES = "data".getBytes();

    private static final SpiContextData SPI_CONTEXT_DATA = buildSpiContextData(null);
    private static final AspspConsentData ASPSP_CONSENT_DATA = new AspspConsentData(BYTES, CONSENT_ID);
    private static final String RESOURCE_ID = "11111-999999999";
    private static final String IBAN = "DE89370400440532013000";

    private final static LocalDate DATE_FROM = LocalDate.of(2019, 1, 1);
    private final static LocalDate DATE_TO = LocalDate.of(2020, 1, 1);

    private static final String RESPONSE_STATUS_200_WITH_EMPTY_BODY = "Response status was 200, but the body was empty!";
    private static final String TRANSACTION_ID = "1234567";
    private static final String DOWNLOAD_ID = "downloadId";

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
    private SCAResponseTO scaResponseTO;

    private JsonReader jsonReader = new JsonReader();
    private SpiAccountConsent spiAccountConsent;
    private SpiAccountConsent spiAccountConsentGlobal;
    private AccountDetailsTO accountDetailsTO;
    private SpiAccountReference accountReference;
    private TransactionTO transactionTO;

    @Before
    public void setUp() {
        spiAccountConsent = jsonReader.getObjectFromFile("json/spi/impl/spi-account-consent.json", SpiAccountConsent.class);
        spiAccountConsentGlobal = jsonReader.getObjectFromFile("json/spi/impl/spi-account-consent-global.json", SpiAccountConsent.class);
        accountDetailsTO = jsonReader.getObjectFromFile("json/spi/impl/account-details.json", AccountDetailsTO.class);
        accountReference = jsonReader.getObjectFromFile("json/spi/impl/account-reference.json", SpiAccountReference.class);
        transactionTO = jsonReader.getObjectFromFile("json/mappers/transaction-to.json", TransactionTO.class);

        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        token.setExpires_in(100);
        token.setAccessTokenObject(new AccessTokenTO());
        token.setRefresh_token("refresh_token");
        token.setAccess_token("access_token");
        sca.setBearerToken(token);
        when(tokenService.response(ASPSP_CONSENT_DATA.getAspspConsentData())).thenReturn(sca);
        when(aspspConsentDataProvider.loadAspspConsentData()).thenReturn(BYTES);
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(bearerTokenTO);
        when(tokenService.response(BYTES)).thenReturn(scaResponseTO);
        when(tokenService.store(scaResponseTO)).thenReturn(BYTES);
        when(accountRestClient.getTransactionById(accountReference.getResourceId(), TRANSACTION_ID)).thenReturn(ResponseEntity.ok(transactionTO));
        when(accountRestClient.getAccountDetailsById(RESOURCE_ID)).thenReturn(ResponseEntity.ok(accountDetailsTO));
        when(accountRestClient.getAccountDetailsByIban(IBAN)).thenReturn(ResponseEntity.ok(accountDetailsTO));
        when(accountRestClient.getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO)).thenReturn(ResponseEntity.ok(Collections.emptyList()));
        when(accountRestClient.getBalances(RESOURCE_ID)).thenReturn(ResponseEntity.ok(Collections.emptyList()));
    }

    @Test
    public void requestTransactionsForAccount_success() {
        SpiResponse<SpiTransactionReport> actualResponse = accountSpi.requestTransactionsForAccount(SPI_CONTEXT_DATA, MediaType.APPLICATION_XML_VALUE, true, DATE_FROM, DATE_TO, BookingStatus.BOOKED,
                                                                                                    accountReference, spiAccountConsent, aspspConsentDataProvider);

        verify(accountRestClient, times(1)).getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO);
        verify(accountRestClient, times(1)).getBalances(RESOURCE_ID);
        verify(tokenService, times(2)).response(ASPSP_CONSENT_DATA.getAspspConsentData());
        verify(authRequestInterceptor, times(2)).setAccessToken("access_token");
        verify(authRequestInterceptor, times(2)).setAccessToken(null);

        assertEquals(MediaType.APPLICATION_XML_VALUE, actualResponse.getPayload().getResponseContentType());
    }

    @Test
    public void requestTransactionsForAccount_useDefaultAcceptTypeWhenNull_success() {
        SpiResponse<SpiTransactionReport> actualResponse = accountSpi.requestTransactionsForAccount(SPI_CONTEXT_DATA, null, true, DATE_FROM, DATE_TO, BookingStatus.BOOKED,
                                                                                                    accountReference, spiAccountConsent, aspspConsentDataProvider);

        verify(accountRestClient, times(1)).getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO);
        verify(accountRestClient, times(1)).getBalances(RESOURCE_ID);
        verify(tokenService, times(2)).response(ASPSP_CONSENT_DATA.getAspspConsentData());
        verify(authRequestInterceptor, times(2)).setAccessToken("access_token");
        verify(authRequestInterceptor, times(2)).setAccessToken(null);

        assertEquals(MediaType.APPLICATION_JSON_VALUE, actualResponse.getPayload().getResponseContentType());
    }

    @Test
    public void requestTransactionsForAccount_useDefaultAcceptTypeWhenWildcard_success() {
        SpiResponse<SpiTransactionReport> actualResponse = accountSpi.requestTransactionsForAccount(SPI_CONTEXT_DATA, "*/*", true, DATE_FROM, DATE_TO, BookingStatus.BOOKED,
                                                                                                    accountReference, spiAccountConsent, aspspConsentDataProvider);

        verify(accountRestClient, times(1)).getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO);
        verify(accountRestClient, times(1)).getBalances(RESOURCE_ID);
        verify(tokenService, times(2)).response(ASPSP_CONSENT_DATA.getAspspConsentData());
        verify(authRequestInterceptor, times(2)).setAccessToken("access_token");
        verify(authRequestInterceptor, times(2)).setAccessToken(null);

        assertEquals(MediaType.APPLICATION_JSON_VALUE, actualResponse.getPayload().getResponseContentType());
    }

    @Test
    public void requestTransactionsForAccount_withException() {
        when(tokenService.store(scaResponseTO)).thenThrow(getFeignException());

        SpiResponse<SpiTransactionReport> actualResponse = accountSpi.requestTransactionsForAccount(SPI_CONTEXT_DATA, MediaType.APPLICATION_XML_VALUE, true, DATE_FROM, DATE_TO, BookingStatus.BOOKED,
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
    public void requestAccountList_withoutBalance_regularConsent() {
        SpiResponse<List<SpiAccountDetails>> actualResponse = accountSpi.requestAccountList(SPI_CONTEXT_DATA, false,
                                                                                            spiAccountConsent, aspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(accountRestClient, times(1)).getAccountDetailsByIban(IBAN);
        verify(tokenService, times(2)).response(ASPSP_CONSENT_DATA.getAspspConsentData());
        verify(authRequestInterceptor, times(2)).setAccessToken("access_token");
        verify(authRequestInterceptor, times(2)).setAccessToken(null);
    }

    @Test
    public void requestAccountList_withBalance_regularConsent() {
        SpiResponse<List<SpiAccountDetails>> actualResponse = accountSpi.requestAccountList(SPI_CONTEXT_DATA, true,
                                                                                            spiAccountConsent, aspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(accountRestClient, times(1)).getAccountDetailsByIban(IBAN);
        verify(tokenService, times(2)).response(ASPSP_CONSENT_DATA.getAspspConsentData());
        verify(authRequestInterceptor, times(2)).setAccessToken("access_token");
        verify(authRequestInterceptor, times(2)).setAccessToken(null);
    }

    @Test
    public void requestAccountList_withBalance_globalConsent() {
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.ok(Collections.singletonList(accountDetailsTO)));

        SpiResponse<List<SpiAccountDetails>> actualResponse = accountSpi.requestAccountList(SPI_CONTEXT_DATA, true,
                                                                                            spiAccountConsentGlobal, aspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(accountRestClient, times(1)).getListOfAccounts();
        verify(tokenService, times(2)).response(ASPSP_CONSENT_DATA.getAspspConsentData());
        verify(authRequestInterceptor, times(2)).setAccessToken("access_token");
        verify(authRequestInterceptor, times(2)).setAccessToken(null);
    }

    @Test
    public void requestAccountList_withoutBalanceAndException_regularConsent() {
        when(tokenService.response(BYTES)).thenThrow(getFeignException());

        SpiResponse<List<SpiAccountDetails>> actualResponse = accountSpi.requestAccountList(SPI_CONTEXT_DATA, false,
                                                                                            spiAccountConsent, aspspConsentDataProvider);

        assertFalse(actualResponse.getErrors().isEmpty());
        assertNull(actualResponse.getPayload());
        verify(aspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(tokenService, times(1)).response(BYTES);
        verify(authRequestInterceptor, times(1)).setAccessToken(null);
    }

    private static TppInfo buildTppInfo() {
        TppInfo tppInfo = new TppInfo();
        tppInfo.setAuthorisationNumber("registrationNumber");
        tppInfo.setTppName("tppName");
        tppInfo.setTppRoles(Collections.singletonList(TppRole.PISP));
        tppInfo.setAuthorityId("authorityId");
        return tppInfo;
    }

    private static SpiContextData buildSpiContextData(SpiPsuData spiPsuData) {
        return new SpiContextData(spiPsuData, TPP_INFO, X_REQUEST_ID, INTERNAL_REQUEST_ID);
    }

    @Test
    public void requestAccountDetailForAccount_withBalance() {
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
    public void requestAccountDetailForAccount_withoutBalance() {
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
    public void requestAccountDetailForAccount_withoutBalanceAndException() {
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
    public void requestTransactionForAccountByTransactionId_success() {
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
    public void requestTransactionForAccountByTransactionId_WithException() {
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
    public void requestTransactionsByDownloadLink_success() throws NoSuchFieldException, IllegalAccessException {
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
    public void requestTransactionsByDownloadLink_WithError() {
        when(tokenService.response(BYTES)).thenThrow(getFeignException());

        SpiResponse<SpiTransactionsDownloadResponse> actualResponse = accountSpi
                                                                              .requestTransactionsByDownloadLink(SPI_CONTEXT_DATA, spiAccountConsent, DOWNLOAD_ID, aspspConsentDataProvider);

        assertFalse(actualResponse.getErrors().isEmpty());
        assertNull(actualResponse.getPayload());
        verify(aspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(tokenService, times(1)).response(BYTES);
        verify(authRequestInterceptor, times(1)).setAccessToken(null);
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
}
