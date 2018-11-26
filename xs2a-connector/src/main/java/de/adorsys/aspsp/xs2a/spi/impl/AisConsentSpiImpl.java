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

import de.adorsys.ledgers.LedgersRestClient;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.AisConsentSpi;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class AisConsentSpiImpl implements AisConsentSpi {
    private static final Logger logger = LoggerFactory.getLogger(AisConsentSpiImpl.class);

    private final LedgersRestClient ledgersRestClient;

    public AisConsentSpiImpl(LedgersRestClient ledgersRestClient) {
        this.ledgersRestClient = ledgersRestClient;
    }

    //TODO should be fully implemented after implementation of Security on Ledgers side.
    @Override
    public SpiResponse<SpiResponse.VoidResponse> initiateAisConsent(@NotNull SpiPsuData spiPsuData, SpiAccountConsent spiAccountConsent, AspspConsentData aspspConsentData) {
        return SpiResponse.<SpiResponse.VoidResponse>builder().payload(SpiResponse.voidResponse()).success();
    }

    @Override
    public SpiResponse<SpiResponse.VoidResponse> revokeAisConsent(@NotNull SpiPsuData spiPsuData, SpiAccountConsent spiAccountConsent, AspspConsentData aspspConsentData) {
        return SpiResponse.<SpiResponse.VoidResponse>builder().payload(SpiResponse.voidResponse()).success();
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> verifyScaAuthorisation(@NotNull SpiPsuData spiPsuData, @NotNull SpiScaConfirmation spiScaConfirmation, @NotNull SpiAccountConsent spiAccountConsent, @NotNull AspspConsentData aspspConsentData) {
        return SpiResponse.<SpiResponse.VoidResponse>builder().payload(SpiResponse.voidResponse()).success();
    }

    @Override
    public SpiResponse<SpiAuthorisationStatus> authorisePsu(@NotNull SpiPsuData spiPsuData, String pin, SpiAccountConsent spiAccountConsent, AspspConsentData aspspConsentData) {
        String login = spiPsuData.getPsuId();
        logger.info("Authorise user with login={} and password={}", login, StringUtils.repeat("*", pin.length()));
        boolean isAuthorised = ledgersRestClient.authorise(login, pin);
        SpiAuthorisationStatus status = isAuthorised ? SpiAuthorisationStatus.SUCCESS : SpiAuthorisationStatus.FAILURE;
        logger.info("Authorisation result is {}", status);
        return new SpiResponse<>(status, aspspConsentData);
    }

    @Override
    public SpiResponse<List<SpiAuthenticationObject>> requestAvailableScaMethods(@NotNull SpiPsuData spiPsuData, SpiAccountConsent spiAccountConsent, AspspConsentData aspspConsentData) {
        return SpiResponse.<List<SpiAuthenticationObject>>builder().payload(Collections.emptyList()).success();
    }

    @Override
    public @NotNull SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(@NotNull SpiPsuData spiPsuData, @NotNull String s, @NotNull SpiAccountConsent spiAccountConsent, @NotNull AspspConsentData aspspConsentData) {
        return SpiResponse.<SpiAuthorizationCodeResult>builder().payload(new SpiAuthorizationCodeResult()).success();
    }
}
