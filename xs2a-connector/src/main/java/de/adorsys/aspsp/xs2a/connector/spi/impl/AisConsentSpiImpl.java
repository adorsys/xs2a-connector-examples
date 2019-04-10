/*
 * Copyright 2018-2019 adorsys GmbH & Co KG
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

import de.adorsys.aspsp.xs2a.connector.spi.converter.AisConsentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaLoginToConsentResponseMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.ledgers.middleware.api.domain.sca.OpTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAConsentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCALoginResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import de.adorsys.ledgers.middleware.api.domain.um.AisConsentTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.ledgers.middleware.api.service.TokenStorageService;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.ConsentRestClient;
import de.adorsys.psd2.xs2a.core.ais.AccountAccessType;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiAccountAccess;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiInitiateAisConsentResponse;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiVerifyScaAuthorisationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse.VoidResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.AisConsentSpi;
import feign.FeignException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import static de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO.*;

@Component
public class AisConsentSpiImpl implements AisConsentSpi {
    private static final Logger logger = LoggerFactory.getLogger(AisConsentSpiImpl.class);

    private final ConsentRestClient consentRestClient;
    private final TokenStorageService tokenStorageService;
    private final AisConsentMapper aisConsentMapper;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final GeneralAuthorisationService authorisationService;
    private final ScaMethodConverter scaMethodConverter;
    private final ScaLoginToConsentResponseMapper scaLoginToConsentResponseMapper;

    public AisConsentSpiImpl(ConsentRestClient consentRestClient, TokenStorageService tokenStorageService,
                             AisConsentMapper aisConsentMapper, AuthRequestInterceptor authRequestInterceptor,
                             AspspConsentDataService consentDataService, GeneralAuthorisationService authorisationService,
                             ScaMethodConverter scaMethodConverter, ScaLoginToConsentResponseMapper scaLoginToConsentResponseMapper) {
        this.consentRestClient = consentRestClient;
        this.tokenStorageService = tokenStorageService;
        this.aisConsentMapper = aisConsentMapper;
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
        this.authorisationService = authorisationService;
        this.scaMethodConverter = scaMethodConverter;
        this.scaLoginToConsentResponseMapper = scaLoginToConsentResponseMapper;
    }

    /*
     * Initiates an ais consent. Initiation request assumes that the psu id at least
     * identified. THis is, we read a {@link SCAResponseTO} object from the {@link AspspConsentData} input.
     */
    @Override
    public SpiResponse<SpiInitiateAisConsentResponse> initiateAisConsent(@NotNull SpiContextData contextData,
                                                                         SpiAccountConsent accountConsent, AspspConsentData initialAspspConsentData) {

        if (initialAspspConsentData.getAspspConsentData() == null) {
            return firstCallInstantiatingConsent(accountConsent, initialAspspConsentData, new SpiInitiateAisConsentResponse());
        }
        SCAConsentResponseTO consentResponse = initiateConsentInternal(accountConsent, initialAspspConsentData);
        SpiAccountAccess accountAccess = accountConsent.getAccess();
        return SpiResponse.<SpiInitiateAisConsentResponse>builder()
                       .payload(new SpiInitiateAisConsentResponse(accountAccess, false))
                       .aspspConsentData(initialAspspConsentData.respondWith(consentDataService.store(consentResponse)))
                       // Pass sca status as message.
                       .message(consentResponse.getScaStatus().name()).success();
    }


    /*
     * Maybe store the corresponding token in the list of revoked token.
     *
     * TODO: Implement this functionality
     *
     */
    @Override
    public SpiResponse<VoidResponse> revokeAisConsent(@NotNull SpiContextData contextData,
                                                      SpiAccountConsent accountConsent, AspspConsentData aspspConsentData) {
        SCAConsentResponseTO sca = consentDataService.response(aspspConsentData.getAspspConsentData(), SCAConsentResponseTO.class);
        sca.setScaStatus(FINALISED);
        sca.setStatusDate(LocalDateTime.now());
        sca.setBearerToken(new BearerTokenTO());// remove existing token.
        return SpiResponse.<SpiResponse.VoidResponse>builder().payload(SpiResponse.voidResponse())
                       .aspspConsentData(aspspConsentData.respondWith(consentDataService.store(sca)))
                       .message(sca.getScaStatus().name())
                       .success();
    }

    /*
     * Verify tan, store resulting token in the returned accountConsent. The token
     * must be presented when TPP requests account information.
     *
     */
    @Override
    public @NotNull SpiResponse<SpiVerifyScaAuthorisationResponse> verifyScaAuthorisation(@NotNull SpiContextData contextData,
                                                                                          @NotNull SpiScaConfirmation spiScaConfirmation, @NotNull SpiAccountConsent accountConsent,
                                                                                          @NotNull AspspConsentData aspspConsentData) {
        try {
            SCAConsentResponseTO sca = consentDataService.response(aspspConsentData.getAspspConsentData(), SCAConsentResponseTO.class);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            ResponseEntity<SCAConsentResponseTO> authorizeConsentResponse = consentRestClient
                                                                                    .authorizeConsent(sca.getConsentId(), sca.getAuthorisationId(), spiScaConfirmation.getTanNumber());
            SCAConsentResponseTO consentResponse = authorizeConsentResponse.getBody();

            // TODO use real sca status from Ledgers for resolving consent status https://git.adorsys.de/adorsys/xs2a/ledgers/issues/206
            return SpiResponse.<SpiVerifyScaAuthorisationResponse>builder().payload(new SpiVerifyScaAuthorisationResponse(ConsentStatus.VALID))
                           .aspspConsentData(aspspConsentData.respondWith(consentDataService.store(consentResponse)))
                           .message(consentResponse.getScaStatus().name()).success();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    /*
     * Login the user. On successful login, store the token in the resulting status
     * object.
     */
    @Override
    public SpiResponse<SpiAuthorisationStatus> authorisePsu(@NotNull SpiContextData contextData,
                                                            @NotNull SpiPsuData psuLoginData, String password, SpiAccountConsent businessObject,
                                                            @NotNull AspspConsentData aspspConsentData) {

        SCAConsentResponseTO originalResponse = consentDataService.response(aspspConsentData.getAspspConsentData(), SCAConsentResponseTO.class, false);

        SpiResponse<SpiAuthorisationStatus> authorisePsu = authorisationService.authorisePsuForConsent(
                psuLoginData, password, businessObject.getId(), originalResponse, OpTypeTO.CONSENT, aspspConsentData);

        if (!authorisePsu.isSuccessful()) {
            return authorisePsu;
        }
        SCAConsentResponseTO scaConsentResponse;
        try {
            scaConsentResponse = toConsentResponse(businessObject, authorisePsu);
        } catch (IOException e) {
            return SpiResponse.<SpiAuthorisationStatus>builder()
                           .message(e.getMessage())
                           .aspspConsentData(aspspConsentData)
                           .fail(SpiResponseStatus.LOGICAL_FAILURE);
        }

        if (EnumSet.of(EXEMPTED, PSUAUTHENTICATED, PSUIDENTIFIED)
                    .contains(scaConsentResponse.getScaStatus())) {
            SCAConsentResponseTO resp = initiateConsentInternal(businessObject, authorisePsu.getAspspConsentData());
            return new SpiResponse<>(authorisePsu.getPayload(), authorisePsu.getAspspConsentData().respondWith(consentDataService.store(resp)));
        }// DO not change. AuthorizePsu is mutable. //TODO @fpo fix this
        return new SpiResponse<>(authorisePsu.getPayload(), authorisePsu.getAspspConsentData());
    }

    /**
     * This call must follow an init consent request, therefore we are expecting the
     * {@link AspspConsentData} object to contain a {@link SCAConsentResponseTO}
     * response.
     */
    @Override
    public SpiResponse<List<SpiAuthenticationObject>> requestAvailableScaMethods(@NotNull SpiContextData contextData,
                                                                                 SpiAccountConsent businessObject, @NotNull AspspConsentData aspspConsentData) {
        try {
            SCAConsentResponseTO sca = consentDataService.response(aspspConsentData.getAspspConsentData(), SCAConsentResponseTO.class);
            if (sca.getScaMethods() != null) {

                // Validate the access token
                BearerTokenTO bearerTokenTO = authorisationService.validateToken(sca.getBearerToken().getAccess_token());
                sca.setBearerToken(bearerTokenTO);

                // Return contained sca methods.
                List<ScaUserDataTO> scaMethods = sca.getScaMethods();
                List<SpiAuthenticationObject> authenticationObjects = scaMethodConverter.toSpiAuthenticationObjectList(scaMethods);
                return SpiResponse.<List<SpiAuthenticationObject>>builder()
                               .aspspConsentData(aspspConsentData.respondWith(consentDataService.store(sca)))
                               .payload(authenticationObjects).success();
            } else {
                logger.error("Process mismatch. Current SCA Status is %s", sca.getScaStatus());
                return SpiResponse.<List<SpiAuthenticationObject>>builder()
                               .aspspConsentData(aspspConsentData)
                               .fail(SpiResponseStatus.LOGICAL_FAILURE);
            }
        } catch (FeignException e) {
            return SpiResponse.<List<SpiAuthenticationObject>>builder()
                           .aspspConsentData(aspspConsentData)
                           .fail(getSpiFailureResponse(e));
        }
    }

    @Override
    public @NotNull SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(@NotNull SpiContextData contextData, @NotNull String authenticationMethodId, @NotNull SpiAccountConsent businessObject, @NotNull AspspConsentData aspspConsentData) {
        SCAConsentResponseTO sca = consentDataService.response(aspspConsentData.getAspspConsentData(), SCAConsentResponseTO.class);
        if (EnumSet.of(PSUIDENTIFIED,PSUAUTHENTICATED).contains(sca.getScaStatus())) {
            try {
                authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());
                logger.info("Request to generate SCA {}", sca.getConsentId());
                ResponseEntity<SCAConsentResponseTO> selectMethodResponse = consentRestClient.selectMethod(sca.getConsentId(), sca.getAuthorisationId(), authenticationMethodId);
                logger.info("SCA was send, operationId is {}", sca.getConsentId());
                SCAConsentResponseTO authCodeResponse = selectMethodResponse.getBody();
                if (authCodeResponse != null && authCodeResponse.getBearerToken() == null) {
                    // TODO: hack. Core banking is supposed to allways return a token. @fpo
                    authCodeResponse.setBearerToken(sca.getBearerToken());
                }
                return authorisationService.returnScaMethodSelection(aspspConsentData, authCodeResponse);
            } finally {
                authRequestInterceptor.setAccessToken(null);
            }
        } else {
            return authorisationService.getResponseIfScaSelected(aspspConsentData, sca);
        }
    }


    private <T extends SpiInitiateAisConsentResponse> SpiResponse<T> firstCallInstantiatingConsent(
            @NotNull SpiAccountConsent accountConsent,
            @NotNull AspspConsentData initialAspspConsentData, T responsePayload) {
        SCAConsentResponseTO response = new SCAConsentResponseTO();
        response.setScaStatus(STARTED);
        responsePayload.setAccountAccess(accountConsent.getAccess());
        return SpiResponse.<T>builder()
                       .aspspConsentData(initialAspspConsentData.respondWith(consentDataService.store(response, false)))
                       .payload(responsePayload).success();
    }

    private SCAConsentResponseTO toConsentResponse(SpiAccountConsent businessObject, SpiResponse<SpiAuthorisationStatus> authorisePsu) throws IOException {
        SCALoginResponseTO scaResponseTO = tokenStorageService.fromBytes(authorisePsu.getAspspConsentData().getAspspConsentData(), SCALoginResponseTO.class);
        SCAConsentResponseTO consentResponse = scaLoginToConsentResponseMapper.toConsentResponse(scaResponseTO);
        consentResponse.setObjectType(SCAConsentResponseTO.class.getSimpleName());
        consentResponse.setConsentId(businessObject.getId());
        return consentResponse;
    }

    /*
     * Check is there is any consent information in this consent object.
     */
    private boolean isEmpty(SpiAccountConsent accountConsent) { //TODO should be added to SpiApi object @dmiex
        return accountConsent.getAccess() == null ||
                       (accountConsent.getAccess().getAccounts() == null || accountConsent.getAccess().getAccounts().isEmpty()) &&
                               (accountConsent.getAccess().getBalances() == null || accountConsent.getAccess().getBalances().isEmpty()) &&
                               (accountConsent.getAccess().getTransactions() == null || accountConsent.getAccess().getTransactions().isEmpty()) &&
                               accountConsent.getAccess().getAllPsd2() == null &&
                               accountConsent.getAccess().getAvailableAccounts() == null;
    }

    private SCAConsentResponseTO initiateConsentInternal(SpiAccountConsent accountConsent, AspspConsentData initialAspspConsentData) {
        try {
            SCAResponseTO sca = consentDataService.response(initialAspspConsentData.getAspspConsentData());
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            // Issue: https://git.adorsys.de/adorsys/xs2a/ledgers/issues/169
            // TODO FIXME waiting for Issue https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/647
            if (isEmpty(accountConsent)) {
                accountConsent.getAccess().setAllPsd2(AccountAccessType.ALL_ACCOUNTS);
            }
            AisConsentTO aisConsent = aisConsentMapper.toTo(accountConsent);

            // Bearer token only returned in case of exempted consent.
            ResponseEntity<SCAConsentResponseTO> consentResponse = consentRestClient.startSCA(accountConsent.getId(),
                    aisConsent);
            SCAConsentResponseTO response = consentResponse.getBody();

            if (response != null && response.getBearerToken() == null) {
                response.setBearerToken(sca.getBearerToken());
            }
            return response;
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    private SpiResponseStatus getSpiFailureResponse(FeignException e) {
        logger.error(e.getMessage(), e);
        return e.status() == 500 ? SpiResponseStatus.TECHNICAL_FAILURE : SpiResponseStatus.LOGICAL_FAILURE;
    }
}
