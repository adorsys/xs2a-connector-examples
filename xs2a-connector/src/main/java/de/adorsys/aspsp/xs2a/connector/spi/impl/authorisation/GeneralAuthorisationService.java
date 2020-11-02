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

package de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation;

import de.adorsys.aspsp.xs2a.connector.spi.converter.ChallengeDataMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionHandler;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.ledgers.middleware.api.domain.sca.*;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.RedirectScaRestClient;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.sca.ChallengeData;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiPsuAuthorisationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO.PSUIDENTIFIED;
import static de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO.SCAMETHODSELECTED;

@Component
@RequiredArgsConstructor
public class GeneralAuthorisationService {
    private static final String ATTEMPT_FAILURE = "PSU_AUTH_ATTEMPT_INVALID";
    private static final Logger logger = LoggerFactory.getLogger(GeneralAuthorisationService.class);
    private final AuthRequestInterceptor authRequestInterceptor;
    private final ChallengeDataMapper challengeDataMapper;
    private final AspspConsentDataService consentDataService;
    private final FeignExceptionReader feignExceptionReader;
    private final RedirectScaRestClient redirectScaRestClient;
    private final ScaMethodConverter scaMethodConverter;

    public SpiResponse<SpiPsuAuthorisationResponse> authorisePsuInternal(String businessObjectId, String authorisationId, OpTypeTO operationType, GlobalScaResponseTO scaResponse, SpiAspspConsentDataProvider aspspConsentDataProvider) {

        aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(scaResponse));
        authRequestInterceptor.setAccessToken(scaResponse.getBearerToken().getAccess_token());

        StartScaOprTO startScaOprTO = new StartScaOprTO(businessObjectId, operationType);
        startScaOprTO.setAuthorisationId(authorisationId);

        ResponseEntity<GlobalScaResponseTO> startScaResponse = redirectScaRestClient.startSca(startScaOprTO);

        GlobalScaResponseTO startScaResponseBody = startScaResponse.getBody();
        startScaResponseBody.setBearerToken(scaResponse.getBearerToken());

        aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(startScaResponseBody));

        try {
            SpiAuthorisationStatus status = startScaResponse.getBody().getBearerToken().getAccess_token() != null
                                                    ? SpiAuthorisationStatus.SUCCESS
                                                    : SpiAuthorisationStatus.FAILURE;
            logger.info("Authorisation status is: {}", status);

            SpiResponse<SpiPsuAuthorisationResponse> response = SpiResponse.<SpiPsuAuthorisationResponse>builder()
                                                                        .payload(new SpiPsuAuthorisationResponse(false, status))
                                                                        .build();
            if (!response.isSuccessful()) {
                SpiPsuAuthorisationResponse spiResponse = response.getPayload();
                if (spiResponse != null && spiResponse.getSpiAuthorisationStatus() == SpiAuthorisationStatus.ATTEMPT_FAILURE) {
                    return response;
                }
                return SpiResponse.<SpiPsuAuthorisationResponse>builder()
                               .payload(new SpiPsuAuthorisationResponse(response.getPayload().isScaExempted(), SpiAuthorisationStatus.FAILURE))
                               .build();
            }

            return response;
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Authorise PSU internal failed: authorisation ID {}, business object ID: {}, devMessage: {}", authorisationId, businessObjectId, devMessage);

            String errorCode = feignExceptionReader.getErrorCode(feignException);
            if (errorCode.equals(ATTEMPT_FAILURE)) {
                return SpiResponse.<SpiPsuAuthorisationResponse>builder()
                               .payload(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.ATTEMPT_FAILURE))
                               .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PSU_CREDENTIALS_INVALID, devMessage))
                               .build();
            }
            return SpiResponse.<SpiPsuAuthorisationResponse>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PSU_CREDENTIALS_INVALID, devMessage))
                           .build();
        }
    }

    public SpiResponse<SpiAuthorizationCodeResult> getResponseIfScaSelected(SpiAspspConsentDataProvider aspspConsentDataProvider, GlobalScaResponseTO sca,
                                                                            String authenticationMethodId) {
        if (SCAMETHODSELECTED.equals(sca.getScaStatus())) {
            return returnScaMethodSelection(aspspConsentDataProvider, sca, authenticationMethodId);
        } else {
            Object[] messageTextArgs = {SCAMETHODSELECTED.toString(), PSUIDENTIFIED.toString(), sca.getScaStatus().toString()};
            return SpiResponse.<SpiAuthorizationCodeResult>builder()
                           .error(new TppMessage(MessageErrorCode.SCA_INVALID, messageTextArgs))
                           .build();
        }
    }

    public SpiResponse<SpiAuthorizationCodeResult> returnScaMethodSelection(SpiAspspConsentDataProvider aspspConsentDataProvider, GlobalScaResponseTO sca,
                                                                            String authenticationMethodId) {
        SpiAuthorizationCodeResult spiAuthorizationCodeResult = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = Optional.ofNullable(challengeDataMapper.toChallengeData(sca.getChallengeData())).orElse(new ChallengeData());
        spiAuthorizationCodeResult.setChallengeData(challengeData);
        Optional<ScaUserDataTO> scaUserDataOptional = sca.getScaMethods().stream().filter(method -> method.getId().equals(authenticationMethodId)).findFirst();
        if (scaUserDataOptional.isPresent()) {
            spiAuthorizationCodeResult.setSelectedScaMethod(scaMethodConverter.toAuthenticationObject(scaUserDataOptional.get()));
        }
        aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(sca));
        return SpiResponse.<SpiAuthorizationCodeResult>builder()
                       .payload(spiAuthorizationCodeResult)
                       .build();
    }
}
