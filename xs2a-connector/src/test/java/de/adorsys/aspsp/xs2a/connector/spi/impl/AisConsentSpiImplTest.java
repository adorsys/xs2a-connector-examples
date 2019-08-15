package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.aspsp.xs2a.connector.spi.converter.*;
import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.account.AccountDetailsTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAConsentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.middleware.api.domain.um.AccessTokenTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.middleware.api.service.TokenStorageService;
import de.adorsys.ledgers.rest.client.AccountRestClient;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.ConsentRestClient;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import de.adorsys.psd2.xs2a.core.tpp.TppInfo;
import de.adorsys.psd2.xs2a.core.tpp.TppRole;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiInitiateAisConsentResponse;
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
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;

import static de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO.STARTED;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AisConsentSpiImplTest {

    @InjectMocks
    private AisConsentSpiImpl spi;

    @Mock
    private ConsentRestClient consentRestClient;
    @Mock
    private TokenStorageService tokenStorageService;
    @Mock
    private AisConsentMapper aisConsentMapper;
    @Mock
    private AuthRequestInterceptor authRequestInterceptor;
    @Mock
    private AspspConsentDataService consentDataService;
    @Mock
    private GeneralAuthorisationService authorisationService;
    @Mock
    private ScaMethodConverter scaMethodConverter;
    @Mock
    private ScaLoginMapper scaLoginMapper;

    @Mock
    private AccountRestClient accountRestClient;
    @Spy
    private LedgersSpiAccountMapper accountMapper = new LedgersSpiAccountMapperImpl();
    @Mock
    private SpiAspspConsentDataProvider aspspConsentDataProvider;
    @Mock
    private SCAResponseTO scaResponseTO;

    private static final TppInfo TPP_INFO = buildTppInfo();
    private final static UUID X_REQUEST_ID = UUID.randomUUID();
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
    private static final String DOWNLOAD_ID= "downloadId";

    private JsonReader jsonReader = new JsonReader();
    private SpiAccountConsent spiAccountConsent;
    private SpiAccountConsent spiAccountConsentGlobal;
    private AccountDetailsTO accountDetailsTO;
    private SpiAccountReference accountReference;

    @Before
    public void setUp() {
        spiAccountConsent = jsonReader.getObjectFromFile("json/spi/impl/spi-account-consent.json", SpiAccountConsent.class);
        spiAccountConsentGlobal = jsonReader.getObjectFromFile("json/spi/impl/spi-account-consent-global.json", SpiAccountConsent.class);
        accountDetailsTO = jsonReader.getObjectFromFile("json/spi/impl/account-details.json", AccountDetailsTO.class);
        accountReference = jsonReader.getObjectFromFile("json/spi/impl/account-reference.json", SpiAccountReference.class);
    }

    @Test
    public void getConsentStatus() {
        assertEquals(ConsentStatus.VALID, spi.getConsentStatus(null));

        SCAConsentResponseTO consentResponse = new SCAConsentResponseTO();
        assertEquals(ConsentStatus.VALID, spi.getConsentStatus(consentResponse));

        consentResponse.setMultilevelScaRequired(true);
        assertEquals(ConsentStatus.VALID, spi.getConsentStatus(consentResponse));

        consentResponse.setPartiallyAuthorised(true);
        assertEquals(ConsentStatus.VALID, spi.getConsentStatus(consentResponse));

        consentResponse.setScaStatus(ScaStatusTO.FINALISED);
        assertEquals(ConsentStatus.PARTIALLY_AUTHORISED, spi.getConsentStatus(consentResponse));
    }

    @Test
    public void initiateAisConsent_WithInitialAspspConsentData() {
        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        token.setExpires_in(100);
        token.setAccessTokenObject(new AccessTokenTO());
        token.setRefresh_token("refresh_token");
        token.setAccess_token("access_token");
        when(scaResponseTO.getBearerToken()).thenReturn(token);
        sca.setBearerToken(token);

        when(aspspConsentDataProvider.loadAspspConsentData()).thenReturn(BYTES);
        when(consentDataService.response(any())).thenReturn(scaResponseTO);
        when(consentRestClient.startSCA(spiAccountConsent.getId(), aisConsentMapper.mapToAisConsent(spiAccountConsent))).thenReturn(ResponseEntity.ok(sca));

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, aspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(consentRestClient, times((1))).startSCA(spiAccountConsent.getId(), aisConsentMapper.mapToAisConsent(spiAccountConsent));
        verify(consentDataService, times(1)).response(ASPSP_CONSENT_DATA.getAspspConsentData());
        verify(authRequestInterceptor, times(1)).setAccessToken(null);
    }

    @Test
    public void initiateAisConsent_WithEmptyInitialAspspConsentData() {
        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        sca.setScaStatus(STARTED);
        sca.setObjectType("SCAConsentResponseTO");
        when(aspspConsentDataProvider.loadAspspConsentData()).thenReturn(new byte[]{});
        when(consentDataService.store(sca, false)).thenReturn(new byte[]{});

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, aspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(consentDataService, times(1)).store(sca, false);
        verify(aspspConsentDataProvider, times(1)).updateAspspConsentData(any());
    }

    @Test
    public void initiateAisConsent_WithException() {
        when(aspspConsentDataProvider.loadAspspConsentData()).thenReturn(BYTES);
        when(consentDataService.response(any())).thenThrow(FeignException.errorStatus(RESPONSE_STATUS_200_WITH_EMPTY_BODY,
                buildErrorResponse()));
//        when(consentRestClient.startSCA(spiAccountConsent.getId(), aisConsentMapper.mapToAisConsent(spiAccountConsent))).thenReturn(ResponseEntity.ok(sca));

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, aspspConsentDataProvider);

        assertFalse(actualResponse.getErrors().isEmpty());
        assertNull(actualResponse.getPayload());
    }

    private Response buildErrorResponse() {
        return Response.builder()
                .status(404)
                .request(Request.create(Request.HttpMethod.GET, "", Collections.emptyMap(), null))
                .headers(Collections.emptyMap())
                .build();
    }

    private static SpiContextData buildSpiContextData(SpiPsuData spiPsuData) {
        return new SpiContextData(spiPsuData, TPP_INFO, X_REQUEST_ID);
    }

    private static TppInfo buildTppInfo() {
        TppInfo tppInfo = new TppInfo();
        tppInfo.setAuthorisationNumber("registrationNumber");
        tppInfo.setTppName("tppName");
        tppInfo.setTppRoles(Collections.singletonList(TppRole.PISP));
        tppInfo.setAuthorityId("authorityId");
        return tppInfo;
    }
}