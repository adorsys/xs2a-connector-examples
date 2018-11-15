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
import de.adorsys.ledgers.domain.PaymentProduct;
import de.adorsys.ledgers.domain.PaymentType;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiTransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.SinglePaymentSpi;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class SinglePaymentSpiImpl implements SinglePaymentSpi {
    private static final Logger logger = LoggerFactory.getLogger(SinglePaymentSpiImpl.class);

    private final LedgersRestClient ledgersRestClient;

    public SinglePaymentSpiImpl(LedgersRestClient ledgersRestClient) {
        this.ledgersRestClient = ledgersRestClient;
    }

    @Override
    public @NotNull SpiResponse<SpiSinglePaymentInitiationResponse> initiatePayment(@NotNull SpiPsuData psuData, @NotNull SpiSinglePayment payment, @NotNull AspspConsentData initialAspspConsentData) {
        String paymentTypeName = payment.getPaymentType().name();
        PaymentType paymentType = PaymentType.valueOf(paymentTypeName);
        logger.info("Initiate single payment with type={}", paymentTypeName);
        logger.debug("Single payment body={}", payment);

        ResponseEntity<?> response = ledgersRestClient.initiatePayment(paymentType, payment);
        logger.debug("Response from Ledgers = {}", response.getBody());

        return SpiResponse.<SpiSinglePaymentInitiationResponse>builder()
                       .aspspConsentData(initialAspspConsentData)
                       .payload(buildPaymentInitializationResponse(payment.getPaymentId()))
                       .success();

    }

    @NotNull
    private SpiSinglePaymentInitiationResponse buildPaymentInitializationResponse(String paymentId) {
        SpiSinglePaymentInitiationResponse response = new SpiSinglePaymentInitiationResponse();
        response.setPaymentId(paymentId);
        response.setTransactionStatus(SpiTransactionStatus.RCVD);
        return response;
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> executePaymentWithoutSca(@NotNull SpiPsuData spiPsuData, @NotNull SpiSinglePayment payment, @NotNull AspspConsentData aspspConsentData) {
        String paymentId = payment.getPaymentId();
        String paymentProductName = payment.getPaymentProduct().name();
        String paymentTypeName = payment.getPaymentType().name();

        logger.info("Executing single payment without SCA for paymentId={}, productName={} and paymentType={}", paymentId, paymentProductName, paymentTypeName);
        logger.debug("Single payment body={}", payment);

        PaymentProduct paymentProduct = PaymentProduct.valueOf(paymentProductName);
        PaymentType paymentType = PaymentType.valueOf(paymentTypeName);

        ledgersRestClient.executePaymentNoSca(paymentId, paymentProduct, paymentType);

        return SpiResponse.<SpiResponse.VoidResponse>builder().aspspConsentData(aspspConsentData).success();
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> verifyScaAuthorisationAndExecutePayment(@NotNull SpiPsuData spiPsuData, @NotNull SpiScaConfirmation spiScaConfirmation, @NotNull SpiSinglePayment spiSinglePayment, @NotNull AspspConsentData aspspConsentData) {
        return null;
    }
}
