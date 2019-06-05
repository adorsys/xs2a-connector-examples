package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapper;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAConsentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.um.AccessTokenTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.rest.client.AccountRestClient;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.psd2.xs2a.core.ais.BookingStatus;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.core.tpp.TppInfo;
import de.adorsys.psd2.xs2a.core.tpp.TppRole;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiTransactionReport;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AccountSpiImplTest {
    private static final TppInfo TPP_INFO = buildTppInfo();
    private final static UUID X_REQUEST_ID = UUID.randomUUID();
    private static final String CONSENT_ID = "c966f143-f6a2-41db-9036-8abaeeef3af7";

    private static final SpiContextData SPI_CONTEXT_DATA = buildSpiContextData(null);
    private static final AspspConsentData ASPSP_CONSENT_DATA = new AspspConsentData("data".getBytes(), CONSENT_ID);
    private static final SpiAccountConsent SPI_ACCOUNT_CONSENT = new SpiAccountConsent();
    private static final String RESOURCE_ID = "5c2d20da-f20a-4a5e-bf6d-be5b239e3561";

    private final static LocalDate DATE_FROM = LocalDate.of(2019, 1, 1);
    private final static LocalDate DATE_TO = LocalDate.of(2020, 1, 1);


    @InjectMocks
    private AccountSpiImpl accountSpi;

    @Mock
    private AccountRestClient accountRestClient;
    @Mock
    private LedgersSpiAccountMapper accountMapper;
    @Mock
    private AuthRequestInterceptor authRequestInterceptor;
    @Mock
    private AspspConsentDataService tokenService;

    @Test
    public void requestTransactionsForAccount_success() {
        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        sca.setBearerToken(new BearerTokenTO("access_token", 100, "refresh_token", new AccessTokenTO()));
        when(tokenService.response(ASPSP_CONSENT_DATA.getAspspConsentData())).thenReturn(sca);

        when(accountRestClient.getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO)).thenReturn(ResponseEntity.ok(new ArrayList<>()));
        when(accountRestClient.getBalances(RESOURCE_ID)).thenReturn(ResponseEntity.ok(new ArrayList<>()));

        SpiResponse<SpiTransactionReport> actualResponse = accountSpi.requestTransactionsForAccount(SPI_CONTEXT_DATA, MediaType.APPLICATION_XML_VALUE, true, DATE_FROM, DATE_TO, BookingStatus.BOOKED,
                                                                                         new SpiAccountReference(RESOURCE_ID, null, null, null, null, null, Currency.getInstance("EUR")),
                                                                                         SPI_ACCOUNT_CONSENT, ASPSP_CONSENT_DATA);

        verify(accountRestClient, times(1)).getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO);
        verify(accountRestClient, times(1)).getBalances(RESOURCE_ID);
        verify(tokenService, times(2)).response(ASPSP_CONSENT_DATA.getAspspConsentData());
        verify(authRequestInterceptor, times(2)).setAccessToken("access_token");
        verify(authRequestInterceptor, times(2)).setAccessToken(null);

        assertEquals(MediaType.APPLICATION_XML_VALUE, actualResponse.getPayload().getResponseContentType());
    }

    @Test
    public void requestTransactionsForAccount_useDefaultAcceptTypeWhenNull_success() {
        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        sca.setBearerToken(new BearerTokenTO("access_token", 100, "refresh_token", new AccessTokenTO()));
        when(tokenService.response(ASPSP_CONSENT_DATA.getAspspConsentData())).thenReturn(sca);

        when(accountRestClient.getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO)).thenReturn(ResponseEntity.ok(new ArrayList<>()));
        when(accountRestClient.getBalances(RESOURCE_ID)).thenReturn(ResponseEntity.ok(new ArrayList<>()));

        SpiResponse<SpiTransactionReport> actualResponse = accountSpi.requestTransactionsForAccount(SPI_CONTEXT_DATA, null, true, DATE_FROM, DATE_TO, BookingStatus.BOOKED,
                                                 new SpiAccountReference(RESOURCE_ID, null, null, null, null, null, Currency.getInstance("EUR")),
                                                 SPI_ACCOUNT_CONSENT, ASPSP_CONSENT_DATA);

        verify(accountRestClient, times(1)).getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO);
        verify(accountRestClient, times(1)).getBalances(RESOURCE_ID);
        verify(tokenService, times(2)).response(ASPSP_CONSENT_DATA.getAspspConsentData());
        verify(authRequestInterceptor, times(2)).setAccessToken("access_token");
        verify(authRequestInterceptor, times(2)).setAccessToken(null);

        assertEquals(MediaType.APPLICATION_JSON_VALUE, actualResponse.getPayload().getResponseContentType());
    }

    @Test
    public void requestTransactionsForAccount_useDefaultAcceptTypeWhenWildcard_success() {
        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        sca.setBearerToken(new BearerTokenTO("access_token", 100, "refresh_token", new AccessTokenTO()));
        when(tokenService.response(ASPSP_CONSENT_DATA.getAspspConsentData())).thenReturn(sca);

        when(accountRestClient.getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO)).thenReturn(ResponseEntity.ok(new ArrayList<>()));
        when(accountRestClient.getBalances(RESOURCE_ID)).thenReturn(ResponseEntity.ok(new ArrayList<>()));

        SpiResponse<SpiTransactionReport> actualResponse = accountSpi.requestTransactionsForAccount(SPI_CONTEXT_DATA, "*/*", true, DATE_FROM, DATE_TO, BookingStatus.BOOKED,
                                                                                                    new SpiAccountReference(RESOURCE_ID, null, null, null, null, null, Currency.getInstance("EUR")),
                                                                                                    SPI_ACCOUNT_CONSENT, ASPSP_CONSENT_DATA);

        verify(accountRestClient, times(1)).getTransactionByDates(RESOURCE_ID, DATE_FROM, DATE_TO);
        verify(accountRestClient, times(1)).getBalances(RESOURCE_ID);
        verify(tokenService, times(2)).response(ASPSP_CONSENT_DATA.getAspspConsentData());
        verify(authRequestInterceptor, times(2)).setAccessToken("access_token");
        verify(authRequestInterceptor, times(2)).setAccessToken(null);

        assertEquals(MediaType.APPLICATION_JSON_VALUE, actualResponse.getPayload().getResponseContentType());

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
        return new SpiContextData(spiPsuData, TPP_INFO, X_REQUEST_ID);
    }
}