/*
 * Copyright 2018-2018 adorsys GmbH & Co KG
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

package de.adorsys.aspsp.xs2a.spi.impl;

import de.adorsys.aspsp.xs2a.spi.converter.ScaMethodConverter;
import de.adorsys.ledgers.LedgersRestClient;
import de.adorsys.ledgers.domain.SCAValidationRequest;
import de.adorsys.ledgers.domain.sca.AuthCodeDataTO;
import de.adorsys.ledgers.domain.sca.SCAGenerationResponse;
import de.adorsys.ledgers.domain.sca.SCAMethodTO;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.domain.MessageErrorCode;
import de.adorsys.psd2.xs2a.exception.RestException;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.AisConsentSpi;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class AisConsentSpiImpl implements AisConsentSpi {
    private static final Logger logger = LoggerFactory.getLogger(AisConsentSpiImpl.class);
    private static final String TEST_ASPSP_DATA = "Test aspsp data";
    private static final String TEST_MESSAGE = "Test message";
    private final LedgersRestClient ledgersRestClient;
    private final ScaMethodConverter scaMethodConverter;

    public AisConsentSpiImpl(LedgersRestClient ledgersRestClient, ScaMethodConverter scaMethodConverter) {
        this.ledgersRestClient = ledgersRestClient;
        this.scaMethodConverter = scaMethodConverter;
    }

    //TODO should be fully implemented after implementation of Security on Ledgers side.
    @Override
    public SpiResponse<SpiResponse.VoidResponse> initiateAisConsent(@NotNull SpiPsuData spiPsuData, SpiAccountConsent spiAccountConsent, AspspConsentData aspspConsentData) {
        return SpiResponse.<SpiResponse.VoidResponse>builder()
                       .payload(SpiResponse.voidResponse())
                       .aspspConsentData(aspspConsentData.respondWith(TEST_ASPSP_DATA.getBytes()))     // added for test purposes TODO remove if some requirements will be received https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/394
                       .message(Collections.singletonList(TEST_MESSAGE))                                      // added for test purposes TODO remove if some requirements will be received https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/394
                       .success();
    }

    @Override
    public SpiResponse<SpiResponse.VoidResponse> revokeAisConsent(@NotNull SpiPsuData spiPsuData, SpiAccountConsent spiAccountConsent, AspspConsentData aspspConsentData) {
        return SpiResponse.<SpiResponse.VoidResponse>builder()
                       .payload(SpiResponse.voidResponse())
                       .aspspConsentData(aspspConsentData.respondWith(TEST_ASPSP_DATA.getBytes()))            // added for test purposes TODO remove if some requirements will be received https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/394
                       .message(Collections.singletonList(TEST_MESSAGE))                                      // added for test purposes TODO remove if some requirements will be received https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/394
                       .success();
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> verifyScaAuthorisation(@NotNull SpiPsuData spiPsuData, @NotNull SpiScaConfirmation spiScaConfirmation, @NotNull SpiAccountConsent spiAccountConsent, @NotNull AspspConsentData aspspConsentData) {
        logger.info("Verifying SCA code");
        try {
            String opData = buildOpData(spiAccountConsent);
            SCAValidationRequest validationRequest = new SCAValidationRequest(opData, spiScaConfirmation.getTanNumber());//TODO fix this! it is not correct!
            boolean isValid = ledgersRestClient.validate(spiScaConfirmation.getPaymentId(), validationRequest);
            logger.info("Validation result is {}", isValid);
            if (isValid) {
                return SpiResponse.<SpiResponse.VoidResponse>builder()
                               .payload(SpiResponse.voidResponse())
                               .aspspConsentData(aspspConsentData.respondWith(TEST_ASPSP_DATA.getBytes()))            // added for test purposes TODO remove if some requirements will be received https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/394
                               .message(Collections.singletonList(TEST_MESSAGE))                                      // added for test purposes TODO remove if some requirements will be received https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/394
                               .success();
            }
            throw new RestException(MessageErrorCode.CONSENT_INVALID);
        } catch (RestException e) {
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .aspspConsentData(aspspConsentData.respondWith(TEST_ASPSP_DATA.getBytes()))            // added for test purposes TODO remove if some requirements will be received https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/394
                           .fail(getSpiFailureResponse(e));
        }
    }

    @Override
    public SpiResponse<SpiAuthorisationStatus> authorisePsu(@NotNull SpiPsuData spiPsuData, String pin, SpiAccountConsent spiAccountConsent, AspspConsentData aspspConsentData) {
        try {
            String login = spiPsuData.getPsuId();
            logger.info("Authorise user with login={} and password={}", login, StringUtils.repeat("*", pin.length()));
            boolean isAuthorised = ledgersRestClient.authorise(login, pin);
            SpiAuthorisationStatus status = isAuthorised
                                                    ? SpiAuthorisationStatus.SUCCESS
                                                    : SpiAuthorisationStatus.FAILURE;
            logger.info("Authorisation result is {}", status);
            return new SpiResponse<>(status, aspspConsentData);
        } catch (RestException e) {
            return SpiResponse.<SpiAuthorisationStatus>builder()
                           .aspspConsentData(aspspConsentData.respondWith(TEST_ASPSP_DATA.getBytes()))            // added for test purposes TODO remove if some requirements will be received https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/394
                           .fail(getSpiFailureResponse(e));
        }
    }

    @Override
    public SpiResponse<List<SpiAuthenticationObject>> requestAvailableScaMethods(@NotNull SpiPsuData spiPsuData, SpiAccountConsent spiAccountConsent, AspspConsentData aspspConsentData) {
        try {
            String userLogin = spiPsuData.getPsuId();
            logger.info("Retrieving sca methods for user {}", userLogin);
            List<SCAMethodTO> scaMethods = ledgersRestClient.getUserScaMethods(userLogin);
            logger.debug("These are sca methods that were found {}", scaMethods);

            List<SpiAuthenticationObject> authenticationObjects = scaMethodConverter.toSpiAuthenticationObjectList(scaMethods);
            return SpiResponse.<List<SpiAuthenticationObject>>builder()
                           .aspspConsentData(aspspConsentData)
                           .payload(authenticationObjects)
                           .success();
        } catch (RestException e) {
            return SpiResponse.<List<SpiAuthenticationObject>>builder()
                           .aspspConsentData(aspspConsentData)
                           .fail(getSpiFailureResponse(e));
        }
    }

    @Override
    public @NotNull SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(@NotNull SpiPsuData spiPsuData, @NotNull String authenticationMethodId, @NotNull SpiAccountConsent spiAccountConsent, @NotNull AspspConsentData aspspConsentData) {
        try {
            String userLogin = spiPsuData.getPsuId();
            String paymentId = spiAccountConsent.getId();
            String opData = buildOpData(spiAccountConsent);
            AuthCodeDataTO data = new AuthCodeDataTO(userLogin, authenticationMethodId, paymentId, opData);
            logger.info("Request to generate SCA {}", data);

            SCAGenerationResponse response = ledgersRestClient.generate(data);
            logger.info("SCA was send, operationId is {}", response.getOpId());

            return SpiResponse.<SpiAuthorizationCodeResult>builder()
                           .aspspConsentData(aspspConsentData)
                           .success();
        } catch (RestException e) {
            return SpiResponse.<SpiAuthorizationCodeResult>builder()
                           .aspspConsentData(aspspConsentData)
                           .fail(getSpiFailureResponse(e));
        }
    }

    private String buildOpData(SpiAccountConsent spiAccountConsent) {
        OpData opData = OpData.of(spiAccountConsent);
        return opData.toString();
    }

    @NotNull
    private SpiResponseStatus getSpiFailureResponse(RestException e) {
        logger.error(e.getMessage(), e);
        return (e.getHttpStatus() == HttpStatus.INTERNAL_SERVER_ERROR)
                       ? SpiResponseStatus.TECHNICAL_FAILURE
                       : SpiResponseStatus.LOGICAL_FAILURE;
    }

    // todo: @fpo let's agree with opData content
    private static final class OpData {
        private List<SpiAccountReference> accounts;
        // todo: discuss because it could modified
        private SpiPsuData psuData;
        private String tppId;

        OpData(List<SpiAccountReference> accounts, SpiPsuData psuData, String tppId) {
            this.accounts = Collections.unmodifiableList(accounts);
            this.psuData = ObjectUtils.clone(psuData);
            this.tppId = tppId;
        }

        private OpData(final SpiAccountConsent consent) {
            this(consent.getAccess().getAccounts(), consent.getPsuData(), consent.getTppId());
        }

        public List<SpiAccountReference> getAccounts() {
            return accounts;
        }

        public SpiPsuData getPsuData() {
            return psuData;
        }

        public String getTppId() {
            return tppId;
        }

        @Override
        public String toString() {
            return "{" +
                           "\"accounts\":" + accounts +
                           ", \"psuData\":" + psuData +
                           ", \"tppId\":\"" + tppId + '\"' +
                           '}';
        }

        @SuppressWarnings("PMD.ShortMethodName")
        static OpData of(final SpiAccountConsent consent) {
            return new OpData(consent);
        }
    }
}
