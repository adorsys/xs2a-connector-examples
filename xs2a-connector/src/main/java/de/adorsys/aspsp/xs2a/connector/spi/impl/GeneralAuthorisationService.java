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

import de.adorsys.aspsp.xs2a.connector.spi.converter.ChallengeDataMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.ledgers.middleware.api.domain.sca.OpTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCALoginResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.UserMgmtRestClient;
import de.adorsys.ledgers.util.Ids;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.sca.ChallengeData;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiPsuAuthorisationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import feign.FeignException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO.PSUIDENTIFIED;
import static de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO.SCAMETHODSELECTED;

@Component
public class GeneralAuthorisationService {
    private static final Logger logger = LoggerFactory.getLogger(GeneralAuthorisationService.class);
    private final UserMgmtRestClient userMgmtRestClient;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final ChallengeDataMapper challengeDataMapper;
    private final ScaMethodConverter scaMethodConverter;
    private final AspspConsentDataService consentDataService;
    private final FeignExceptionReader feignExceptionReader;

    public GeneralAuthorisationService(UserMgmtRestClient userMgmtRestClient, AuthRequestInterceptor authRequestInterceptor,
                                       ChallengeDataMapper challengeDataMapper, ScaMethodConverter scaMethodConverter, AspspConsentDataService consentDataService, FeignExceptionReader feignExceptionReader) {
        this.userMgmtRestClient = userMgmtRestClient;
        this.authRequestInterceptor = authRequestInterceptor;
        this.challengeDataMapper = challengeDataMapper;
        this.scaMethodConverter = scaMethodConverter;
        this.consentDataService = consentDataService;
        this.feignExceptionReader = feignExceptionReader;
    }

    /**
     * First authorization of the PSU.
     * <p>
     * The result of this authorisation must contain an scaStatus with following options:
     * - {@link ScaStatusTO#EXEMPTED}: There is no SCA needed. The user does not have any SCA method anyway.
     * - {@link ScaStatusTO#SCAMETHODSELECTED}: The user has receive an authorisation code and must enter it.
     * - {@link ScaStatusTO#PSUIDENTIFIED}: the user must select an authorisation method to complete authorisation.
     * <p>
     * In all three cases, we store the response object for reuse in an {@link AspspConsentData} object.
     *
     * @param spiPsuData               identification data for the psu
     * @param pin                      : pis of the psu
     * @param aspspConsentDataProvider :Provides access to read/write encrypted data to be stored in the consent management system
     * @return : the authorisation status
     */

    public <T extends SCAResponseTO> SpiResponse<SpiPsuAuthorisationResponse> authorisePsuForConsent(@NotNull SpiPsuData spiPsuData, String pin, String consentId, T originalResponse, OpTypeTO opType, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        String authorisationId = originalResponse != null && originalResponse.getAuthorisationId() != null
                                         ? originalResponse.getAuthorisationId()
                                         : Ids.id();
        try {
            String login = spiPsuData.getPsuId();
            logger.info("Authorise user with login: {}", login);
            ResponseEntity<SCALoginResponseTO> response = userMgmtRestClient.authoriseForConsent(login, pin, consentId, authorisationId, opType);
            SpiAuthorisationStatus status = response != null && response.getBody() != null && response.getBody().getBearerToken() != null
                                                    ? SpiAuthorisationStatus.SUCCESS
                                                    : SpiAuthorisationStatus.FAILURE;
            logger.info("Authorisation status is: {}", status);

            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(Optional.ofNullable(response)
                                                                                             .map(HttpEntity::getBody)
                                                                                             .orElseGet(SCALoginResponseTO::new)));
            return SpiResponse.<SpiPsuAuthorisationResponse>builder()
                           .payload(new SpiPsuAuthorisationResponse(status, false))
                           .build();
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Authorise PSU for consent failed: authorisation ID {}, consent ID {}, devMessage {}", authorisationId, consentId, devMessage);
            return SpiResponse.<SpiPsuAuthorisationResponse>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PSU_CREDENTIALS_INVALID, devMessage, "PSU authorisation request was failed."))
                           .build();
        }
    }

    public BearerTokenTO validateToken(String accessToken) {
        try {
            authRequestInterceptor.setAccessToken(accessToken);
            return userMgmtRestClient.validate(accessToken).getBody();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    public SpiResponse<SpiAuthorizationCodeResult> getResponseIfScaSelected(SpiAspspConsentDataProvider aspspConsentDataProvider, SCAResponseTO sca) {
        if (SCAMETHODSELECTED.equals(sca.getScaStatus())) {
            return returnScaMethodSelection(aspspConsentDataProvider, sca);
        } else {
            return SpiResponse.<SpiAuthorizationCodeResult>builder()
                           .error(new TppMessage(MessageErrorCode.FORMAT_ERROR,
                                                 String.format("Wrong state. Expecting SCA status to be %s if auth was sent or %s if auth code wasn't sent yet. But was %s.", SCAMETHODSELECTED.name(), PSUIDENTIFIED.name(), sca.getScaStatus().name())))
                           .build();
        }
    }

    public SpiResponse<SpiAuthorizationCodeResult> returnScaMethodSelection(SpiAspspConsentDataProvider aspspConsentDataProvider, SCAResponseTO sca) {
        SpiAuthorizationCodeResult spiAuthorizationCodeResult = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = Optional.ofNullable(challengeDataMapper.toChallengeData(sca.getChallengeData())).orElse(new ChallengeData());
        spiAuthorizationCodeResult.setChallengeData(challengeData);
        spiAuthorizationCodeResult.setSelectedScaMethod(scaMethodConverter.toSpiAuthenticationObject(sca.getChosenScaMethod()));
        aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(sca));
        return SpiResponse.<SpiAuthorizationCodeResult>builder()
                       .payload(spiAuthorizationCodeResult)
                       .build();
    }
}
