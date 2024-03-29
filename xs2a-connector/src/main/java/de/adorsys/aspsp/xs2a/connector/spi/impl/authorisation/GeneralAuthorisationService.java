/*
 * Copyright 2018-2023 adorsys GmbH & Co KG
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
import de.adorsys.aspsp.xs2a.connector.spi.util.AspspConsentDataExtractor;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.OpTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.StartScaOprTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.RedirectScaRestClient;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiPsuAuthorisationResponse;
import de.adorsys.psd2.xs2a.spi.domain.error.SpiMessageErrorCode;
import de.adorsys.psd2.xs2a.spi.domain.error.SpiTppMessage;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.sca.SpiChallengeData;
import de.adorsys.psd2.xs2a.spi.domain.sca.SpiScaStatus;
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
    private static final Logger logger = LoggerFactory.getLogger(GeneralAuthorisationService.class);
    private final AuthRequestInterceptor authRequestInterceptor;
    private final ChallengeDataMapper challengeDataMapper;
    private final AspspConsentDataService consentDataService;
    private final FeignExceptionReader feignExceptionReader;
    private final RedirectScaRestClient redirectScaRestClient;
    private final ScaMethodConverter scaMethodConverter;

    public SpiResponse<SpiPsuAuthorisationResponse> authorisePsuInternal(String businessObjectId, String authorisationId,
                                                                         OpTypeTO operationType, GlobalScaResponseTO scaResponse,
                                                                         SpiAspspConsentDataProvider aspspConsentDataProvider) {

        aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(scaResponse));
        authRequestInterceptor.setAccessToken(scaResponse.getBearerToken().getAccess_token());

        String encryptedConsentId = AspspConsentDataExtractor.extractEncryptedConsentId(aspspConsentDataProvider);
        StartScaOprTO startScaOprTO = new StartScaOprTO(businessObjectId, encryptedConsentId, authorisationId, operationType);

        try {
            ResponseEntity<GlobalScaResponseTO> startScaResponse = redirectScaRestClient.startSca(startScaOprTO);

            if (startScaResponse == null || startScaResponse.getBody() == null) {
                logger.error("Start SCA response is NULL");
                return SpiResponse.<SpiPsuAuthorisationResponse>builder()
                               .error(new SpiTppMessage(SpiMessageErrorCode.FORMAT_ERROR))
                               .build();
            }

            GlobalScaResponseTO startScaResponseBody = startScaResponse.getBody();
            startScaResponseBody.setBearerToken(scaResponse.getBearerToken()); //NOSONAR

            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(startScaResponseBody));

            SpiAuthorisationStatus status = startScaResponseBody.getBearerToken().getAccess_token() != null
                                                    ? SpiAuthorisationStatus.SUCCESS
                                                    : SpiAuthorisationStatus.FAILURE;
            logger.info("Authorisation status is: {}", status);

            return SpiResponse.<SpiPsuAuthorisationResponse>builder()
                           .payload(new SpiPsuAuthorisationResponse(false, status))
                           .build();
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Authorise PSU internal failed: authorisation ID {}, business object ID: {}, devMessage: {}", authorisationId, businessObjectId, devMessage);
            return SpiResponse.<SpiPsuAuthorisationResponse>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, SpiMessageErrorCode.SCA_INVALID, devMessage))
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
                           .error(new SpiTppMessage(SpiMessageErrorCode.SCA_INVALID, messageTextArgs))
                           .build();
        }
    }

    public SpiResponse<SpiAuthorizationCodeResult> returnScaMethodSelection(SpiAspspConsentDataProvider aspspConsentDataProvider, GlobalScaResponseTO sca,
                                                                            String authenticationMethodId) {
        SpiChallengeData challengeData = Optional.ofNullable(challengeDataMapper.toChallengeData(sca.getChallengeData()))
                                              .orElse(new SpiChallengeData());
        SpiAuthenticationObject selectedScaMethod = sca.getScaMethods().stream()
                                                         .filter(method -> method.getId().equals(authenticationMethodId))
                                                         .findFirst()
                                                         .map(scaMethodConverter::toAuthenticationObject)
                                                         .orElse(null);
        aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(sca));
        return SpiResponse.<SpiAuthorizationCodeResult>builder()
                       .payload(new SpiAuthorizationCodeResult(false, challengeData, selectedScaMethod,
                                                               SpiScaStatus.fromValue(sca.getScaStatus().name())))
                       .build();
    }
}
