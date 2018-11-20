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
import de.adorsys.ledgers.domain.sca.*;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.PaymentAuthorisationSpi;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentAuthorisationSpiImpl implements PaymentAuthorisationSpi {
    private static final Logger logger = LoggerFactory.getLogger(PaymentAuthorisationSpiImpl.class);

    private final LedgersRestClient ledgersRestClient;
    private final ScaMethodConverter scaMethodConverter;

    public PaymentAuthorisationSpiImpl(LedgersRestClient ledgersRestClient, ScaMethodConverter scaMethodConverter) {
        this.ledgersRestClient = ledgersRestClient;
        this.scaMethodConverter = scaMethodConverter;
    }

    @Override
    public SpiResponse<SpiAuthorisationStatus> authorisePsu(@NotNull SpiPsuData spiPsuData, String pin, SpiPayment spiPayment, AspspConsentData aspspConsentData) {
        String login = spiPsuData.getPsuId();
        logger.info("Authorise user with login={} and password={}", login, StringUtils.repeat("*", pin.length()));

        boolean isAuthorised = ledgersRestClient.authorise(login, pin);
        SpiAuthorisationStatus status = isAuthorised ? SpiAuthorisationStatus.SUCCESS : SpiAuthorisationStatus.FAILURE;
        logger.info("Authorisation result is {}", status);

        return new SpiResponse<>(status, aspspConsentData);
    }

    @Override
    public SpiResponse<List<SpiAuthenticationObject>> requestAvailableScaMethods(@NotNull SpiPsuData spiPsuData, SpiPayment spiPayment, AspspConsentData aspspConsentData) {
        String userLogin = spiPsuData.getPsuId();
        logger.info("Retrieving sca methods for user {}", userLogin);

        List<SCAMethodTO> scaMethods = ledgersRestClient.getUserScaMethods(userLogin);
        logger.debug("These are sca methods that was found {}", scaMethods);

        List<SpiAuthenticationObject> authenticationObjects = scaMethodConverter.toSpiAuthenticationObjectList(scaMethods);

        return SpiResponse.<List<SpiAuthenticationObject>>builder()
                       .aspspConsentData(aspspConsentData)
                       .payload(authenticationObjects)
                       .success();

//        todo: proceed with rest exceptions
    }

    @Override
    public @NotNull SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(@NotNull SpiPsuData spiPsuData, @NotNull String authenticationMethodId, @NotNull SpiPayment spiPayment, @NotNull AspspConsentData aspspConsentData) {
        String userLogin = spiPsuData.getPsuId();
        String paymentId = spiPayment.getPaymentId();

        AuthCodeDataTO data = new AuthCodeDataTO(userLogin, authenticationMethodId, paymentId, spiPayment.toString());
        logger.info("Request to generate SCA {}", data);

        SCAGenerationResponse response = ledgersRestClient.generate(data);
        logger.info("SCA was send, operationId is {}", response.getOpId());

        return SpiResponse.<SpiAuthorizationCodeResult>builder()
                       .aspspConsentData(aspspConsentData)
                       .success();
    }
}
