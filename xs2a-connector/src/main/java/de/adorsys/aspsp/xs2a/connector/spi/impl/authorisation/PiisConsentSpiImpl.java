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

package de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation;

import de.adorsys.aspsp.xs2a.connector.spi.converter.AisConsentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaResponseMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionHandler;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.aspsp.xs2a.connector.spi.impl.MultilevelScaService;
import de.adorsys.ledgers.keycloak.client.api.KeycloakTokenService;
import de.adorsys.ledgers.middleware.api.domain.sca.*;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.ConsentRestClient;
import de.adorsys.ledgers.rest.client.RedirectScaRestClient;
import de.adorsys.ledgers.rest.client.UserMgmtRestClient;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.core.tpp.TppInfo;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiCheckConfirmationCodeRequest;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.consent.*;
import de.adorsys.psd2.xs2a.spi.domain.piis.SpiPiisConsent;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.PiisConsentSpi;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Slf4j
@Component
public class PiisConsentSpiImpl extends AbstractAuthorisationSpi<SpiPiisConsent> implements PiisConsentSpi {
    // TODO REPLACE WITH PIIS FLOW WHEN LEDGERS STARTS TO SUPPORT PIIS CONSENT https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/-/issues/1323
    private static final String SCA_STATUS_LOG = "SCA status is {}";
    private static final String ATTEMPT_FAILURE = "SCA_VALIDATION_ATTEMPT_FAILED";

    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final MultilevelScaService multilevelScaService;
    private final FeignExceptionReader feignExceptionReader;
    private final UserMgmtRestClient userMgmtRestClient;
    private final RedirectScaRestClient redirectScaRestClient;
    private final ConsentRestClient consentRestClient;
    private final AisConsentMapper aisConsentMapper;
    private final ScaResponseMapper scaResponseMapper;

    public PiisConsentSpiImpl(AuthRequestInterceptor authRequestInterceptor,
                              AspspConsentDataService consentDataService, GeneralAuthorisationService authorisationService,
                              ScaMethodConverter scaMethodConverter, FeignExceptionReader feignExceptionReader,
                              MultilevelScaService multilevelScaService, UserMgmtRestClient userMgmtRestClient,
                              RedirectScaRestClient redirectScaRestClient,
                              KeycloakTokenService keycloakTokenService, ConsentRestClient consentRestClient, AisConsentMapper aisConsentMapper, ScaResponseMapper scaResponseMapper) {
        super(authRequestInterceptor, consentDataService, authorisationService, scaMethodConverter, feignExceptionReader, keycloakTokenService, redirectScaRestClient);
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
        this.multilevelScaService = multilevelScaService;
        this.feignExceptionReader = feignExceptionReader;
        this.userMgmtRestClient = userMgmtRestClient;
        this.redirectScaRestClient = redirectScaRestClient;
        this.consentRestClient = consentRestClient;
        this.aisConsentMapper = aisConsentMapper;
        this.scaResponseMapper = scaResponseMapper;
    }

    @Override
    protected ResponseEntity<GlobalScaResponseTO> getSelectMethodResponse(@NotNull String authenticationMethodId, GlobalScaResponseTO sca) {
        ResponseEntity<GlobalScaResponseTO> scaResponse = redirectScaRestClient.selectMethod(sca.getAuthorisationId(), authenticationMethodId);

        return scaResponse.getStatusCode() == HttpStatus.OK
                       ? ResponseEntity.ok(scaResponse.getBody())
                       : ResponseEntity.badRequest().build();
    }

    @Override
    protected GlobalScaResponseTO getScaObjectResponse(@NotNull SpiAspspConsentDataProvider aspspConsentDataProvider, boolean checkCredentials) {
        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();
        return consentDataService.response(aspspConsentData, checkCredentials);
    }

    @Override
    protected String getBusinessObjectId(SpiPiisConsent businessObject) {
        return businessObject.getId();
    }

