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
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiInitiateAisConsentResponse;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiVerifyScaAuthorisationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse.VoidResponse;
import de.adorsys.psd2.xs2a.spi.service.AisConsentSpi;
import feign.FeignException;
import org.apache.commons.lang3.ArrayUtils;
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
                                                                         SpiAccountConsent accountConsent, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        byte[] initialAspspConsentData = aspspConsentDataProvider.loadAspspConsentData();
        if (ArrayUtils.isEmpty(initialAspspConsentData)) {
            return firstCallInstantiatingConsent(accountConsent, aspspConsentDataProvider, new SpiInitiateAisConsentResponse());
        }

        SCAConsentResponseTO aisConsentResponse;
        try {

            aisConsentResponse = initiateConsentInternal(accountConsent, initialAspspConsentData);
        } catch (FeignException e) {
            return SpiResponse.<SpiInitiateAisConsentResponse>builder()
                           .error(getFailureMessageFromFeignException(e))
                           .build();
        }

        logger.info("SCA status` is {}", aisConsentResponse.getScaStatus().name());
        aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(aisConsentResponse));

        return SpiResponse.<SpiInitiateAisConsentResponse>builder()
                       .payload(new SpiInitiateAisConsentResponse(accountConsent.getAccess(), false, ""))
                       .build();
    }

    /*
     * Maybe store the corresponding token in the list of revoked token.
     *
     * TODO: Implement this functionality
     *
     */
    @Override
    public SpiResponse<VoidResponse> revokeAisConsent(@NotNull SpiContextData contextData,
                                                      SpiAccountConsent accountConsent, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        SCAConsentResponseTO sca = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData(), SCAConsentResponseTO.class);
        sca.setScaStatus(FINALISED);
        sca.setStatusDate(LocalDateTime.now());
        sca.setBearerToken(new BearerTokenTO());// remove existing token.

        String scaStatusName = sca.getScaStatus().name();
        logger.info("SCA status` is {}", scaStatusName);

        aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(sca));

        return SpiResponse.<SpiResponse.VoidResponse>builder()
                       .payload(SpiResponse.voidResponse())
                       .build();
    }

    /*
     * Verify tan, store resulting token in the returned accountConsent. The token
     * must be presented when TPP requests account information.
     *
     */
    @Override
    public @NotNull SpiResponse<SpiVerifyScaAuthorisationResponse> verifyScaAuthorisation(@NotNull SpiContextData contextData,
                                                                                          @NotNull SpiScaConfirmation spiScaConfirmation,
                                                                                          @NotNull SpiAccountConsent accountConsent,
                                                                                          @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        try {
            SCAConsentResponseTO sca = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData(), SCAConsentResponseTO.class);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            ResponseEntity<SCAConsentResponseTO> authorizeConsentResponse = consentRestClient
                                                                                    .authorizeConsent(sca.getConsentId(), sca.getAuthorisationId(), spiScaConfirmation.getTanNumber());
            SCAConsentResponseTO consentResponse = authorizeConsentResponse.getBody();

            String scaStatusName = sca.getScaStatus().name();
            logger.info("SCA status` is {}", scaStatusName);
            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(consentResponse, !consentResponse.isPartiallyAuthorised()));

            // TODO use real sca status from Ledgers for resolving consent status https://git.adorsys.de/adorsys/xs2a/ledgers/issues/206
            return SpiResponse.<SpiVerifyScaAuthorisationResponse>builder()
                           .payload(new SpiVerifyScaAuthorisationResponse(getConsentStatus(consentResponse)))
                           .build();
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
                                                            @NotNull SpiPsuData psuLoginData, String password, SpiAccountConsent aisConsent,
                                                            @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        SCAConsentResponseTO originalResponse;
        try {
            originalResponse = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData(), SCAConsentResponseTO.class, false);
        } catch (FeignException e) {
            return SpiResponse.<SpiAuthorisationStatus>builder()
                           .error(new TppMessage(MessageErrorCode.TOKEN_UNKNOWN, "Missing credentials. Expecting a bearer token in the consent data object."))
                           .build();
        }

        SpiResponse<SpiAuthorisationStatus> authorisePsu = authorisationService.authorisePsuForConsent(
                psuLoginData, password, aisConsent.getId(), originalResponse, OpTypeTO.CONSENT, aspspConsentDataProvider);

        if (!authorisePsu.isSuccessful()) {
            return SpiResponse.<SpiAuthorisationStatus>builder()
                           .error(new TppMessage(MessageErrorCode.PSU_CREDENTIALS_INVALID, "authorisation PSU for consent was failed"))
                           .build();
        }

        SCAConsentResponseTO scaConsentResponse;

        try {
            scaConsentResponse = mapToScaConsentResponse(aisConsent, aspspConsentDataProvider.loadAspspConsentData());
        } catch (IOException e) {
            return SpiResponse.<SpiAuthorisationStatus>builder()
                           .error(new TppMessage(MessageErrorCode.FORMAT_ERROR, "Unknown response type"))
                           .build();
        }

        if (EnumSet.of(EXEMPTED, PSUAUTHENTICATED, PSUIDENTIFIED).contains(scaConsentResponse.getScaStatus())) {

            SCAConsentResponseTO aisConsentResponse;

            try {
                aisConsentResponse = initiateConsentInternal(aisConsent, aspspConsentDataProvider.loadAspspConsentData());
            } catch (FeignException e) {
                return SpiResponse.<SpiAuthorisationStatus>builder()
                               .error(getFailureMessageFromFeignException(e))
                               .build();
            }

            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(aisConsentResponse));

            return SpiResponse.<SpiAuthorisationStatus>builder()
                           .payload(authorisePsu.getPayload())
                           .build();
        }// DO not change. AuthorizePsu is mutable. //TODO @fpo fix this

        return SpiResponse.<SpiAuthorisationStatus>builder()
                       .payload(authorisePsu.getPayload())
                       .build();
    }

    /**
     * This call must follow an init consent request, therefore we are expecting the
     * {@link AspspConsentData} object to contain a {@link SCAConsentResponseTO}
     * response.
     */
    @Override
    public SpiResponse<List<SpiAuthenticationObject>> requestAvailableScaMethods(@NotNull SpiContextData contextData,
                                                                                 SpiAccountConsent businessObject, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        try {
            SCAConsentResponseTO sca = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData(), SCAConsentResponseTO.class);
            if (sca.getScaMethods() != null) {

                // Validate the access token
                BearerTokenTO bearerTokenTO = authorisationService.validateToken(sca.getBearerToken().getAccess_token());
                sca.setBearerToken(bearerTokenTO);

                // Return contained sca methods.
                List<ScaUserDataTO> scaMethods = sca.getScaMethods();
                List<SpiAuthenticationObject> authenticationObjects = scaMethodConverter.toSpiAuthenticationObjectList(scaMethods);
                aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(sca));
                return SpiResponse.<List<SpiAuthenticationObject>>builder()
                               .payload(authenticationObjects)
                               .build();
            } else {
                logger.error("Process mismatch. Current SCA Status is %s", sca.getScaStatus());
                return SpiResponse.<List<SpiAuthenticationObject>>builder()
                               .error(new TppMessage(MessageErrorCode.SESSIONS_NOT_SUPPORTED, "Process mismatch. Psu doest'n have any sca method"))
                               .build();
            }
        } catch (FeignException e) {
            return SpiResponse.<List<SpiAuthenticationObject>>builder()
                           .error(new TppMessage(MessageErrorCode.FORMAT_ERROR, "Getting SCA methods failed"))
                           .build();
        }
    }

    @Override
    public @NotNull SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(@NotNull SpiContextData contextData, @NotNull String authenticationMethodId, @NotNull SpiAccountConsent businessObject, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        SCAConsentResponseTO sca = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData(), SCAConsentResponseTO.class);
        if (EnumSet.of(PSUIDENTIFIED, PSUAUTHENTICATED).contains(sca.getScaStatus())) {
            try {
                authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());
                logger.info("Request to generate SCA {}", sca.getConsentId());
                ResponseEntity<SCAConsentResponseTO> selectMethodResponse = consentRestClient.selectMethod(sca.getConsentId(), sca.getAuthorisationId(), authenticationMethodId);
                logger.info("SCA was send, operationId is {}", sca.getConsentId());
                SCAConsentResponseTO authCodeResponse = selectMethodResponse.getBody();
                if (authCodeResponse != null && authCodeResponse.getBearerToken() == null) {
                    // TODO: hack. Core banking is supposed to always return a token. @fpo
                    authCodeResponse.setBearerToken(sca.getBearerToken());
                }
                return authorisationService.returnScaMethodSelection(aspspConsentDataProvider, authCodeResponse);
            } catch (FeignException e) {
                return SpiResponse.<SpiAuthorizationCodeResult>builder()
                               // TODO fix response form ledgers https://git.adorsys.de/adorsys/xs2a/psd2-dynamic-sandbox/issues/185
                               .error(new TppMessage(MessageErrorCode.SCA_METHOD_UNKNOWN, "Sending SCA via phone not implemented yet"))
                               .build();
            } finally {
                authRequestInterceptor.setAccessToken(null);
            }
        } else {
            return authorisationService.getResponseIfScaSelected(aspspConsentDataProvider, sca);
        }
    }

    ConsentStatus getConsentStatus(SCAConsentResponseTO consentResponse) {
        if (consentResponse != null
                    && consentResponse.isMultilevelScaRequired()
                    && consentResponse.isPartiallyAuthorised()
                    && FINALISED.equals(consentResponse.getScaStatus())) {
            return ConsentStatus.PARTIALLY_AUTHORISED;
        }
        return ConsentStatus.VALID;
    }

    private <T extends SpiInitiateAisConsentResponse> SpiResponse<T> firstCallInstantiatingConsent(
            @NotNull SpiAccountConsent accountConsent,
            @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider, T responsePayload) {
        SCAConsentResponseTO response = new SCAConsentResponseTO();
        response.setScaStatus(STARTED);
        responsePayload.setAccountAccess(accountConsent.getAccess());
        aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(response, false));
        return SpiResponse.<T>builder()
                       .payload(responsePayload)
                       .build();
    }

    private SCAConsentResponseTO mapToScaConsentResponse(SpiAccountConsent businessObject, byte[] aspspConsentData) throws IOException {
        SCALoginResponseTO scaResponseTO = tokenStorageService.fromBytes(aspspConsentData, SCALoginResponseTO.class);
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

    private SCAConsentResponseTO initiateConsentInternal(SpiAccountConsent accountConsent, byte[] initialAspspConsentData) throws FeignException {
        try {
            SCAResponseTO sca = consentDataService.response(initialAspspConsentData);
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

    private TppMessage getFailureMessageFromFeignException(FeignException e) {
        logger.error(e.getMessage(), e);

        return e.status() == 500
                       ? new TppMessage(MessageErrorCode.INTERNAL_SERVER_ERROR, "Request was failed")
                       : new TppMessage(MessageErrorCode.FORMAT_ERROR, "Addressed account is unknown to the ASPSP or not associated to the PSU.");

    }
}
