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

import de.adorsys.aspsp.xs2a.connector.spi.converter.AisConsentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaLoginMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation.AbstractAuthorisationSpi;
import de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation.GeneralAuthorisationService;
import de.adorsys.ledgers.middleware.api.domain.sca.*;
import de.adorsys.ledgers.middleware.api.domain.um.AisConsentTO;
import de.adorsys.ledgers.middleware.api.service.TokenStorageService;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.ConsentRestClient;
import de.adorsys.ledgers.rest.client.UserMgmtRestClient;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiCheckConfirmationCodeRequest;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiConsentConfirmationCodeValidationResponse;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiConsentStatusResponse;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiInitiatePiisConsentResponse;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiVerifyScaAuthorisationResponse;
import de.adorsys.psd2.xs2a.spi.domain.piis.SpiPiisConsent;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.PiisConsentSpi;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
public class PiisConsentSpiImpl extends AbstractAuthorisationSpi<SpiPiisConsent, SCAConsentResponseTO> implements PiisConsentSpi {
    // TODO REPLACE WITH PIIS FLOW WHEN LEDGERS STARTS TO SUPPORT PIIS CONSENT https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/-/issues/1323
    private static final String SCA_STATUS_LOG = "SCA status is {}";
    private static final String ATTEMPT_FAILURE = "SCA_VALIDATION_ATTEMPT_FAILED";

    private final ConsentRestClient consentRestClient;
    private final TokenStorageService tokenStorageService;
    private final AisConsentMapper aisConsentMapper;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final ScaLoginMapper scaLoginMapper;
    private final MultilevelScaService multilevelScaService;
    private final FeignExceptionReader feignExceptionReader;
    private final UserMgmtRestClient userMgmtRestClient;

    public PiisConsentSpiImpl(ConsentRestClient consentRestClient, TokenStorageService tokenStorageService,
                              AisConsentMapper aisConsentMapper, AuthRequestInterceptor authRequestInterceptor,
                              AspspConsentDataService consentDataService, GeneralAuthorisationService authorisationService,
                              ScaMethodConverter scaMethodConverter, ScaLoginMapper scaLoginMapper, FeignExceptionReader feignExceptionReader,
                              MultilevelScaService multilevelScaService, UserMgmtRestClient userMgmtRestClient) {
        super(authRequestInterceptor, consentDataService, authorisationService, scaMethodConverter, feignExceptionReader, tokenStorageService);
        this.consentRestClient = consentRestClient;
        this.tokenStorageService = tokenStorageService;
        this.aisConsentMapper = aisConsentMapper;
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
        this.scaLoginMapper = scaLoginMapper;
        this.multilevelScaService = multilevelScaService;
        this.feignExceptionReader = feignExceptionReader;
        this.userMgmtRestClient = userMgmtRestClient;
    }

    @Override
    protected ResponseEntity<SCAConsentResponseTO> getSelectMethodResponse(@NotNull String authenticationMethodId, SCAConsentResponseTO sca) {
        return consentRestClient.selectMethod(sca.getConsentId(), sca.getAuthorisationId(), authenticationMethodId);
    }

    @Override
    protected SCAConsentResponseTO getSCAConsentResponse(@NotNull SpiAspspConsentDataProvider aspspConsentDataProvider, boolean checkCredentials) {
        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();
        return consentDataService.response(aspspConsentData, SCAConsentResponseTO.class, checkCredentials);
    }

    @Override
    protected String getBusinessObjectId(SpiPiisConsent businessObject) {
        return businessObject.getId();
    }

    @Override
    protected OpTypeTO getOtpType() {
        return OpTypeTO.CONSENT;
    }

    @Override
    protected TppMessage getAuthorisePsuFailureMessage(SpiPiisConsent businessObject) {
        log.error("Initiate consent failed: consent ID {}", businessObject.getId());
        return new TppMessage(MessageErrorCode.FORMAT_ERROR_UNKNOWN_ACCOUNT);
    }

