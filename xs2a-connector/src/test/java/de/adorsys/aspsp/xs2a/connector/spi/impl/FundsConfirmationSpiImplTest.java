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

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapper;
import de.adorsys.aspsp.xs2a.util.TestSpiDataProvider;
import de.adorsys.ledgers.keycloak.client.api.KeycloakTokenService;
import de.adorsys.ledgers.middleware.api.domain.account.AccountReferenceTO;
import de.adorsys.ledgers.middleware.api.domain.account.FundsConfirmationRequestTO;
import de.adorsys.ledgers.middleware.api.domain.payment.AmountTO;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.middleware.api.domain.um.AccessTokenTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.rest.client.AccountRestClient;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiAmount;
import de.adorsys.psd2.xs2a.spi.domain.fund.SpiFundsConfirmationRequest;
import de.adorsys.psd2.xs2a.spi.domain.fund.SpiFundsConfirmationResponse;
import de.adorsys.psd2.xs2a.spi.domain.piis.SpiPiisConsent;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FundsConfirmationSpiImplTest {

    private static final byte[] BYTES = "data".getBytes();

    private static final SpiContextData SPI_CONTEXT_DATA = TestSpiDataProvider.getSpiContextData();
    private static final String RESOURCE_ID = "11111-999999999";
    private static final String PASSWORD = "12345";

    private static final String RESPONSE_STATUS_200_WITH_EMPTY_BODY = "Response status was 200, but the body was empty!";

    private static final String CARD_NUMBER = "5351765401348529";
    private static final String IBAN = "DE89370400440532013000";
    private static final Currency CURRENCY_EUR = Currency.getInstance("EUR");

    @InjectMocks
    private FundsConfirmationSpiImpl fundsConfirmationSpi;

    @Mock
    private AccountRestClient accountRestClient;
    @Mock
    private LedgersSpiAccountMapper accountMapper;
    @Mock
    private AuthRequestInterceptor authRequestInterceptor;
    @Mock
    private AspspConsentDataService consentDataService;
    @Mock
    private SpiAspspConsentDataProvider aspspConsentDataProvider;
    @Mock
    private KeycloakTokenService keycloakTokenService;

    @Test
    void performFundsSufficientCheck_ok() {
        // Given
        SpiPiisConsent spiPiisConsent = new SpiPiisConsent();
        SpiFundsConfirmationRequest spiFundsConfirmationRequest = getSpiFundsConfirmationRequest();

        when(aspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(BYTES);
        when(consentDataService.response(BYTES))
                .thenReturn(getScaResponse());
        FundsConfirmationRequestTO request = getFundsConfirmationRequestTO();
        when(accountMapper.toFundsConfirmationTO(SPI_CONTEXT_DATA.getPsuData(), spiFundsConfirmationRequest))
                .thenReturn(request);
        when(accountRestClient.fundsConfirmation(request))
                .thenReturn(ResponseEntity.ok(Boolean.TRUE));

        // When
        SpiResponse<SpiFundsConfirmationResponse> actual =
                fundsConfirmationSpi.performFundsSufficientCheck(SPI_CONTEXT_DATA, spiPiisConsent, spiFundsConfirmationRequest, aspspConsentDataProvider);

        // Then
        assertTrue(actual.isSuccessful());
        assertTrue(actual.getPayload().isFundsAvailable());
        verify(authRequestInterceptor, times(1)).setAccessToken(getScaResponse().getBearerToken().getAccess_token());
        verify(authRequestInterceptor, times(1)).setAccessToken(null);

    }

    @Test
    void performFundsSufficientCheck_ok_noPiisConsent() throws NoSuchFieldException, IllegalAccessException {
        // Given
        String fundsConfirmationUserLogin = "fundsConfirmationUserLogin";
        Field fieldFundsConfirmationUserLogin = fundsConfirmationSpi.getClass().getDeclaredField(fundsConfirmationUserLogin);
        fieldFundsConfirmationUserLogin.setAccessible(true);
        fieldFundsConfirmationUserLogin.set(fundsConfirmationSpi, SPI_CONTEXT_DATA.getPsuData().getPsuId());

        String fundsConfirmationUserPassword = "fundsConfirmationUserPassword";
        Field fieldFundsConfirmationUserPassword = fundsConfirmationSpi.getClass().getDeclaredField(fundsConfirmationUserPassword);
        fieldFundsConfirmationUserPassword.setAccessible(true);
        fieldFundsConfirmationUserPassword.set(fundsConfirmationSpi, PASSWORD);

        SpiFundsConfirmationRequest spiFundsConfirmationRequest = getSpiFundsConfirmationRequest();

        when(keycloakTokenService.login(anyString(), anyString()))
                .thenReturn(getScaResponse().getBearerToken());
        FundsConfirmationRequestTO request = getFundsConfirmationRequestTO();
        when(accountMapper.toFundsConfirmationTO(SPI_CONTEXT_DATA.getPsuData(), spiFundsConfirmationRequest))
                .thenReturn(request);
        when(accountRestClient.fundsConfirmation(request))
                .thenReturn(ResponseEntity.ok(Boolean.TRUE));

        // When
        SpiResponse<SpiFundsConfirmationResponse> actual =
                fundsConfirmationSpi.performFundsSufficientCheck(SPI_CONTEXT_DATA, null, spiFundsConfirmationRequest, aspspConsentDataProvider);

        // Then
        assertTrue(actual.isSuccessful());
        assertTrue(actual.getPayload().isFundsAvailable());
        verify(authRequestInterceptor, times(1)).setAccessToken(getScaResponse().getBearerToken().getAccess_token());
        verify(authRequestInterceptor, times(1)).setAccessToken(null);
    }

    @Test
    void performFundsSufficientCheck_fail_feignException() {
        // Given
        SpiPiisConsent spiPiisConsent = new SpiPiisConsent();
        SpiFundsConfirmationRequest spiFundsConfirmationRequest = getSpiFundsConfirmationRequest();

        FundsConfirmationRequestTO request = getFundsConfirmationRequestTO();
        when(accountMapper.toFundsConfirmationTO(SPI_CONTEXT_DATA.getPsuData(), spiFundsConfirmationRequest))
                .thenReturn(request);
        when(accountRestClient.fundsConfirmation(request))
                .thenThrow(getFeignException());

        // When
        SpiResponse<SpiFundsConfirmationResponse> actual =
                fundsConfirmationSpi.performFundsSufficientCheck(SPI_CONTEXT_DATA, spiPiisConsent, spiFundsConfirmationRequest, aspspConsentDataProvider);

        // Then
        assertFalse(actual.isSuccessful());
        assertNotNull(actual.getErrors());
        verify(authRequestInterceptor, times(2)).setAccessToken(null);
    }

    private GlobalScaResponseTO getScaResponse() {
        GlobalScaResponseTO sca = new GlobalScaResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        token.setExpires_in(100);
        token.setAccessTokenObject(new AccessTokenTO());
        token.setRefresh_token("refresh_token");
        token.setAccess_token("access_token");
        sca.setBearerToken(token);

        return sca;
    }

    private SpiFundsConfirmationRequest getSpiFundsConfirmationRequest() {
        SpiFundsConfirmationRequest request = new SpiFundsConfirmationRequest();
        SpiAccountReference accountReference = new SpiAccountReference(RESOURCE_ID, IBAN, null, null, null, null, CURRENCY_EUR);
        request.setPsuAccount(accountReference);
        request.setCardNumber(CARD_NUMBER);
        request.setInstructedAmount(new SpiAmount(CURRENCY_EUR, BigDecimal.TEN));

        return request;
    }

    private FundsConfirmationRequestTO getFundsConfirmationRequestTO() {
        FundsConfirmationRequestTO request = new FundsConfirmationRequestTO();
        request.setPsuId(SPI_CONTEXT_DATA.getPsuData().getPsuId());
        request.setCardNumber(CARD_NUMBER);
        request.setPsuAccount(new AccountReferenceTO(IBAN, null, null, null, null, CURRENCY_EUR));
        request.setInstructedAmount(new AmountTO(CURRENCY_EUR, BigDecimal.TEN));

        return request;
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
}