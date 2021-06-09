package de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation;

import de.adorsys.aspsp.xs2a.connector.spi.converter.AisConsentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.aspsp.xs2a.connector.spi.converter.SpiScaStatusResponseMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.*;
import de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation.confirmation.ConsentAuthConfirmationCodeService;
import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.aspsp.xs2a.util.TestSpiDataProvider;
import de.adorsys.ledgers.keycloak.client.api.KeycloakTokenService;
import de.adorsys.ledgers.middleware.api.domain.account.AccountDetailsTO;
import de.adorsys.ledgers.middleware.api.domain.sca.*;
import de.adorsys.ledgers.middleware.api.domain.um.AisConsentTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.middleware.api.domain.um.ScaMethodTypeTO;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.ledgers.rest.client.AccountRestClient;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.ConsentRestClient;
import de.adorsys.ledgers.rest.client.RedirectScaRestClient;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.sca.ChallengeData;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountDetails;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.*;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiAccountAccess;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiConsentConfirmationCodeValidationResponse;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiInitiateAisConsentResponse;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiVerifyScaAuthorisationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO.FINALISED;
import static de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO.PSUAUTHENTICATED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AisConsentSpiImplTest {
    private static final String ACCESS_TOKEN = "access_token";
    private static final String AUTHORISATION_ID = "authorisation id";
    private static final String AUTHENTICATION_METHOD_ID = "authentication method id";

    private static final String CONSENT_ID = "c966f143-f6a2-41db-9036-8abaeeef3af7";
    private static final byte[] CONSENT_DATA_BYTES = "consent_data".getBytes();

    private static final String CONFIRMATION_CODE = "code";

    private static final SpiContextData SPI_CONTEXT_DATA = TestSpiDataProvider.getSpiContextData();
    private static final String RESPONSE_STATUS_200_WITH_EMPTY_BODY = "Response status was 200, but the body was empty!";
    private static final String ONLINE_BANKING_URL_FIELD_NAME = "onlineBankingUrl";
    private static final String ONLINE_BANKING_URL_VALUE = "some.url";
    private static final String PSU_MESSAGE_FROM_LEDGERS = "Your Login for CONSENT id: cd9eb664-660c-48a6-878d-ddc74273315c is successful";
    private static final ScaStatus SCA_STATUS_FROM_LEDGERS = ScaStatus.PSUAUTHENTICATED;
    private static final String PSU_MESSAGE_MOCKED = "Mocked PSU message from SPI.";
    private static final ScaStatus SCA_STATUS_FROM_CMS = ScaStatus.FAILED;

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
    private RedirectScaRestClient redirectScaRestClient;
    @Spy
    private AisConsentMapper aisConsentMapper = Mappers.getMapper(AisConsentMapper.class);
    @Mock
    private AuthRequestInterceptor authRequestInterceptor;
    @Mock
    private AspspConsentDataService consentDataService;
    @Mock
    private GeneralAuthorisationService authorisationService;
    @Mock
    private GlobalScaResponseTO scaResponseTO;
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
    @Mock
    private KeycloakTokenService keycloakTokenService;
    @Mock
    private ConsentAuthConfirmationCodeService authConfirmationCodeService;
    @Mock
    private SpiScaStatusResponseMapper spiScaStatusResponseMapper;

    @Test
    void initiateAisConsent_WithInitialAspspConsentData() {
        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        token.setAccess_token(ACCESS_TOKEN);
        sca.setBearerToken(token);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentRestClient.initiateAisConsent(CONSENT_ID, aisConsentMapper.mapToAisConsent(spiAccountConsent)))
                .thenReturn(ResponseEntity.ok(sca));
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        when(redirectScaRestClient.startSca(any(StartScaOprTO.class))).thenReturn(ResponseEntity.ok(globalScaResponseTO));
        when(consentDataService.store(globalScaResponseTO)).thenReturn(CONSENT_DATA_BYTES);

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(redirectScaRestClient, times((1))).startSca(any(StartScaOprTO.class));
        verify(authRequestInterceptor).setAccessToken(null);
    }

    @Test
    void initiateAisConsent_WithInitialAspspConsentData_availableAccounts() {
        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        token.setAccess_token(ACCESS_TOKEN);
        sca.setBearerToken(token);

        List<AccountDetailsTO> accountDetailsTOS = buildListOfAccounts();
        List<SpiAccountDetails> spiAccountDetails = buildSpiAccountDetails();

        SpiAccountConsent spiAccountConsent = Mockito.spy(spiAccountConsentAvailableAccounts);

        SpiAccountAccess spiAccountAccess = Mockito.spy(spiAccountConsentAvailableAccounts.getAccess());
        spiAccountAccess.setAccounts(createAccountReferenceList());
        spiAccountAccess.setBalances(createAccountReferenceList());
        spiAccountAccess.setTransactions(createAccountReferenceList());

        spiAccountConsent.setAccess(spiAccountAccess);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentRestClient.initiateAisConsent(CONSENT_ID, aisConsentMapper.mapToAisConsent(spiAccountConsent)))
                .thenReturn(ResponseEntity.ok(sca));
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        when(accountMapper.toSpiAccountDetails(accountDetailsTOS.get(0))).thenReturn(spiAccountDetails.get(0));
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.of(Optional.of(accountDetailsTOS)));

        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        when(redirectScaRestClient.startSca(any(StartScaOprTO.class))).thenReturn(ResponseEntity.ok(globalScaResponseTO));
        when(consentDataService.store(globalScaResponseTO)).thenReturn(CONSENT_DATA_BYTES);

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(redirectScaRestClient, times((1))).startSca(any(StartScaOprTO.class));
        verify(authRequestInterceptor).setAccessToken(null);

        List<SpiAccountReference> spiAccountReferences = spiAccountDetails.stream().map(SpiAccountReference::new).collect(Collectors.toList());
        verify(spiAccountConsent, times(4)).getAccess();
        verify(spiAccountAccess, times(2)).setAccounts(spiAccountReferences);
    }

    @Test
    void initiateAisConsent_WithInitialAspspConsentData_availableAccountsWithBalances() {
        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        token.setAccess_token(ACCESS_TOKEN);
        sca.setBearerToken(token);

        List<AccountDetailsTO> accountDetailsTOS = buildListOfAccounts();
        List<SpiAccountDetails> spiAccountDetails = buildSpiAccountDetails();

        SpiAccountConsent spiAccountConsent = Mockito.spy(spiAccountConsentAvailableAccountsWithBalances);
        SpiAccountAccess spiAccountAccess = Mockito.spy(spiAccountConsentAvailableAccountsWithBalances.getAccess());

        spiAccountAccess.setAccounts(createAccountReferenceList());
        spiAccountAccess.setBalances(createAccountReferenceList());
        spiAccountAccess.setTransactions(createAccountReferenceList());

        spiAccountConsent.setAccess(spiAccountAccess);

        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentRestClient.initiateAisConsent(CONSENT_ID, aisConsentMapper.mapToAisConsent(spiAccountConsent)))
                .thenReturn(ResponseEntity.ok(sca));
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        when(accountMapper.toSpiAccountDetails(accountDetailsTOS.get(0))).thenReturn(spiAccountDetails.get(0));
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.of(Optional.of(accountDetailsTOS)));

        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        when(redirectScaRestClient.startSca(any(StartScaOprTO.class))).thenReturn(ResponseEntity.ok(globalScaResponseTO));
        when(consentDataService.store(globalScaResponseTO)).thenReturn(CONSENT_DATA_BYTES);

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(redirectScaRestClient, times((1))).startSca(any(StartScaOprTO.class));
        verify(authRequestInterceptor).setAccessToken(null);

        List<SpiAccountReference> spiAccountReferences = spiAccountDetails.stream().map(SpiAccountReference::new).collect(Collectors.toList());
        verify(spiAccountConsent, times(4)).getAccess();
        verify(spiAccountAccess, times(2)).setAccounts(spiAccountReferences);
    }

    @Test
    void initiateAisConsent_WithInitialAspspConsentData_global() {
        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        token.setAccess_token(ACCESS_TOKEN);
        sca.setBearerToken(token);

        List<AccountDetailsTO> accountDetailsTOS = buildListOfAccounts();
        List<SpiAccountDetails> spiAccountDetails = buildSpiAccountDetails();

        SpiAccountConsent spiAccountConsent = Mockito.spy(spiAccountConsentGlobal);
        SpiAccountAccess spiAccountAccess = Mockito.spy(spiAccountConsentGlobal.getAccess());
        spiAccountAccess.setAccounts(createAccountReferenceList());
        spiAccountAccess.setBalances(createAccountReferenceList());
        spiAccountAccess.setTransactions(createAccountReferenceList());

        spiAccountConsent.setAccess(spiAccountAccess);

        when(spiAccountConsent.getId()).thenReturn(CONSENT_ID);
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentRestClient.initiateAisConsent(CONSENT_ID, aisConsentMapper.mapToAisConsent(spiAccountConsent)))
                .thenReturn(ResponseEntity.ok(sca));
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        when(accountMapper.toSpiAccountDetails(accountDetailsTOS.get(0))).thenReturn(spiAccountDetails.get(0));
        when(accountRestClient.getListOfAccounts()).thenReturn(ResponseEntity.of(Optional.of(accountDetailsTOS)));

        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        when(redirectScaRestClient.startSca(any(StartScaOprTO.class))).thenReturn(ResponseEntity.ok(globalScaResponseTO));
        when(consentDataService.store(globalScaResponseTO)).thenReturn(CONSENT_DATA_BYTES);

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(redirectScaRestClient, times((1))).startSca(any(StartScaOprTO.class));
        verify(authRequestInterceptor).setAccessToken(null);

        List<SpiAccountReference> spiAccountReferences = spiAccountDetails.stream().map(SpiAccountReference::new).collect(Collectors.toList());
        verify(spiAccountConsent, times(4)).getAccess();
        verify(spiAccountAccess, times(2)).setAccounts(spiAccountReferences);
        verify(spiAccountAccess, times(2)).setTransactions(spiAccountReferences);
        verify(spiAccountAccess, times(2)).setBalances(spiAccountReferences);
    }


    @Test
    void initiateAisConsent_WithEmptyInitialAspspConsentData() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(new byte[]{});
        when(multilevelScaService.isMultilevelScaRequired(SPI_CONTEXT_DATA.getPsuData(), Collections.singleton(spiAccountConsent.getAccess().getAccounts().get(0)))).thenReturn(true);

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(spiAspspConsentDataProvider).updateAspspConsentData(any());
    }

    @Test
    void initiateAisConsent_WithEmptyInitialAspspConsentDataAndFeignException() {
        when(multilevelScaService.isMultilevelScaRequired(SPI_CONTEXT_DATA.getPsuData(), Collections.singleton(spiAccountConsent.getAccess().getAccounts().get(0)))).thenThrow(getFeignException());

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        assertFalse(actualResponse.getErrors().isEmpty());
        assertNull(actualResponse.getPayload());
        assertTrue(actualResponse.getErrors().contains(new TppMessage(MessageErrorCode.FORMAT_ERROR_UNKNOWN_ACCOUNT)));
    }

    @Test
    void initiateAisConsent_WithException() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(consentRestClient.initiateAisConsent(CONSENT_ID, aisConsentMapper.mapToAisConsent(spiAccountConsent)))
                .thenThrow(FeignException.errorStatus(RESPONSE_STATUS_200_WITH_EMPTY_BODY,
                                                      buildErrorResponse()));

        SpiResponse<SpiInitiateAisConsentResponse> actualResponse = spi.initiateAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        assertFalse(actualResponse.getErrors().isEmpty());
        assertNull(actualResponse.getPayload());
    }

    @Test
    void authorisePsu() {
        // Given
        SpiPsuData spiPsuData = SpiPsuData.builder().psuId("psuId").build();
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        String password = "password";

        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        token.setAccess_token(ACCESS_TOKEN);
        sca.setBearerToken(token);

        when(keycloakTokenService.login(spiPsuData.getPsuId(), password)).thenReturn(token);

        spiAccountConsent.setId(CONSENT_ID);
        when(consentRestClient.initiateAisConsent(eq(CONSENT_ID), any(AisConsentTO.class)))
                .thenReturn(ResponseEntity.ok(sca));
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        GlobalScaResponseTO globalScaResponseTO = new GlobalScaResponseTO();
        when(redirectScaRestClient.startSca(any(StartScaOprTO.class))).thenReturn(ResponseEntity.ok(globalScaResponseTO));


        when(authorisationService.authorisePsuInternal(CONSENT_ID, AUTHORISATION_ID, OpTypeTO.CONSENT, globalScaResponseTO, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPsuAuthorisationResponse>builder().payload(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS)).build());

        // When
        SpiResponse<SpiPsuAuthorisationResponse> actualResponse = spi.authorisePsu(SPI_CONTEXT_DATA, AUTHORISATION_ID, spiPsuData, password, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertFalse(actualResponse.hasError());
        assertEquals(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS), actualResponse.getPayload());

        verify(authRequestInterceptor, times(3)).setAccessToken(scaConsentResponseTO.getBearerToken().getAccess_token());
        verify(consentRestClient).initiateAisConsent(eq(CONSENT_ID), any(AisConsentTO.class));
        verify(redirectScaRestClient).startSca(any(StartScaOprTO.class));
        verify(authorisationService).authorisePsuInternal(CONSENT_ID, AUTHORISATION_ID, OpTypeTO.CONSENT, globalScaResponseTO, spiAspspConsentDataProvider);
        verify(authRequestInterceptor).setAccessToken(null);
    }

    @Test
    void authorisePsu_feignExceptionOnGetSCAConsentResponse() {
        // Given
        SpiPsuData spiPsuData = SpiPsuData.builder().psuId("psuId").build();
        String password = "password";
        SCAConsentResponseTO sca = new SCAConsentResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        sca.setBearerToken(token);
        token.setAccess_token(ACCESS_TOKEN);
        when(keycloakTokenService.login(spiPsuData.getPsuId(), password)).thenReturn(token);
        authRequestInterceptor.setAccessToken(ACCESS_TOKEN);

        spiAccountConsent.setId(CONSENT_ID);
        when(consentRestClient.initiateAisConsent(eq(CONSENT_ID), any(AisConsentTO.class)))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, "message"));

        // When
        SpiResponse<SpiPsuAuthorisationResponse> actualResponse = spi.authorisePsu(SPI_CONTEXT_DATA, AUTHORISATION_ID, spiPsuData, password, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertFalse(actualResponse.hasError());
        assertEquals(SpiAuthorisationStatus.FAILURE, actualResponse.getPayload().getSpiAuthorisationStatus());
    }

    @Test
    void revokeAisConsent() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        GlobalScaResponseTO initialConsentResponseTO = new GlobalScaResponseTO();
        when(consentDataService.response(CONSENT_DATA_BYTES, false)).thenReturn(initialConsentResponseTO);

        ArgumentCaptor<GlobalScaResponseTO> consentResponseCaptor = ArgumentCaptor.forClass(GlobalScaResponseTO.class);

        // When
        SpiResponse<SpiResponse.VoidResponse> actual = spi.revokeAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        verify(consentDataService).store(consentResponseCaptor.capture());

        assertFalse(actual.hasError());
    }

    @Test
    void revokeAisConsent_feignException() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        when(consentDataService.response(CONSENT_DATA_BYTES, false))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, "message"));

        // When
        SpiResponse<SpiResponse.VoidResponse> actual = spi.revokeAisConsent(SPI_CONTEXT_DATA, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void verifyScaAuthorisation() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        GlobalScaResponseTO initialConsentResponseTO = new GlobalScaResponseTO();
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token(ACCESS_TOKEN);
        initialConsentResponseTO.setOperationObjectId(CONSENT_ID);
        initialConsentResponseTO.setAuthorisationId("authorisation id");
        initialConsentResponseTO.setBearerToken(bearerTokenTO);
        initialConsentResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);

        when(consentDataService.response(CONSENT_DATA_BYTES))
                .thenReturn(initialConsentResponseTO);

        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        spiScaConfirmation.setTanNumber("tan");

        GlobalScaResponseTO authoriseConsentResponseTO = new GlobalScaResponseTO();
        authoriseConsentResponseTO.setScaStatus(PSUAUTHENTICATED);
        when(redirectScaRestClient.validateScaCode("authorisation id", "tan")).thenReturn(ResponseEntity.ok(authoriseConsentResponseTO));

        SpiVerifyScaAuthorisationResponse expected = new SpiVerifyScaAuthorisationResponse(ConsentStatus.VALID);

        // When
        SpiResponse<SpiVerifyScaAuthorisationResponse> actual = spi.verifyScaAuthorisation(SPI_CONTEXT_DATA, spiScaConfirmation, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertFalse(actual.hasError());
        assertEquals(expected, actual.getPayload());
    }

    @Test
    void verifyScaAuthorisation_partiallyAuthorised() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        GlobalScaResponseTO initialConsentResponseTO = new GlobalScaResponseTO();
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token(ACCESS_TOKEN);
        initialConsentResponseTO.setOperationObjectId(CONSENT_ID);
        initialConsentResponseTO.setAuthorisationId("authorisation id");
        initialConsentResponseTO.setBearerToken(bearerTokenTO);
        initialConsentResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);

        when(consentDataService.response(CONSENT_DATA_BYTES))
                .thenReturn(initialConsentResponseTO);

        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        spiScaConfirmation.setTanNumber("tan");

        GlobalScaResponseTO authoriseConsentResponseTO = new GlobalScaResponseTO();
        authoriseConsentResponseTO.setMultilevelScaRequired(true);
        authoriseConsentResponseTO.setPartiallyAuthorised(true);
        authoriseConsentResponseTO.setScaStatus(FINALISED);
        when(redirectScaRestClient.validateScaCode("authorisation id", "tan")).thenReturn(ResponseEntity.ok(authoriseConsentResponseTO));

        SpiVerifyScaAuthorisationResponse expected = new SpiVerifyScaAuthorisationResponse(ConsentStatus.PARTIALLY_AUTHORISED);

        // When
        SpiResponse<SpiVerifyScaAuthorisationResponse> actual = spi.verifyScaAuthorisation(SPI_CONTEXT_DATA, spiScaConfirmation, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertFalse(actual.hasError());
        assertEquals(expected, actual.getPayload());
    }

    @Test
    void verifyScaAuthorisation_feignException() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        GlobalScaResponseTO initialConsentResponseTO = new GlobalScaResponseTO();
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token(ACCESS_TOKEN);
        initialConsentResponseTO.setOperationObjectId(CONSENT_ID);
        initialConsentResponseTO.setAuthorisationId("authorisation id");
        initialConsentResponseTO.setBearerToken(bearerTokenTO);
        initialConsentResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);

        FeignException feignException = FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, "message");
        when(consentDataService.response(CONSENT_DATA_BYTES)).thenThrow(feignException);
        when(feignExceptionReader.getLedgersErrorCode(feignException)).thenReturn(LedgersErrorCode.PSU_AUTH_ATTEMPT_INVALID);

        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        spiScaConfirmation.setTanNumber("tan");

        // When
        SpiResponse<SpiVerifyScaAuthorisationResponse> actual = spi.verifyScaAuthorisation(SPI_CONTEXT_DATA, spiScaConfirmation, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void verifyScaAuthorisation_feignExceptionAttemptFailure() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        GlobalScaResponseTO initialConsentResponseTO = new GlobalScaResponseTO();
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token(ACCESS_TOKEN);
        initialConsentResponseTO.setOperationObjectId(CONSENT_ID);
        initialConsentResponseTO.setAuthorisationId("authorisation id");
        initialConsentResponseTO.setBearerToken(bearerTokenTO);
        initialConsentResponseTO.setScaStatus(ScaStatusTO.PSUIDENTIFIED);

        FeignException feignException = FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, "message");
        when(consentDataService.response(CONSENT_DATA_BYTES))
                .thenThrow(feignException);
        when(feignExceptionReader.getLedgersErrorCode(feignException)).thenReturn(LedgersErrorCode.SCA_VALIDATION_ATTEMPT_FAILED);

        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        spiScaConfirmation.setTanNumber("tan");

        // When
        SpiResponse<SpiVerifyScaAuthorisationResponse> actual = spi.verifyScaAuthorisation(SPI_CONTEXT_DATA, spiScaConfirmation, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());
        assertEquals(SpiAuthorisationStatus.ATTEMPT_FAILURE, actual.getPayload().getSpiAuthorisationStatus());
    }

    @Test
    void getScaStatusFromLedgers() {
        //Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, false))
                .thenReturn(scaConsentResponseTO);
        when(redirectScaRestClient.getSCA(AUTHORISATION_ID))
                .thenReturn(ResponseEntity.ok(scaConsentResponseTO));
        when(spiScaStatusResponseMapper.toSpiScaStatusResponse(ResponseEntity.ok(scaConsentResponseTO).getBody()))
                .thenReturn(getTestData_ledgersAnswer());

        //When
        SpiResponse<SpiScaStatusResponse> actual = spi.getScaStatus(SCA_STATUS_FROM_CMS, SPI_CONTEXT_DATA, AUTHORISATION_ID,
                                                                    spiAccountConsent, spiAspspConsentDataProvider);

        //Then
        assertFalse(actual.hasError());
        assertEquals(SCA_STATUS_FROM_LEDGERS, actual.getPayload().getScaStatus());
        assertEquals(PSU_MESSAGE_FROM_LEDGERS, actual.getPayload().getPsuMessage());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, false);
        verify(redirectScaRestClient).getSCA(AUTHORISATION_ID);
    }

    @Test
    void getScaStatusFromCms() {
        //Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(CONSENT_DATA_BYTES);
        when(consentDataService.response(CONSENT_DATA_BYTES, false))
                .thenReturn(null);

        //When
        SpiResponse<SpiScaStatusResponse> actual = spi.getScaStatus(SCA_STATUS_FROM_CMS, SPI_CONTEXT_DATA, AUTHORISATION_ID,
                                                                    spiAccountConsent, spiAspspConsentDataProvider);

        //Then
        assertFalse(actual.hasError());
        assertEquals(SCA_STATUS_FROM_CMS, actual.getPayload().getScaStatus());
        assertEquals(PSU_MESSAGE_MOCKED, actual.getPayload().getPsuMessage());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, false);
        verifyNoInteractions(redirectScaRestClient);
        verifyNoInteractions(spiScaStatusResponseMapper);
    }

    @Test
    void getScaStatus_feignException() {
        //Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, false))
                .thenReturn(scaConsentResponseTO);
        FeignException feignException = FeignExceptionHandler.getException(HttpStatus.NOT_FOUND, "message");
        when(redirectScaRestClient.getSCA(AUTHORISATION_ID))
                .thenThrow(feignException);


        //When
        SpiResponse<SpiScaStatusResponse> actual = spi.getScaStatus(SCA_STATUS_FROM_CMS, SPI_CONTEXT_DATA, AUTHORISATION_ID,
                                                                    spiAccountConsent, spiAspspConsentDataProvider);

        //Then
        assertFalse(actual.hasError());
        assertEquals(SCA_STATUS_FROM_CMS, actual.getPayload().getScaStatus());
        assertEquals(PSU_MESSAGE_MOCKED, actual.getPayload().getPsuMessage());

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(consentDataService, times(1)).response(CONSENT_DATA_BYTES, false);
        verify(redirectScaRestClient).getSCA(AUTHORISATION_ID);
        verifyNoInteractions(spiScaStatusResponseMapper);
    }

    @Test
    void requestAvailableScaMethods_success() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true))
                .thenReturn(scaConsentResponseTO);
        when(redirectScaRestClient.getSCA(AUTHORISATION_ID))
                .thenReturn(ResponseEntity.ok(scaConsentResponseTO));

        // When
        SpiResponse<SpiAvailableScaMethodsResponse> actual = spi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                            spiAccountConsent, spiAspspConsentDataProvider);
        // Then
        assertFalse(actual.hasError());

        verify(spiAspspConsentDataProvider, times(2)).loadAspspConsentData();
        verify(consentDataService, times(2)).response(CONSENT_DATA_BYTES, true);
        verify(redirectScaRestClient).getSCA(AUTHORISATION_ID);
    }

    @Test
    void requestAvailableScaMethods_scaMethodUnknown() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaConsentResponseTO.setScaMethods(null);
        when(consentDataService.response(CONSENT_DATA_BYTES, true))
                .thenReturn(scaConsentResponseTO);
        when(redirectScaRestClient.getSCA(AUTHORISATION_ID))
                .thenReturn(ResponseEntity.ok(scaConsentResponseTO));

        // When
        SpiResponse<SpiAvailableScaMethodsResponse> actual = spi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                            spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.SCA_METHOD_UNKNOWN_PROCESS_MISMATCH, actual.getErrors().get(0).getErrorCode());

        verify(spiAspspConsentDataProvider, times(2)).loadAspspConsentData();
        verify(consentDataService, times(2)).response(CONSENT_DATA_BYTES, true);
        verify(redirectScaRestClient).getSCA(AUTHORISATION_ID);
    }

    @Test
    void requestAvailableScaMethods_feignException() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        scaConsentResponseTO.setScaMethods(Collections.emptyList());
        when(consentDataService.response(CONSENT_DATA_BYTES, true))
                .thenReturn(scaConsentResponseTO);
        when(redirectScaRestClient.getSCA(AUTHORISATION_ID))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message"));

        // When
        SpiResponse<SpiAvailableScaMethodsResponse> actual = spi.requestAvailableScaMethods(SPI_CONTEXT_DATA,
                                                                                            spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.FORMAT_ERROR_SCA_METHODS, actual.getErrors().get(0).getErrorCode());

        verify(spiAspspConsentDataProvider, times(2)).loadAspspConsentData();
        verify(consentDataService, times(2)).response(CONSENT_DATA_BYTES, true);
        verify(redirectScaRestClient).getSCA(AUTHORISATION_ID);
    }

    @Test
    void requestAuthorisationCode() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaConsentResponseTO);

        SpiAuthorizationCodeResult expected = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        expected.setChallengeData(challengeData);

        when(redirectScaRestClient.selectMethod("authorisation id", "authentication method id"))
                .thenReturn(ResponseEntity.ok(scaConsentResponseTO));

        when(authorisationService.returnScaMethodSelection(spiAspspConsentDataProvider, scaConsentResponseTO, "authentication method id"))
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
    void requestAuthorisationCode_feignException501() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaConsentResponseTO);

        SpiAuthorizationCodeResult expected = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        expected.setChallengeData(challengeData);

        when(redirectScaRestClient.selectMethod("authorisation id", "authentication method id"))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.NOT_IMPLEMENTED, "message"));

        // When
        SpiResponse<SpiAuthorizationCodeResult> actual = spi.requestAuthorisationCode(SPI_CONTEXT_DATA, AUTHENTICATION_METHOD_ID, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.SCA_METHOD_UNKNOWN, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void requestAuthorisationCode_feignException400() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaConsentResponseTO);

        SpiAuthorizationCodeResult expected = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        expected.setChallengeData(challengeData);

        when(redirectScaRestClient.selectMethod("authorisation id", "authentication method id"))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message"));

        // When
        SpiResponse<SpiAuthorizationCodeResult> actual = spi.requestAuthorisationCode(SPI_CONTEXT_DATA, AUTHENTICATION_METHOD_ID, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.FORMAT_ERROR, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void requestAuthorisationCode_feignException404() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaConsentResponseTO);

        SpiAuthorizationCodeResult expected = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        expected.setChallengeData(challengeData);

        when(redirectScaRestClient.selectMethod("authorisation id", "authentication method id"))
                .thenThrow(FeignExceptionHandler.getException(HttpStatus.NOT_FOUND, "message"));

        // When
        SpiResponse<SpiAuthorizationCodeResult> actual = spi.requestAuthorisationCode(SPI_CONTEXT_DATA, AUTHENTICATION_METHOD_ID, spiAccountConsent, spiAspspConsentDataProvider);

        // Then
        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void requestAuthorisationCode_noBearerTokenInSelectMethodResponse() {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);

        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaConsentResponseTO);

        SpiAuthorizationCodeResult expected = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        expected.setChallengeData(challengeData);

        GlobalScaResponseTO selectMethodScaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED, null);
        when(redirectScaRestClient.selectMethod("authorisation id", "authentication method id"))
                .thenReturn(ResponseEntity.ok(selectMethodScaConsentResponseTO));

        when(authorisationService.returnScaMethodSelection(spiAspspConsentDataProvider, scaConsentResponseTO, "authentication method id"))
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
    void startScaDecoupled_success() {
        SpiAspspConsentDataProvider spiAspspConsentDataProviderWithEncryptedId = new SpiAspspConsentDataProviderWithEncryptedId(spiAspspConsentDataProvider, CONSENT_ID);
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaConsentResponseTO);

        doNothing().when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        when(redirectScaRestClient.selectMethod("authorisation id", "authentication method id"))
                .thenReturn(ResponseEntity.ok(scaConsentResponseTO));
        SpiAuthorizationCodeResult payload = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        payload.setChallengeData(challengeData);
        when(authorisationService.returnScaMethodSelection(spiAspspConsentDataProviderWithEncryptedId, scaConsentResponseTO, "authentication method id"))
                .thenReturn(SpiResponse.<SpiAuthorizationCodeResult>builder()
                                    .payload(payload)
                                    .build());

        ReflectionTestUtils.setField(spi, "onlineBankingUrl", "some.url");

        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = spi.startScaDecoupled(SPI_CONTEXT_DATA, "authorisation id", "authentication method id",
                                                                                         spiAccountConsent, spiAspspConsentDataProviderWithEncryptedId);

        assertFalse(actual.hasError());

        verify(spiAspspConsentDataProvider).loadAspspConsentData();
        verify(consentDataService).response(CONSENT_DATA_BYTES, true);
        verify(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        verify(redirectScaRestClient).selectMethod(AUTHORISATION_ID, AUTHENTICATION_METHOD_ID);
        verify(authorisationService).returnScaMethodSelection(spiAspspConsentDataProviderWithEncryptedId, scaConsentResponseTO, "authentication method id");
    }

    @Test
    void startScaDecoupled_errorOnReturningScaMethodSelection() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.PSUIDENTIFIED);

        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaConsentResponseTO);

        doNothing().when(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        when(redirectScaRestClient.selectMethod(AUTHORISATION_ID, AUTHENTICATION_METHOD_ID))
                .thenReturn(ResponseEntity.ok(scaConsentResponseTO));
        FeignException feignException = FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message");
        when(authorisationService.returnScaMethodSelection(spiAspspConsentDataProvider, scaConsentResponseTO, "authentication method id"))
                .thenThrow(feignException);
        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn("message");


        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = spi.startScaDecoupled(SPI_CONTEXT_DATA, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID,
                                                                                         spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals("", actual.getErrors().get(0).getMessageText());

        verify(spiAspspConsentDataProvider).loadAspspConsentData();
        verify(consentDataService).response(CONSENT_DATA_BYTES, true);
        verify(authRequestInterceptor).setAccessToken(ACCESS_TOKEN);
        verify(redirectScaRestClient).selectMethod(AUTHORISATION_ID, AUTHENTICATION_METHOD_ID);
        verify(authorisationService).returnScaMethodSelection(spiAspspConsentDataProvider, scaConsentResponseTO, "authentication method id");
        verify(feignExceptionReader).getErrorMessage(any(FeignException.class));
    }

    @Test
    void startScaDecoupled_scaSelected() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        GlobalScaResponseTO scaConsentResponseTO = buildSCAConsentResponseTO(ScaStatusTO.FINALISED);
        when(consentDataService.response(CONSENT_DATA_BYTES, true)).thenReturn(scaConsentResponseTO);

        SpiAuthorizationCodeResult payload = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = new ChallengeData();
        challengeData.setAdditionalInformation("SCA method EMAIL: tan is 123456");
        payload.setChallengeData(challengeData);

        SpiAspspConsentDataProvider spiAspspConsentDataProviderWithEncryptedId = new SpiAspspConsentDataProviderWithEncryptedId(spiAspspConsentDataProvider, CONSENT_ID);

        when(authorisationService.getResponseIfScaSelected(spiAspspConsentDataProviderWithEncryptedId, scaConsentResponseTO, "authentication method id"))
                .thenReturn(SpiResponse.<SpiAuthorizationCodeResult>builder()
                                    .payload(payload)
                                    .build());

        ReflectionTestUtils.setField(spi, ONLINE_BANKING_URL_FIELD_NAME, ONLINE_BANKING_URL_VALUE);

        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = spi.startScaDecoupled(SPI_CONTEXT_DATA, AUTHORISATION_ID, AUTHENTICATION_METHOD_ID,
                                                                                         spiAccountConsent, spiAspspConsentDataProviderWithEncryptedId);

        assertFalse(actual.hasError());

        verify(spiAspspConsentDataProvider).loadAspspConsentData();
        verify(consentDataService).response(CONSENT_DATA_BYTES, true);
        verify(authorisationService).getResponseIfScaSelected(spiAspspConsentDataProviderWithEncryptedId, scaConsentResponseTO, "authentication method id");
    }

    @Test
    void requestAvailableScaMethods_authenticationMethodIdIsNull() {
        SpiResponse<SpiAuthorisationDecoupledScaResponse> actual = spi.startScaDecoupled(SPI_CONTEXT_DATA, AUTHORISATION_ID, null,
                                                                                         spiAccountConsent, spiAspspConsentDataProvider);

        assertTrue(actual.hasError());
        assertEquals(MessageErrorCode.SERVICE_NOT_SUPPORTED, actual.getErrors().get(0).getErrorCode());
    }

    @Test
    void checkConfirmationCode() {
        SpiCheckConfirmationCodeRequest request = new SpiCheckConfirmationCodeRequest(CONFIRMATION_CODE, AUTHORISATION_ID);
        when(authConfirmationCodeService.checkConfirmationCode(request, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiConsentConfirmationCodeValidationResponse>builder().build());


        spi.checkConfirmationCode(SPI_CONTEXT_DATA, request, spiAspspConsentDataProvider);

        verify(authConfirmationCodeService, times(1)).checkConfirmationCode(request, spiAspspConsentDataProvider);
    }

    @Test
    void checkConfirmationCodeInternally() {
        spi.checkConfirmationCodeInternally(AUTHORISATION_ID, CONFIRMATION_CODE, CONFIRMATION_CODE, spiAspspConsentDataProvider);

        verify(authConfirmationCodeService, times(1)).checkConfirmationCodeInternally(AUTHORISATION_ID, CONFIRMATION_CODE, CONFIRMATION_CODE, spiAspspConsentDataProvider);
    }

    @Test
    void notifyConfirmationCodeValidation() {
        when(authConfirmationCodeService.completeAuthConfirmation(true, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiConsentConfirmationCodeValidationResponse>builder().build());

        spi.notifyConfirmationCodeValidation(SPI_CONTEXT_DATA, true, new SpiAccountConsent(), spiAspspConsentDataProvider);

        verify(authConfirmationCodeService, times(1)).completeAuthConfirmation(true, spiAspspConsentDataProvider);
    }

    private Response buildErrorResponse() {
        return Response.builder()
                       .status(404)
                       .request(Request.create(Request.HttpMethod.GET, "", Collections.emptyMap(), null, Charset.defaultCharset(), null))
                       .headers(Collections.emptyMap())
                       .build();
    }

    private List<SpiAccountReference> createAccountReferenceList() {
        return Collections.singletonList(SpiAccountReference.builder().iban("DE371234599997").build());
    }

    private GlobalScaResponseTO buildSCAConsentResponseTO(ScaStatusTO scaStatusTO) {
        BearerTokenTO bearerToken = new BearerTokenTO();
        bearerToken.setAccess_token(ACCESS_TOKEN);
        return buildSCAConsentResponseTO(scaStatusTO, bearerToken);
    }

    private GlobalScaResponseTO buildSCAConsentResponseTO(ScaStatusTO scaStatusTO, BearerTokenTO bearerTokenTO) {
        GlobalScaResponseTO scaConsentResponseTO = new GlobalScaResponseTO();
        scaConsentResponseTO.setOperationObjectId(CONSENT_ID);
        scaConsentResponseTO.setAuthorisationId(AUTHORISATION_ID);
        scaConsentResponseTO.setScaStatus(scaStatusTO);
        scaConsentResponseTO.setScaMethods(Collections.singletonList(getScaUserData()));
        scaConsentResponseTO.setBearerToken(bearerTokenTO);
        return scaConsentResponseTO;
    }

    private ScaUserDataTO getScaUserData() {
        ScaUserDataTO userDataTO = new ScaUserDataTO();
        userDataTO.setScaMethod(ScaMethodTypeTO.EMAIL);
        userDataTO.setDecoupled(false);
        return userDataTO;
    }

    private List<AccountDetailsTO> buildListOfAccounts() {
        return Collections.singletonList(jsonReader.getObjectFromFile("json/spi/impl/account-details.json", AccountDetailsTO.class));
    }

    private List<SpiAccountDetails> buildSpiAccountDetails() {
        return Collections.singletonList(jsonReader.getObjectFromFile("json/spi/impl/spi-account-details.json", SpiAccountDetails.class));
    }

    private FeignException getFeignException() {
        return FeignException.errorStatus("User doesn't have access to the requested account",
                                          buildErrorResponseForbidden());
    }

    private Response buildErrorResponseForbidden() {
        return Response.builder()
                       .status(403)
                       .request(Request.create(Request.HttpMethod.GET, "", Collections.emptyMap(), null, Charset.defaultCharset(), null))
                       .headers(Collections.emptyMap())
                       .build();
    }

    private SpiScaStatusResponse getTestData_ledgersAnswer() {
        return new SpiScaStatusResponse(SCA_STATUS_FROM_LEDGERS,
                                        false,
                                        PSU_MESSAGE_FROM_LEDGERS,
                                        SpiMockData.SPI_LINKS,
                                        SpiMockData.TPP_MESSAGES);
    }

    private SpiScaStatusResponse getTestData_mockedData() {
        return new SpiScaStatusResponse(SCA_STATUS_FROM_CMS,
                                        false,
                                        PSU_MESSAGE_MOCKED,
                                        SpiMockData.SPI_LINKS,
                                        SpiMockData.TPP_MESSAGES);
    }
}