    @Override
    protected SCAResponseTO initiateBusinessObject(SpiPiisConsent piisConsent, byte[] initialAspspConsentData) {
        try {
            SCAResponseTO sca = consentDataService.response(initialAspspConsentData);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            AisConsentTO aisConsent = aisConsentMapper.mapPiisToAisConsent(piisConsent);

            // Bearer token only returned in case of exempted consent.
            ResponseEntity<SCAConsentResponseTO> consentResponse = consentRestClient.startSCA(piisConsent.getId(),
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

    @Override
    protected SCAConsentResponseTO mapToScaResponse(SpiPiisConsent businessObject, byte[] aspspConsentData, SCAConsentResponseTO originalResponse) throws IOException {
        SCALoginResponseTO scaResponseTO = tokenStorageService.fromBytes(aspspConsentData, SCALoginResponseTO.class);
        SCAConsentResponseTO consentResponse = scaLoginMapper.toConsentResponse(scaResponseTO);
        consentResponse.setObjectType(SCAConsentResponseTO.class.getSimpleName());
        consentResponse.setConsentId(businessObject.getId());
        consentResponse.setMultilevelScaRequired(originalResponse.isMultilevelScaRequired());
        return consentResponse;
    }

    @Override
    protected boolean isFirstInitiationOfMultilevelSca(SpiPiisConsent businessObject, SCAConsentResponseTO scaConsentResponseTO) {
        return !scaConsentResponseTO.isMultilevelScaRequired() || businessObject.getPsuData().size() <= 1;
    }

    @Override
    public @NotNull SpiResponse<Boolean> requestTrustedBeneficiaryFlag(@NotNull SpiContextData contextData, @NotNull SpiPiisConsent businessObject, @NotNull String authorisationId, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        log.info("PiisConsentSpiImpl#requestTrustedBeneficiaryFlag: contextData {}, businessObject-id {}", contextData, businessObject.getId());

        return SpiResponse.<Boolean>builder()
                       .payload(true)
                       .build();
    }

    @Override
    public @NotNull SpiResponse<SpiVerifyScaAuthorisationResponse> verifyScaAuthorisation(@NotNull SpiContextData contextData, @NotNull SpiScaConfirmation spiScaConfirmation, @NotNull SpiPiisConsent spiPiisConsent, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        try {
            SCAConsentResponseTO sca = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData(), SCAConsentResponseTO.class);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            ResponseEntity<SCAConsentResponseTO> authorizeConsentResponse = consentRestClient
                                                                                    .authorizeConsent(sca.getConsentId(), sca.getAuthorisationId(), spiScaConfirmation.getTanNumber());
            SCAConsentResponseTO consentResponse = authorizeConsentResponse.getBody();

            String scaStatusName = sca.getScaStatus().name();
            log.info(SCA_STATUS_LOG, scaStatusName);
            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(consentResponse, !consentResponse.isPartiallyAuthorised()));

            // TODO use real sca status from Ledgers for resolving consent status https://git.adorsys.de/adorsys/xs2a/ledgers/issues/206
            return SpiResponse.<SpiVerifyScaAuthorisationResponse>builder()
                           .payload(new SpiVerifyScaAuthorisationResponse(getConsentStatus(consentResponse)))
                           .build();
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            log.error("Verify sca authorisation failed: consent ID {}, devMessage {}", spiPiisConsent.getId(), devMessage);

            String errorCode = feignExceptionReader.getErrorCode(feignException);
            if (errorCode.equals(ATTEMPT_FAILURE)) {
                return SpiResponse.<SpiVerifyScaAuthorisationResponse>builder()
                               .payload(new SpiVerifyScaAuthorisationResponse(spiPiisConsent.getConsentStatus(), SpiAuthorisationStatus.ATTEMPT_FAILURE))
                               .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PSU_CREDENTIALS_INVALID, devMessage))
                               .build();
            }

            return SpiResponse.<SpiVerifyScaAuthorisationResponse>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PSU_CREDENTIALS_INVALID, devMessage))
                           .build();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    ConsentStatus getConsentStatus(SCAConsentResponseTO consentResponse) {
        if (consentResponse != null
                    && consentResponse.isPartiallyAuthorised()
                    && ScaStatusTO.FINALISED.equals(consentResponse.getScaStatus())) {
            return ConsentStatus.PARTIALLY_AUTHORISED;
        }
        return ConsentStatus.VALID;
    }

    @Override
    public @NotNull SpiResponse<SpiConsentConfirmationCodeValidationResponse> notifyConfirmationCodeValidation
            (@NotNull SpiContextData spiContextData, boolean confirmationCodeValidationResult,
             @NotNull SpiPiisConsent spiPiisConsent, @NotNull SpiAspspConsentDataProvider spiAspspConsentDataProvider) {
        ScaStatus scaStatus = confirmationCodeValidationResult ? ScaStatus.FINALISED : ScaStatus.FAILED;
        ConsentStatus consentStatus = confirmationCodeValidationResult ? ConsentStatus.VALID : ConsentStatus.REJECTED;

        SpiConsentConfirmationCodeValidationResponse response = new SpiConsentConfirmationCodeValidationResponse(scaStatus, consentStatus);

        return SpiResponse.<SpiConsentConfirmationCodeValidationResponse>builder()
                       .payload(response)
                       .build();
    }

    @Override
    public @NotNull SpiResponse<SpiConsentConfirmationCodeValidationResponse> checkConfirmationCode(@NotNull SpiContextData spiContextData,
                                                                                                    @NotNull SpiCheckConfirmationCodeRequest spiCheckConfirmationCodeRequest, @NotNull SpiAspspConsentDataProvider spiAspspConsentDataProvider) {
        try {
            SCAConsentResponseTO sca = consentDataService.response(spiAspspConsentDataProvider.loadAspspConsentData(), SCAConsentResponseTO.class);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            ResponseEntity<AuthConfirmationTO> authConfirmationTOResponse =
                    userMgmtRestClient.verifyAuthConfirmationCode(spiCheckConfirmationCodeRequest.getAuthorisationId(), spiCheckConfirmationCodeRequest.getConfirmationCode());

            AuthConfirmationTO authConfirmationTO = authConfirmationTOResponse.getBody();

            if (authConfirmationTO == null || !authConfirmationTO.isSuccess()) {
                // No response in payload from ASPSP or confirmation code verification failed at ASPSP side.
                return getConfirmationCodeResponseForXs2a(ScaStatus.FAILED, ConsentStatus.REJECTED);
            }

            if (authConfirmationTO.isPartiallyAuthorised()) {
                // This authorisation is finished, but others are left.
                return getConfirmationCodeResponseForXs2a(ScaStatus.FINALISED, ConsentStatus.PARTIALLY_AUTHORISED);
            }

            // Authorisation is finalised and consent becomes valid.
            return getConfirmationCodeResponseForXs2a(ScaStatus.FINALISED, ConsentStatus.VALID);

        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            return SpiResponse.<SpiConsentConfirmationCodeValidationResponse>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PSU_CREDENTIALS_INVALID, devMessage))
                           .build();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    @Override
    public SpiResponse<SpiInitiatePiisConsentResponse> initiatePiisConsent(@NotNull SpiContextData contextData, SpiPiisConsent spiPiisConsent,
                                                                           @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        SpiInitiatePiisConsentResponse spiInitiatePiisConsentResponse = new SpiInitiatePiisConsentResponse();
        spiInitiatePiisConsentResponse.setSpiAccountReference(spiPiisConsent.getAccount());

        boolean multilevelScaRequired;
        try {
            multilevelScaRequired = multilevelScaService.isMultilevelScaRequired(contextData.getPsuData(), Collections.singleton(spiPiisConsent.getAccount()));
        } catch (FeignException e) {
            log.error("Error during REST call for consent initiation to ledgers for account multilevel checking, PSU ID: {}", contextData.getPsuData().getPsuId());
            return SpiResponse.<SpiInitiatePiisConsentResponse>builder()
                           .error(new TppMessage(MessageErrorCode.FORMAT_ERROR_UNKNOWN_ACCOUNT))
                           .build();
        }
        SCAConsentResponseTO response = new SCAConsentResponseTO();
        response.setScaStatus(ScaStatusTO.STARTED);
        response.setMultilevelScaRequired(multilevelScaRequired);
        aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(response, false));

        spiInitiatePiisConsentResponse.setMultilevelScaRequired(multilevelScaRequired);
        return SpiResponse.<SpiInitiatePiisConsentResponse>builder().payload(spiInitiatePiisConsentResponse).build();
    }

    @Override
    public SpiResponse<SpiConsentStatusResponse> getConsentStatus(@NotNull SpiContextData contextData, @NotNull SpiPiisConsent spiPiisConsent,
                                                                  @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return SpiResponse.<SpiConsentStatusResponse>builder()
                       .payload(new SpiConsentStatusResponse(spiPiisConsent.getConsentStatus(), null))
                       .build();
    }

    @Override
    public SpiResponse<SpiResponse.VoidResponse> revokePiisConsent(@NotNull SpiContextData contextData, SpiPiisConsent spiPiisConsent,
                                                                   @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return SpiResponse.<SpiResponse.VoidResponse>builder()
                       .payload(SpiResponse.voidResponse())
                       .build();
    }

    private SpiResponse<SpiConsentConfirmationCodeValidationResponse> getConfirmationCodeResponseForXs2a(ScaStatus scaStatus, ConsentStatus consentStatus) {
        SpiConsentConfirmationCodeValidationResponse response = new SpiConsentConfirmationCodeValidationResponse(scaStatus, consentStatus);

        return SpiResponse.<SpiConsentConfirmationCodeValidationResponse>builder()
                       .payload(response)
                       .build();
    }
}
