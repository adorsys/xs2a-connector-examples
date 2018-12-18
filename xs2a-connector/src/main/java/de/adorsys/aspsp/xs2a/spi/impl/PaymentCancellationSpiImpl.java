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

import de.adorsys.aspsp.xs2a.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.ledgers.LedgersRestClient;
import de.adorsys.ledgers.domain.payment.PaymentCancellationResponseTO;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentCancellationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.PaymentCancellationSpi;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import feign.FeignException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentCancellationSpiImpl implements PaymentCancellationSpi {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SinglePaymentSpiImpl.class);

    private final LedgersRestClient ledgersRestClient;
    private final LedgersSpiPaymentMapper paymentMapper;
    private final GeneralAuthorisationService authorisationService;

    public PaymentCancellationSpiImpl(LedgersRestClient ledgersRestClient, LedgersSpiPaymentMapper paymentMapper, GeneralAuthorisationService authorisationService) {
        this.ledgersRestClient = ledgersRestClient;
        this.paymentMapper = paymentMapper;
        this.authorisationService = authorisationService;
    }

    @Override
    public @NotNull SpiResponse<SpiPaymentCancellationResponse> initiatePaymentCancellation(@NotNull SpiPsuData psuData, @NotNull SpiPayment payment, @NotNull AspspConsentData aspspConsentData) {
        try {
            logger.info("Initiate payment cancellation payment:{}, userId:{}", payment.getPaymentId(), psuData.getPsuId());
            PaymentCancellationResponseTO aspspResponse = ledgersRestClient.initiatePmtCancellation(TokenUtils.read(aspspConsentData),psuData.getPsuId(), payment.getPaymentId()).getBody();
            SpiPaymentCancellationResponse response = paymentMapper.toSpiPaymentCancellationResponse(aspspResponse);
            logger.info("With response:{}", response.getTransactionStatus().name());
            return SpiResponse.<SpiPaymentCancellationResponse>builder()
                           .aspspConsentData(aspspConsentData)
                           .payload(response)
                           .success();
        } catch (FeignException e) {
            return SpiResponse.<SpiPaymentCancellationResponse>builder()
                           .aspspConsentData(aspspConsentData)
                           .fail(getSpiFailureResponse(e));
        }
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> cancelPaymentWithoutSca(@NotNull SpiPsuData psuData, @NotNull SpiPayment payment, @NotNull AspspConsentData aspspConsentData) {
        try {
            logger.info("Cancel payment:{}, userId:{}", payment.getPaymentId(), psuData.getPsuId());
            ledgersRestClient.cancelPaymentNoSca(TokenUtils.read(aspspConsentData),payment.getPaymentId());
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .aspspConsentData(aspspConsentData)
                           .payload(SpiResponse.voidResponse())
                           .success();
        } catch (FeignException e) {
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .aspspConsentData(aspspConsentData)
                           .fail(getSpiFailureResponse(e));
        }
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> verifyScaAuthorisationAndCancelPayment(@NotNull SpiPsuData psuData, @NotNull SpiScaConfirmation spiScaConfirmation, @NotNull SpiPayment payment, @NotNull AspspConsentData aspspConsentData) {
        SpiResponse<SpiResponse.VoidResponse> authResponse = authorisationService.verifyScaAuthorisation(spiScaConfirmation, payment.toString(), aspspConsentData);
        return authResponse.isSuccessful()
                       ? cancelPaymentWithoutSca(psuData, payment, aspspConsentData)
                       : authResponse;
    }

    @Override
    public SpiResponse<SpiAuthorisationStatus> authorisePsu(@NotNull SpiPsuData psuData, String password, SpiPayment businessObject, AspspConsentData aspspConsentData) {
        return authorisationService.authorisePsu(psuData, password, aspspConsentData);
    }

    @Override
    public SpiResponse<List<SpiAuthenticationObject>> requestAvailableScaMethods(@NotNull SpiPsuData psuData, SpiPayment businessObject, AspspConsentData aspspConsentData) {
        return authorisationService.requestAvailableScaMethods(psuData, aspspConsentData);
    }

    @Override
    public @NotNull SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(@NotNull SpiPsuData psuData, @NotNull String authenticationMethodId, @NotNull SpiPayment businessObject, @NotNull AspspConsentData aspspConsentData) {
        return authorisationService.requestAuthorisationCode(psuData, authenticationMethodId, businessObject.getPaymentId(), businessObject.toString(), aspspConsentData);
    }

    @NotNull
    private SpiResponseStatus getSpiFailureResponse(FeignException e) {
        logger.error(e.getMessage(), e);
        return e.status() == 500
                       ? SpiResponseStatus.TECHNICAL_FAILURE
                       : SpiResponseStatus.LOGICAL_FAILURE;
    }
}