    @Override
    protected OpTypeTO getOpType() {
        return OpTypeTO.CONSENT;
    }

    @Override
    protected TppMessage getAuthorisePsuFailureMessage(SpiPiisConsent businessObject) {
        log.error("Initiate consent failed: consent ID {}", businessObject.getId());
        return new TppMessage(MessageErrorCode.FORMAT_ERROR_UNKNOWN_ACCOUNT);
    }

    @Override
    protected GlobalScaResponseTO initiateBusinessObject(SpiPiisConsent piisConsent, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider, String authorisationId) {
        try {

            SpiAccountConsent mockedConsent = new SpiAccountConsent();
            SpiAccountAccess access = new SpiAccountAccess();
            access.setAccounts(Collections.singletonList(piisConsent.getAccount()));
            mockedConsent.setAccess(access);
            TppInfo tppInfo = new TppInfo();
            tppInfo.setAuthorityId("TPP ID");
            tppInfo.setAuthorisationNumber("TPP ID");
            mockedConsent.setTppInfo(tppInfo);
            mockedConsent.setId(piisConsent.getId());

            ResponseEntity<SCAConsentResponseTO> consentResponseEntity = consentRestClient.initiateAisConsent(piisConsent.getId(), aisConsentMapper.mapToAisConsent(mockedConsent));
            SCAConsentResponseTO consentResponse = consentResponseEntity.getBody();

            authRequestInterceptor.setAccessToken(consentResponse.getBearerToken().getAccess_token());
            consentResponse.setAuthorisationId(authorisationId);

            // EXEMPTED here means that we should not start SCA in ledgers.
            if (consentResponse.getScaStatus() == ScaStatusTO.EXEMPTED) {
                return scaResponseMapper.toGlobalScaResponse(consentResponse);
            }

            StartScaOprTO startScaOprTO = new StartScaOprTO(piisConsent.getId(), OpTypeTO.CONSENT);
            startScaOprTO.setAuthorisationId(authorisationId);

            ResponseEntity<GlobalScaResponseTO> consentScaResponse = redirectScaRestClient.startSca(startScaOprTO);
            return consentScaResponse.getBody();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    @Override
    protected boolean isFirstInitiationOfMultilevelSca(SpiPiisConsent businessObject, GlobalScaResponseTO scaConsentResponseTO) {
        return !scaConsentResponseTO.isMultilevelScaRequired() || businessObject.getPsuData().size() <= 1;
    }

    @Override
    protected GlobalScaResponseTO executeBusinessObject(SpiPiisConsent businessObject) {
        return null;
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
            GlobalScaResponseTO sca = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData());
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            ResponseEntity<GlobalScaResponseTO> authorizeConsentResponse = redirectScaRestClient.validateScaCode(sca.getAuthorisationId(), spiScaConfirmation.getTanNumber());
            GlobalScaResponseTO globalScaResponse = authorizeConsentResponse.getBody();

            String scaStatusName = sca.getScaStatus().name();
            log.info(SCA_STATUS_LOG, scaStatusName);
            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(globalScaResponse, !globalScaResponse.isPartiallyAuthorised()));

            // TODO use real sca status from Ledgers for resolving consent status https://git.adorsys.de/adorsys/xs2a/ledgers/issues/206
            return SpiResponse.<SpiVerifyScaAuthorisationResponse>builder()
                           .payload(new SpiVerifyScaAuthorisationResponse(getConsentStatus(globalScaResponse)))
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

    private ConsentStatus getConsentStatus(GlobalScaResponseTO globalScaResponse) {
        if (globalScaResponse != null
                    && globalScaResponse.isPartiallyAuthorised()
                    && ScaStatusTO.FINALISED.equals(globalScaResponse.getScaStatus())) {
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
            GlobalScaResponseTO sca = consentDataService.response(spiAspspConsentDataProvider.loadAspspConsentData());
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
        GlobalScaResponseTO response = new GlobalScaResponseTO();
        response.setOpType(OpTypeTO.CONSENT);
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
