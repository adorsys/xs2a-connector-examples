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

import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.AisConsentSpi;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class AisConsentSpiImpl implements AisConsentSpi {
    private static final String TEST_ASPSP_DATA = "Test aspsp data";
    private static final String TEST_MESSAGE = "Test message";
    private final GeneralAuthorisationService authorisationService;

    public AisConsentSpiImpl(GeneralAuthorisationService authorisationService) {
        this.authorisationService = authorisationService;
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
        String opData = buildOpData(spiAccountConsent);
        return authorisationService.verifyScaAuthorisation(spiScaConfirmation, opData, aspspConsentData);
    }

    @Override
    public SpiResponse<SpiAuthorisationStatus> authorisePsu(@NotNull SpiPsuData spiPsuData, String pin, SpiAccountConsent spiAccountConsent, AspspConsentData aspspConsentData) {
        return authorisationService.authorisePsu(spiPsuData, pin, aspspConsentData);
    }

    @Override
    public SpiResponse<List<SpiAuthenticationObject>> requestAvailableScaMethods(@NotNull SpiPsuData spiPsuData, SpiAccountConsent spiAccountConsent, AspspConsentData aspspConsentData) {
        return authorisationService.requestAvailableScaMethods(spiPsuData, aspspConsentData);
    }

    @Override
    public @NotNull SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(@NotNull SpiPsuData spiPsuData, @NotNull String authenticationMethodId, @NotNull SpiAccountConsent spiAccountConsent, @NotNull AspspConsentData aspspConsentData) {
        String opData = buildOpData(spiAccountConsent);
        return authorisationService.requestAuthorisationCode(spiPsuData, authenticationMethodId, spiAccountConsent.getId(), opData, aspspConsentData);
    }

    private String buildOpData(SpiAccountConsent spiAccountConsent) {
        OpData opData = OpData.of(spiAccountConsent);
        return opData.toString();
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
