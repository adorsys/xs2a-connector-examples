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
import de.adorsys.psd2.xs2a.core.sca.ChallengeData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import feign.FeignException;
import org.apache.commons.lang3.StringUtils;
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

    public GeneralAuthorisationService(UserMgmtRestClient userMgmtRestClient, AuthRequestInterceptor authRequestInterceptor,
                                       ChallengeDataMapper challengeDataMapper, ScaMethodConverter scaMethodConverter, AspspConsentDataService consentDataService) {
        this.userMgmtRestClient = userMgmtRestClient;
        this.authRequestInterceptor = authRequestInterceptor;
        this.challengeDataMapper = challengeDataMapper;
        this.scaMethodConverter = scaMethodConverter;
        this.consentDataService = consentDataService;
    }

    /**
     * First authorization of the PSU.
     * <p>
     * The result of this authorization must contain an scaStatus with following options:
     * - {@link ScaStatusTO#EXEMPTED} : There is no sca needed. The user does not have any sca method anyway.
     * - {@link ScaStatusTO#SCAMETHODSELECTED} : The user has receive an authorization code and must enter it.
     * - {@link ScaStatusTO#PSUIDENTIFIED} : the user must select a authorization method to complete auth.
     * <p>
     * In all three cases, we store the response object for reuse in an {@link AspspConsentData} object.
     *
     * @param spiPsuData       identification data for the psu
     * @param pin              : pis of the psu
     * @param aspspConsentData : credential transport object.
     * @return : the authorisation status
     */

    public <T extends SCAResponseTO> SpiResponse<SpiAuthorisationStatus> authorisePsuForConsent(@NotNull SpiPsuData spiPsuData, String pin, String consentId, T originalResponse, OpTypeTO opType, AspspConsentData aspspConsentData) {
        String authorisationId = originalResponse != null && originalResponse.getAuthorisationId() != null
                                         ? originalResponse.getAuthorisationId()
                                         : Ids.id();
        try {
            String login = spiPsuData.getPsuId();
            logger.info("Authorise user with login={} and password={}", login, StringUtils.repeat("*", 10));
            ResponseEntity<SCALoginResponseTO> response = userMgmtRestClient.authoriseForConsent(login, pin, consentId, authorisationId, opType);
            SpiAuthorisationStatus status = response != null && response.getBody() != null && response.getBody().getBearerToken() != null
                                                    ? SpiAuthorisationStatus.SUCCESS
                                                    : SpiAuthorisationStatus.FAILURE;
            logger.info("Authorisation result is {}", status);
            return new SpiResponse<>(status, aspspConsentData.respondWith(consentDataService.store(Optional.ofNullable(response).map(HttpEntity::getBody).orElseGet(SCALoginResponseTO::new))));
        } catch (FeignException e) {
            return SpiResponse.<SpiAuthorisationStatus>builder()
                           .aspspConsentData(aspspConsentData)
                           .fail(SpiFailureResponseHelper.getSpiFailureResponse(e, logger));
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

    public SpiResponse<SpiAuthorizationCodeResult> getResponseIfScaSelected(AspspConsentData aspspConsentData, SCAResponseTO sca) {
        if (SCAMETHODSELECTED.equals(sca.getScaStatus())) {
            return returnScaMethodSelection(aspspConsentData, sca);
        } else {
            return SpiResponse.<SpiAuthorizationCodeResult>builder()
                           .aspspConsentData(aspspConsentData)
                           .message(String.format("Wrong state. Expecting sca status to be %s if auth was sent or %s if auth code wasn't sent yet. But was %s.", SCAMETHODSELECTED.name(), PSUIDENTIFIED.name(), sca.getScaStatus().name()))
                           .fail(SpiResponseStatus.LOGICAL_FAILURE);
        }
    }

    public SpiResponse<SpiAuthorizationCodeResult> returnScaMethodSelection(AspspConsentData aspspConsentData, SCAResponseTO sca) {
        SpiAuthorizationCodeResult spiAuthorizationCodeResult = new SpiAuthorizationCodeResult();
        ChallengeData challengeData = Optional.ofNullable(challengeDataMapper.toChallengeData(sca.getChallengeData())).orElse(new ChallengeData());
        spiAuthorizationCodeResult.setChallengeData(challengeData);
        spiAuthorizationCodeResult.setSelectedScaMethod(scaMethodConverter.toSpiAuthenticationObject(sca.getChosenScaMethod()));
        return SpiResponse.<SpiAuthorizationCodeResult>builder()
                       .aspspConsentData(aspspConsentData.respondWith(consentDataService.store(sca)))
                       .payload(spiAuthorizationCodeResult)
                       .success();
    }

}
