package de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation;

import de.adorsys.aspsp.xs2a.connector.spi.converter.AisConsentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaLoginMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionHandler;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.aspsp.xs2a.connector.spi.impl.MultilevelScaService;
import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.aspsp.xs2a.util.TestSpiDataProvider;
import de.adorsys.ledgers.middleware.api.domain.account.AccountDetailsTO;
import de.adorsys.ledgers.middleware.api.domain.sca.*;
import de.adorsys.ledgers.middleware.api.domain.um.AccessTokenTO;
import de.adorsys.ledgers.middleware.api.domain.um.AisConsentTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.middleware.api.service.TokenStorageService;
import de.adorsys.ledgers.rest.client.AccountRestClient;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.ConsentRestClient;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.sca.ChallengeData;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountDetails;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.*;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiAccountAccess;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiInitiateAisConsentResponse;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiVerifyScaAuthorisationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AisConsentSpiImplTest {
    private static final String ACCESS_TOKEN = "access_token";
    private static final String AUTHORISATION_ID = "authorisation id";
    private static final String AUTHENTICATION_METHOD_ID = "authentication method id";

    private static final String CONSENT_ID = "c966f143-f6a2-41db-9036-8abaeeef3af7";
    private static final byte[] CONSENT_DATA_BYTES = "consent_data".getBytes();

    private static final SpiContextData SPI_CONTEXT_DATA = TestSpiDataProvider.getSpiContextData();
    private static final AspspConsentData ASPSP_CONSENT_DATA = new AspspConsentData(CONSENT_DATA_BYTES, CONSENT_ID);
    private static final String RESPONSE_STATUS_200_WITH_EMPTY_BODY = "Response status was 200, but the body was empty!";
    private static final String ONLINE_BANKING_URL_FIELD_NAME = "onlineBankingUrl";
    private static final String ONLINE_BANKING_URL_VALUE = "some.url";
    private static final SpiPsuAuthorisationResponse SPI_PSU_AUTHORISATION_FAILURE_RESPONSE = new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.FAILURE);

    private JsonReader jsonReader = new JsonReader();
    private SpiAccountConsent spiAccountConsent = jsonReader.getObjectFromFile("json/spi/impl/spi-account-consent.json", SpiAccountConsent.class);
    private SpiAccountConsent spiAccountConsentAvailableAccounts = jsonReader.getObjectFromFile("json/spi/impl/spi-account-consent-available-accounts.json", SpiAccountConsent.class);
    private SpiAccountConsent spiAccountConsentAvailableAccountsWithBalances = jsonReader.getObjectFromFile("json/spi/impl/spi-account-consent-available-accounts-with-balances.json", SpiAccountConsent.class);
    private SpiAccountConsent spiAccountConsentGlobal = jsonReader.getObjectFromFile("json/spi/impl/spi-account-consent-global.json", SpiAccountConsent.class);

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
    private ScaLoginMapper scaLoginMapper;
    @Mock
    private SCAResponseTO scaResponseTO;
    @Mock
    private FeignExceptionReader feignExceptionReader;
    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;
    @Mock
    private ScaMethodConverter scaMethodConverter;
    @Mock
    private AccountRestClient accountRestClient;
    @Mock
    private LedgersSpiAccountMapper accountMapper;
    @Mock
    private MultilevelScaService multilevelScaService;

    @Test
    public void initiateAisConsent_WithInitialAspspConsentData() {
        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        token.setExpires_in(100);
        token.setAccessTokenObject(new AccessTokenTO());
        token.setRefresh_token("refresh_token");
        token.setAccess_token(ACCESS_TOKEN);
        when(scaResponseTO.getBearerToken()).thenReturn(token);
        sca.setBearerToken(token);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(any())).thenReturn(scaResponseTO);
        when(consentRestClient.startSCA(spiAccountConsent.getId(), aisConsentMapper.mapToAisConsent(spiAccountConsent))).thenReturn(ResponseEntity.ok(sca));

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(consentRestClient, times((1))).startSCA(spiAccountConsent.getId(), aisConsentMapper.mapToAisConsent(spiAccountConsent));
        verify(consentDataService).response(ASPSP_CONSENT_DATA.getAspspConsentData());
        verify(authRequestInterceptor).setAccessToken(null);
    }

    @Test
    public void initiateAisConsent_WithInitialAspspConsentData_availableAccounts() {
        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        token.setExpires_in(100);
        token.setAccessTokenObject(new AccessTokenTO());
        token.setRefresh_token("refresh_token");
        token.setAccess_token(ACCESS_TOKEN);
        when(scaResponseTO.getBearerToken()).thenReturn(token);
        sca.setBearerToken(token);

        List<AccountDetailsTO> accountDetailsTOS = buildListOfAccounts();
        List<SpiAccountDetails> spiAccountDetails = buildSpiAccountDetails();

        SpiAccountConsent spiAccountConsent = Mockito.spy(spiAccountConsentAvailableAccounts);
        SpiAccountAccess spiAccountAccess = Mockito.spy(spiAccountConsentAvailableAccounts.getAccess());
        spiAccountConsent.setAccess(spiAccountAccess);

        when(accountMapper.toSpiAccountDetails(accountDetailsTOS.get(0))).thenReturn(spiAccountDetails.get(0));
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.of(Optional.of(accountDetailsTOS)));
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(any())).thenReturn(scaResponseTO);
        when(consentRestClient.startSCA(spiAccountConsent.getId(), aisConsentMapper.mapToAisConsent(spiAccountConsent))).thenReturn(ResponseEntity.ok(sca));

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(consentRestClient, times((1))).startSCA(spiAccountConsent.getId(), aisConsentMapper.mapToAisConsent(spiAccountConsent));
        verify(consentDataService).response(ASPSP_CONSENT_DATA.getAspspConsentData());
        verify(authRequestInterceptor).setAccessToken(null);

        List<SpiAccountReference> spiAccountReferences = spiAccountDetails.stream().map(SpiAccountReference::new).collect(Collectors.toList());
        verify(spiAccountConsent, times(2)).getAccess();
        verify(spiAccountAccess).setAccounts(spiAccountReferences);
    }

    @Test
    public void initiateAisConsent_WithInitialAspspConsentData_availableAccountsWithBalances() {
        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        token.setExpires_in(100);
        token.setAccessTokenObject(new AccessTokenTO());
        token.setRefresh_token("refresh_token");
        token.setAccess_token(ACCESS_TOKEN);
        when(scaResponseTO.getBearerToken()).thenReturn(token);
        sca.setBearerToken(token);

        List<AccountDetailsTO> accountDetailsTOS = buildListOfAccounts();
        List<SpiAccountDetails> spiAccountDetails = buildSpiAccountDetails();

        SpiAccountConsent spiAccountConsent = Mockito.spy(spiAccountConsentAvailableAccountsWithBalances);
        SpiAccountAccess spiAccountAccess = Mockito.spy(spiAccountConsentAvailableAccountsWithBalances.getAccess());
        spiAccountConsent.setAccess(spiAccountAccess);

        when(accountMapper.toSpiAccountDetails(accountDetailsTOS.get(0))).thenReturn(spiAccountDetails.get(0));
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.of(Optional.of(accountDetailsTOS)));
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(any())).thenReturn(scaResponseTO);
        when(consentRestClient.startSCA(spiAccountConsent.getId(), aisConsentMapper.mapToAisConsent(spiAccountConsent))).thenReturn(ResponseEntity.ok(sca));

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(consentRestClient, times((1))).startSCA(spiAccountConsent.getId(), aisConsentMapper.mapToAisConsent(spiAccountConsent));
        verify(consentDataService).response(ASPSP_CONSENT_DATA.getAspspConsentData());
        verify(authRequestInterceptor).setAccessToken(null);

        List<SpiAccountReference> spiAccountReferences = spiAccountDetails.stream().map(SpiAccountReference::new).collect(Collectors.toList());
        verify(spiAccountConsent, times(2)).getAccess();
        verify(spiAccountAccess).setAccounts(spiAccountReferences);
    }

    @Test
    public void initiateAisConsent_WithInitialAspspConsentData_global() {
        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        token.setExpires_in(100);
        token.setAccessTokenObject(new AccessTokenTO());
        token.setRefresh_token("refresh_token");
        token.setAccess_token(ACCESS_TOKEN);
        when(scaResponseTO.getBearerToken()).thenReturn(token);
        sca.setBearerToken(token);

        List<AccountDetailsTO> accountDetailsTOS = buildListOfAccounts();
        List<SpiAccountDetails> spiAccountDetails = buildSpiAccountDetails();

        SpiAccountConsent spiAccountConsent = Mockito.spy(spiAccountConsentGlobal);
        SpiAccountAccess spiAccountAccess = Mockito.spy(spiAccountConsentGlobal.getAccess());
        spiAccountConsent.setAccess(spiAccountAccess);

        when(accountMapper.toSpiAccountDetails(accountDetailsTOS.get(0))).thenReturn(spiAccountDetails.get(0));
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.of(Optional.of(accountDetailsTOS)));
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(any())).thenReturn(scaResponseTO);
        when(consentRestClient.startSCA(spiAccountConsent.getId(), aisConsentMapper.mapToAisConsent(spiAccountConsent))).thenReturn(ResponseEntity.ok(sca));

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(consentRestClient, times((1))).startSCA(spiAccountConsent.getId(), aisConsentMapper.mapToAisConsent(spiAccountConsent));
        verify(consentDataService).response(ASPSP_CONSENT_DATA.getAspspConsentData());
        verify(authRequestInterceptor).setAccessToken(null);

        List<SpiAccountReference> spiAccountReferences = spiAccountDetails.stream().map(SpiAccountReference::new).collect(Collectors.toList());
        verify(spiAccountConsent, times(2)).getAccess();
        verify(spiAccountAccess).setAccounts(spiAccountReferences);
        verify(spiAccountAccess).setTransactions(spiAccountReferences);
        verify(spiAccountAccess).setBalances(spiAccountReferences);
    }


    @Test
    public void initiateAisConsent_WithEmptyInitialAspspConsentData() {
        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        sca.setScaStatus(STARTED);
        sca.setObjectType("SCAConsentResponseTO");
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(new byte[]{});
        when(consentDataService.store(sca, false)).thenReturn(new byte[]{});
        when(multilevelScaService.isMultilevelScaRequired(SPI_CONTEXT_DATA.getPsuData(), Collections.singleton(spiAccountConsent.getAccess().getAccounts().get(0)))).thenReturn(Optional.of(Boolean.TRUE));

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(consentDataService).store(sca, false);
        verify(spiAspspConsentDataProvider).updateAspspConsentData(any());
    }

    @Test
    public void initiateAisConsent_WithException() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(any())).thenThrow(FeignException.errorStatus(RESPONSE_STATUS_200_WITH_EMPTY_BODY,
                                                                                      buildErrorResponse()));

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        assertFalse(actualResponse.getErrors().isEmpty());
        assertNull(actualResponse.getPayload());
    }

    @Test
    public void authorisePsu() throws IOException {
        // Given
        SpiPsuData spiPsuData = SpiPsuData.builder().psuId("psuId").build();
        SCAConsentResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        AisConsentTO aisConsentTO = new AisConsentTO();
        String password = "password";
        SCALoginResponseTO scaLoginResponseTO = new SCALoginResponseTO();

        SCAConsentResponseTO scaConsentResponseFromLoginResponse = new SCAConsentResponseTO();
        scaConsentResponseFromLoginResponse.setScaStatus(PSUAUTHENTICATED);

        when(authorisationService.authorisePsuForConsent(spiPsuData, password, CONSENT_ID, OpTypeTO.CONSENT, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPsuAuthorisationResponse>builder().payload(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS)).build());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, false))
                .thenReturn(scaConsentResponseTO);

        when(consentDataService.response(CONSENT_DATA_BYTES))
                .thenReturn(scaConsentResponseTO);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        when(tokenStorageService.fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class)).thenReturn(scaLoginResponseTO);
        when(scaLoginMapper.toConsentResponse(scaLoginResponseTO)).thenReturn(scaConsentResponseFromLoginResponse);

        SCAConsentResponseTO startScaResponse = new SCAConsentResponseTO();
        startScaResponse.setScaStatus(PSUAUTHENTICATED);

        when(aisConsentMapper.mapToAisConsent(spiAccountConsent)).thenReturn(aisConsentTO);
        when(consentRestClient.startSCA(spiAccountConsent.getId(), aisConsentTO)).thenReturn(ResponseEntity.ok(startScaResponse));

        // When
        SpiResponse<SpiPsuAuthorisationResponse> actualResponse = spi.authorisePsu(SPI_CONTEXT_DATA, spiPsuData, password, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertFalse(actualResponse.hasError());
        assertEquals(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS), actualResponse.getPayload());

        verify(spiAspspConsentDataProvider, times(2)).updateAspspConsentData(tokenStorageService.toBytes(scaConsentResponseTO));
        verify(authorisationService).authorisePsuForConsent(spiPsuData, password, CONSENT_ID, OpTypeTO.CONSENT, spiAspspConsentDataProvider);
        verify(authRequestInterceptor).setAccessToken(scaConsentResponseTO.getBearerToken().getAccess_token());
        verify(consentRestClient).startSCA(CONSENT_ID, aisConsentTO);
        verify(authRequestInterceptor).setAccessToken(null);
    }

    @Test
    public void authorisePsu_multilevel() throws IOException {
        // Given
        SpiPsuData spiPsuData = SpiPsuData.builder().psuId("psu").build();
        SpiPsuData spiPsuData2 = SpiPsuData.builder().psuId("psu2").build();
        spiAccountConsent.setPsuData(Arrays.asList(spiPsuData, spiPsuData2));
        SCAConsentResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(PSUIDENTIFIED);
        String password = "password";
        SCALoginResponseTO scaLoginResponseTO = new SCALoginResponseTO();

        SCAConsentResponseTO scaConsentResponseFromLoginResponse = new SCAConsentResponseTO();
        scaConsentResponseFromLoginResponse.setScaStatus(PSUAUTHENTICATED);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, false))
                .thenReturn(scaConsentResponseTO);
        when(authorisationService.authorisePsuForConsent(spiPsuData, password, CONSENT_ID, OpTypeTO.CONSENT, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPsuAuthorisationResponse>builder().payload(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS)).build());

        when(tokenStorageService.fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class)).thenReturn(scaLoginResponseTO);
        when(scaLoginMapper.toConsentResponse(scaLoginResponseTO)).thenReturn(scaConsentResponseFromLoginResponse);

        SCAConsentResponseTO startScaResponse = new SCAConsentResponseTO();
        startScaResponse.setScaStatus(PSUAUTHENTICATED);
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(scaConsentResponseTO);
        when(consentRestClient.startSCA(spiAccountConsent.getId(), aisConsentMapper.mapToAisConsent(spiAccountConsent))).thenReturn(ResponseEntity.ok(startScaResponse));

        // When
        SpiResponse<SpiPsuAuthorisationResponse> actualResponse = spi.authorisePsu(SPI_CONTEXT_DATA, spiPsuData, password, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertFalse(actualResponse.hasError());
        assertEquals(SpiAuthorisationStatus.SUCCESS, actualResponse.getPayload().getSpiAuthorisationStatus());

        verify(spiAspspConsentDataProvider, times(2)).updateAspspConsentData(tokenStorageService.toBytes(scaConsentResponseTO));
        verify(authorisationService).authorisePsuForConsent(spiPsuData, password, CONSENT_ID, OpTypeTO.CONSENT, spiAspspConsentDataProvider);
    }

    @Test
    public void authorisePsu_consentInternalError() throws IOException {
        // Given
        SpiPsuData spiPsuData = SpiPsuData.builder().psuId("psuId").build();
        SCAConsentResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        AisConsentTO aisConsentTO = new AisConsentTO();
        String password = "password";
        SCALoginResponseTO scaLoginResponseTO = new SCALoginResponseTO();

        SCAConsentResponseTO scaConsentResponseFromLoginResponse = new SCAConsentResponseTO();
        scaConsentResponseFromLoginResponse.setScaStatus(PSUAUTHENTICATED);

        when(authorisationService.authorisePsuForConsent(spiPsuData, password, CONSENT_ID, OpTypeTO.CONSENT, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPsuAuthorisationResponse>builder().payload(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS)).build());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, false))
                .thenReturn(scaConsentResponseTO);

        when(consentDataService.response(CONSENT_DATA_BYTES)).thenReturn(scaConsentResponseTO);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        when(tokenStorageService.fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class)).thenReturn(scaLoginResponseTO);
        when(scaLoginMapper.toConsentResponse(scaLoginResponseTO)).thenReturn(scaConsentResponseFromLoginResponse);

        SCAConsentResponseTO startScaResponse = new SCAConsentResponseTO();
        startScaResponse.setScaStatus(PSUAUTHENTICATED);

        when(aisConsentMapper.mapToAisConsent(spiAccountConsent)).thenReturn(aisConsentTO);
        when(consentRestClient.startSCA(spiAccountConsent.getId(), aisConsentTO)).thenReturn(ResponseEntity.badRequest().build());

        // When
        SpiResponse<SpiPsuAuthorisationResponse> actual = spi.authorisePsu(SPI_CONTEXT_DATA, spiPsuData, password, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.FORMAT_ERROR_UNKNOWN_ACCOUNT, actual.getErrors().get(0).getErrorCode());

        verify(authorisationService).authorisePsuForConsent(spiPsuData, password, CONSENT_ID, OpTypeTO.CONSENT, spiAspspConsentDataProvider);
        verify(authRequestInterceptor).setAccessToken(scaConsentResponseTO.getBearerToken().getAccess_token());
        verify(consentRestClient).startSCA(CONSENT_ID, aisConsentTO);
        verify(authRequestInterceptor).setAccessToken(null);
    }

    @Test
    public void authorisePsu_feignExceptionOnGetSCAConsentResponse() {
        // Given
        SpiPsuData spiPsuData = SpiPsuData.builder().psuId("psuId").build();

        String password = "password";

        SCAConsentResponseTO scaConsentResponseFromLoginResponse = new SCAConsentResponseTO();
        scaConsentResponseFromLoginResponse.setScaStatus(PSUAUTHENTICATED);

        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, false))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, "message"));


        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);


        SCAConsentResponseTO startScaResponse = new SCAConsentResponseTO();
        startScaResponse.setScaStatus(PSUAUTHENTICATED);


        // When
        SpiResponse<SpiPsuAuthorisationResponse> actualResponse = spi.authorisePsu(SPI_CONTEXT_DATA, spiPsuData, password, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actualResponse.hasError());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actualResponse.getErrors().get(0).getErrorCode());
    }

    @Test
    public void authorisePsu_failureResponseOnAuthorisingPsuForConsent() {
        // Given
        SpiPsuData spiPsuData = SpiPsuData.builder().psuId("psuId").build();
        SCAConsentResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);

        String password = "password";

        SCAConsentResponseTO scaConsentResponseFromLoginResponse = new SCAConsentResponseTO();
        scaConsentResponseFromLoginResponse.setScaStatus(PSUAUTHENTICATED);

        when(authorisationService.authorisePsuForConsent(spiPsuData, password, CONSENT_ID, OpTypeTO.CONSENT, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPsuAuthorisationResponse>builder().error(new TppMessage(MessageErrorCode.PSU_CREDENTIALS_INVALID)).build());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, false))
                .thenReturn(scaConsentResponseTO);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        SCAConsentResponseTO startScaResponse = new SCAConsentResponseTO();
        startScaResponse.setScaStatus(PSUAUTHENTICATED);

        // When
        SpiResponse<SpiPsuAuthorisationResponse> actualResponse = spi.authorisePsu(SPI_CONTEXT_DATA, spiPsuData, password, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertFalse(actualResponse.hasError());
        assertEquals(SPI_PSU_AUTHORISATION_FAILURE_RESPONSE, actualResponse.getPayload());

        verify(authorisationService).authorisePsuForConsent(spiPsuData, password, CONSENT_ID, OpTypeTO.CONSENT, spiAspspConsentDataProvider);
    }

    @Test
    public void authorisePsu_errorOnRetrievingTokenWhenMappingToScaResponse() throws IOException {
        // Given
        SpiPsuData spiPsuData = SpiPsuData.builder().psuId("psuId").build();
        SCAConsentResponseTO scaConsentResponseTO = new SCAConsentResponseTO();
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("some token");
        scaConsentResponseTO.setBearerToken(bearerTokenTO);
        String password = "password";

        SCAConsentResponseTO scaConsentResponseFromLoginResponse = new SCAConsentResponseTO();
        scaConsentResponseFromLoginResponse.setScaStatus(PSUAUTHENTICATED);

        when(authorisationService.authorisePsuForConsent(spiPsuData, password, CONSENT_ID, OpTypeTO.CONSENT, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPsuAuthorisationResponse>builder().payload(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS)).build());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, false))
                .thenReturn(scaConsentResponseTO);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        when(tokenStorageService.fromBytes(CONSENT_DATA_BYTES, SCALoginResponseTO.class)).thenThrow(new IOException());

        SCAConsentResponseTO startScaResponse = new SCAConsentResponseTO();
        startScaResponse.setScaStatus(PSUAUTHENTICATED);

        // When
        SpiResponse<SpiPsuAuthorisationResponse> actualResponse = spi.authorisePsu(SPI_CONTEXT_DATA, spiPsuData, password, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actualResponse.hasError());
        assertEquals(MessageErrorCode.FORMAT_ERROR_RESPONSE_TYPE, actualResponse.getErrors().get(0).getErrorCode());

        verify(authorisationService).authorisePsuForConsent(spiPsuData, password, CONSENT_ID, OpTypeTO.CONSENT, spiAspspConsentDataProvider);
    }

    @Test
    public void revokeAisConsent() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        SCAConsentResponseTO initialConsentResponseTO = new SCAConsentResponseTO();
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, false)).thenReturn(initialConsentResponseTO);

        ArgumentCaptor<SCAConsentResponseTO> consentResponseCaptor = ArgumentCaptor.forClass(SCAConsentResponseTO.class);

        // When
        SpiResponse<SpiResponse.VoidResponse> actual = spi.revokeAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        verify(consentDataService).store(consentResponseCaptor.capture());

        assertFalse(actual.hasError());
    }

    @Test
    public void revokeAisConsent_feignException() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, false))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, "message"));

        // When
        SpiResponse<SpiResponse.VoidResponse> actual = spi.revokeAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    public void verifyScaAuthorisation() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        SCAConsentResponseTO initialConsentResponseTO = new SCAConsentResponseTO();
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token(ACCESS_TOKEN);
        initialConsentResponseTO.setConsentId(CONSENT_ID);
        initialConsentResponseTO.setAuthorisationId("authorisation id");
        initialConsentResponseTO.setBearerToken(bearerTokenTO);
        initialConsentResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);

        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class))
                .thenReturn(initialConsentResponseTO);

        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        spiScaConfirmation.setTanNumber("tan");

        SCAConsentResponseTO authoriseConsentResponseTO = new SCAConsentResponseTO();
        authoriseConsentResponseTO.setScaStatus(PSUAUTHENTICATED);
        when(consentRestClient.authorizeConsent(CONSENT_ID, "authorisation id", "tan")).thenReturn(ResponseEntity.ok(authoriseConsentResponseTO));

        SpiVerifyScaAuthorisationResponse expected = new SpiVerifyScaAuthorisationResponse(ConsentStatus.VALID);

        // When
        SpiResponse<SpiVerifyScaAuthorisationResponse> actual = spi.verifyScaAuthorisation(SPI_CONTEXT_DATA, spiScaConfirmation, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertFalse(actual.hasError());
        assertEquals(expected, actual.getPayload());
    }

    @Test
    public void verifyScaAuthorisation_partiallyAuthorised() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        SCAConsentResponseTO initialConsentResponseTO = new SCAConsentResponseTO();
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token(ACCESS_TOKEN);
        initialConsentResponseTO.setConsentId(CONSENT_ID);
        initialConsentResponseTO.setAuthorisationId("authorisation id");
        initialConsentResponseTO.setBearerToken(bearerTokenTO);
        initialConsentResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);

        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class))
                .thenReturn(initialConsentResponseTO);

        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        spiScaConfirmation.setTanNumber("tan");

        SCAConsentResponseTO authoriseConsentResponseTO = new SCAConsentResponseTO();
        authoriseConsentResponseTO.setMultilevelScaRequired(true);
        authoriseConsentResponseTO.setPartiallyAuthorised(true);
        authoriseConsentResponseTO.setScaStatus(FINALISED);
        when(consentRestClient.authorizeConsent(CONSENT_ID, "authorisation id", "tan")).thenReturn(ResponseEntity.ok(authoriseConsentResponseTO));

        SpiVerifyScaAuthorisationResponse expected = new SpiVerifyScaAuthorisationResponse(ConsentStatus.PARTIALLY_AUTHORISED);

        // When
        SpiResponse<SpiVerifyScaAuthorisationResponse> actual = spi.verifyScaAuthorisation(SPI_CONTEXT_DATA, spiScaConfirmation, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertFalse(actual.hasError());
        assertEquals(expected, actual.getPayload());
    }

    @Test
    public void verifyScaAuthorisation_feignException() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        SCAConsentResponseTO initialConsentResponseTO = new SCAConsentResponseTO();
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token(ACCESS_TOKEN);
        initialConsentResponseTO.setConsentId(CONSENT_ID);
        initialConsentResponseTO.setAuthorisationId("authorisation id");
        initialConsentResponseTO.setBearerToken(bearerTokenTO);
        initialConsentResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);

        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, "message"));

        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        spiScaConfirmation.setTanNumber("tan");

        // When
        SpiResponse<SpiVerifyScaAuthorisationResponse> actual = spi.verifyScaAuthorisation(SPI_CONTEXT_DATA, spiScaConfirmation, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    public void requestAvailableScaMethods_success() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAConsentResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaConsentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, true)).thenReturn(scaConsentResponseTO);

        when(authorisationService.validateToken(ACCESS_TOKEN)).thenReturn(scaConsentResponseTO.getBearerToken());
        when(scaMethodConverter.toAuthenticationObjectList(Collections.emptyList())).thenReturn(Collections.emptyList());
        byte[] responseBytes = "response_byte".getBytes();
        when(consentDataService.store(scaConsentResponseTO)).thenReturn(responseBytes);
        doNothing().when(spiAspspConsentDataProvider).updateAspspConsentData(responseBytes);

        SpiResponse<SpiAvailableScaMethodsResponse> actual = spi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                            spiAccountConsent, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());

        verify(spiAspspConsentDataProvider).loadAspspConsentData();
        verify(consentDataService).response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, true);
        verify(authorisationService).validateToken(ACCESS_TOKEN);
        verify(consentDataService).store(scaConsentResponseTO);
        verify(spiAspspConsentDataProvider).updateAspspConsentData(responseBytes);
    }

    @Test
    public void requestAvailableScaMethods_scaMethodUnknown() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAConsentResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaConsentResponseTO.setScaMethods(null);
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, true)).thenReturn(scaConsentResponseTO);

        SpiResponse<SpiAvailableScaMethodsResponse> actual = spi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                            spiAccountConsent, spiAspspConsentDataProvider);

        assertFalse(actual.hasError());
        assertEquals(Collections.emptyList(), actual.getPayload().getAvailableScaMethods());

        verify(spiAspspConsentDataProvider).loadAspspConsentData();
        verify(consentDataService).response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, true);
        verify(authorisationService, never()).validateToken(anyString());
        verify(consentDataService, never()).store(any());
        verify(spiAspspConsentDataProvider, never()).updateAspspConsentData(any());
    }

    @Test
    public void requestAvailableScaMethods_feignException() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAConsentResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaConsentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, true))
                .thenReturn(scaConsentResponseTO);

        when(authorisationService.validateToken(ACCESS_TOKEN))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message"));

        SpiResponse<SpiAvailableScaMethodsResponse> actual = spi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                            spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.FORMAT_ERROR_SCA_METHODS, actual.getErrors().get(0).getErrorCode());

        verify(spiAspspConsentDataProvider).loadAspspConsentData();
        verify(consentDataService).response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, true);
        verify(authorisationService).validateToken(ACCESS_TOKEN);
        verify(consentDataService, never()).store(any());
        verify(spiAspspConsentDataProvider, never()).updateAspspConsentData(any());
    }

    @Test
    public void requestAuthorisationCode() {
        // Given

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        SCAConsentResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, true)).thenReturn(scaConsentResponseTO);

        SpiAuthorizationCodeResult expected = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        expected.setChallengeData(challengeData);

        when(consentRestClient.selectMethod(CONSENT_ID, "authorisation id", "authentication method id"))
                .thenReturn(ResponseEntity.ok(scaConsentResponseTO));

        when(authorisationService.returnScaMethodSelection(spiAspspConsentDataProvider, scaConsentResponseTO))
                .thenReturn(SpiResponse.<SpiAuthorizationCodeResult>builder()
                                    .payload(expected)
                                    .build());

        // When
        SpiResponse<SpiAuthorizationCodeResult> actual = spi.requestAuthorisationCode(SPI_CONTEXT_DATA, AUTHENTICATION_METHOD_ID, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertFalse(actual.hasError());
        assertEquals(expected, actual.getPayload());
    }

    @Test
    public void requestAuthorisationCode_feignException501() {
        // Given

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        SCAConsentResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, true)).thenReturn(scaConsentResponseTO);

        SpiAuthorizationCodeResult expected = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        expected.setChallengeData(challengeData);

        when(consentRestClient.selectMethod(CONSENT_ID, "authorisation id", "authentication method id"))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.NOT_IMPLEMENTED, "message"));

        // When
        SpiResponse<SpiAuthorizationCodeResult> actual = spi.requestAuthorisationCode(SPI_CONTEXT_DATA, AUTHENTICATION_METHOD_ID, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.SCA_METHOD_UNKNOWN, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    public void requestAuthorisationCode_feignException400() {
        // Given

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        SCAConsentResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, true)).thenReturn(scaConsentResponseTO);

        SpiAuthorizationCodeResult expected = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        expected.setChallengeData(challengeData);

        when(consentRestClient.selectMethod(CONSENT_ID, "authorisation id", "authentication method id"))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message"));

        // When
        SpiResponse<SpiAuthorizationCodeResult> actual = spi.requestAuthorisationCode(SPI_CONTEXT_DATA, AUTHENTICATION_METHOD_ID, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.FORMAT_ERROR, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    public void requestAuthorisationCode_feignException404() {
        // Given

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        SCAConsentResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, true)).thenReturn(scaConsentResponseTO);

        SpiAuthorizationCodeResult expected = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        expected.setChallengeData(challengeData);

        when(consentRestClient.selectMethod(CONSENT_ID, "authorisation id", "authentication method id"))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.NOT_FOUND, "message"));

        // When
        SpiResponse<SpiAuthorizationCodeResult> actual = spi.requestAuthorisationCode(SPI_CONTEXT_DATA, AUTHENTICATION_METHOD_ID, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    public void requestAuthorisationCode_noBearerTokenInSelectMethodResponse() {
        // Given

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        SCAConsentResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, true)).thenReturn(scaConsentResponseTO);

        SpiAuthorizationCodeResult expected = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        expected.setChallengeData(challengeData);

        SCAConsentResponseTO selectMethodScaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED, null);
        when(consentRestClient.selectMethod(CONSENT_ID, "authorisation id", "authentication method id"))
                .thenReturn(ResponseEntity.ok(selectMethodScaConsentResponseTO));

        when(authorisationService.returnScaMethodSelection(spiAspspConsentDataProvider, scaConsentResponseTO))
                .thenReturn(SpiResponse.<SpiAuthorizationCodeResult>builder()
                                    .payload(expected)
                                    .build());

        // When
        SpiResponse<SpiAuthorizationCodeResult> actual = spi.requestAuthorisationCode(SPI_CONTEXT_DATA, AUTHENTICATION_METHOD_ID, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertFalse(actual.hasError());
        assertEquals(expected, actual.getPayload());
    }

    @Test
    public void startScaDecoupled_success() {
        SpiAspspConsentDataProvider spiAspspConsentDataProviderWithEncryptedId = new SpiAspspConsentDataProviderWithEncryptedId(spiAspspConsentDataProvider, CONSENT_ID);
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAConsentResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, true)).thenReturn(scaConsentResponseTO);

        doNothing().when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        when(consentRestClient.selectMethod(CONSENT_ID, "authorisation id", "authentication method id"))
                .thenReturn(ResponseEntity.ok(scaConsentResponseTO));
        SpiAuthorizationCodeResult payload = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        payload.setChallengeData(challengeData);
        when(authorisationService.returnScaMethodSelection(spiAspspConsentDataProviderWithEncryptedId, scaConsentResponseTO))
                .thenReturn(SpiResponse.<SpiAuthorizationCodeResult>builder()
                                    .payload(payload)
                                    .build());

        ReflectionTestUtils.setField(spi, "onlineBankingUrl", "some.url");

        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = spi.startScaDecoupled(SPI_CONTEXT_DATA, "authorisation id", "authentication method id",
                                                                                         spiAccountConsent, spiAspspConsentDataProviderWithEncryptedId);

        assertFalse(actual.hasError());

        verify(spiAspspConsentDataProvider).loadAspspConsentData();
        verify(consentDataService).response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, true);
        verify(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        verify(consentRestClient).selectMethod(CONSENT_ID, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID);
        verify(authorisationService).returnScaMethodSelection(spiAspspConsentDataProviderWithEncryptedId, scaConsentResponseTO);
    }

    @Test
    public void startScaDecoupled_errorOnReturningScaMethodSelection() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAConsentResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);

        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, true)).thenReturn(scaConsentResponseTO);

        doNothing().when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        when(consentRestClient.selectMethod(CONSENT_ID, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID))
                .thenReturn(ResponseEntity.ok(scaConsentResponseTO));
        FeignException feignException = FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message");
        when(authorisationService.returnScaMethodSelection(spiAspspConsentDataProvider, scaConsentResponseTO))
                .thenThrow(feignException);
        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn("message");


        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = spi.startScaDecoupled(SPI_CONTEXT_DATA, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID,
                                                                                         spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals("", actual.getErrors().get(0).getMessageText());

        verify(spiAspspConsentDataProvider).loadAspspConsentData();
        verify(consentDataService).response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, true);
        verify(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        verify(consentRestClient).selectMethod(CONSENT_ID, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID);
        verify(authorisationService).returnScaMethodSelection(spiAspspConsentDataProvider, scaConsentResponseTO);
        verify(feignExceptionReader).getErrorMessage(any(FeignException.class));
    }

    @Test
    public void startScaDecoupled_scaSelected() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        SCAConsentResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.FINALISED);
        when(consentDataService.response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, true)).thenReturn(scaConsentResponseTO);

        SpiAuthorizationCodeResult payload = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        payload.setChallengeData(challengeData);

        SpiAspspConsentDataProvider spiAspspConsentDataProviderWithEncryptedId = new SpiAspspConsentDataProviderWithEncryptedId(spiAspspConsentDataProvider, CONSENT_ID);

        when(authorisationService.getResponseIfScaSelected(spiAspspConsentDataProviderWithEncryptedId, scaConsentResponseTO))
                .thenReturn(SpiResponse.<SpiAuthorizationCodeResult>builder()
                                    .payload(payload)
                                    .build());

        ReflectionTestUtils.setField(spi, ONLINE_BANKING_URL_FIELD_NAME, ONLINE_BANKING_URL_VALUE);

        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = spi.startScaDecoupled(SPI_CONTEXT_DATA, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID,
                                                                                         spiAccountConsent, spiAspspConsentDataProviderWithEncryptedId);

        assertFalse(actual.hasError());

        verify(spiAspspConsentDataProvider).loadAspspConsentData();
        verify(consentDataService).response(CONSENT_DATA_BYTES, SCAConsentResponseTO.class, true);
        verify(authorisationService).getResponseIfScaSelected(spiAspspConsentDataProviderWithEncryptedId, scaConsentResponseTO);
    }

    @Test
    public void requestAvailableScaMethods_authenticationMethodIdIsNull() {
        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = spi.startScaDecoupled(SPI_CONTEXT_DATA, AUTHORISATION_ID, null,
                                                                                         spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.SERVICE_NOT_SUPPORTED, actual.getErrors().get(0).getErrorCode());
    }

    private Response buildErrorResponse() {
        return Response.builder()
                       .status(404)
                       .request(Request.create(Request.HttpMethod.GET, "", Collections.emptyMap(), null))
                       .headers(Collections.emptyMap())
                       .build();
    }

    private SCAConsentResponseTO buildSCAConsentResponseTO(ScaStatusTO scaStatusTO) {
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        return buildSCAConsentResponseTO(scaStatusTO, bearerToken);
    }

    private SCAConsentResponseTO buildSCAConsentResponseTO(ScaStatusTO scaStatusTO, BearerTokenTO bearerTokenTO) {
        SCAConsentResponseTO scaConsentResponseTO = new SCAConsentResponseTO();
        scaConsentResponseTO.setConsentId(CONSENT_ID);
        scaConsentResponseTO.setAuthorisationId(AUTHORISATION_ID);
        scaConsentResponseTO.setScaStatus(scaStatusTO);
        scaConsentResponseTO.setBearerToken(bearerTokenTO);
        return scaConsentResponseTO;
    }

    private List<AccountDetailsTO> buildListOfAccounts() {
        return Collections.singletonList(jsonReader.getObjectFromFile("json/spi/impl/account-details.json", AccountDetailsTO.class));
    }

    private List<SpiAccountDetails> buildSpiAccountDetails() {
        return Collections.singletonList(jsonReader.getObjectFromFile("json/spi/impl/spi-account-details.json", SpiAccountDetails.class));
    }
}