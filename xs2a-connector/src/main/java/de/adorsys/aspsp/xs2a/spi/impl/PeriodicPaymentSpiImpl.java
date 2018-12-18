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

import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.adorsys.aspsp.xs2a.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.ledgers.LedgersRestClient;
import de.adorsys.ledgers.domain.PaymentType;
import de.adorsys.ledgers.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.domain.payment.PeriodicPaymentTO;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiTransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPeriodicPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.PeriodicPaymentSpi;
import feign.FeignException;
import feign.Response;

@Component
public class PeriodicPaymentSpiImpl implements PeriodicPaymentSpi {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SinglePaymentSpiImpl.class);

    private final LedgersRestClient ledgersRestClient;
    private final LedgersSpiPaymentMapper paymentMapper;
    private final GeneralPaymentService paymentService;

    public PeriodicPaymentSpiImpl(LedgersRestClient ledgersRestClient, LedgersSpiPaymentMapper paymentMapper, GeneralPaymentService paymentService) {
        this.ledgersRestClient = ledgersRestClient;
        this.paymentMapper = paymentMapper;
        this.paymentService = paymentService;
    }

    @Override
    public @NotNull SpiResponse<SpiPeriodicPaymentInitiationResponse> initiatePayment(@NotNull SpiPsuData psuData, @NotNull SpiPeriodicPayment payment, @NotNull AspspConsentData initialAspspConsentData) {
        try {
            logger.info("Initiate periodic payment with type={}", PaymentTypeTO.PERIODIC);
            logger.debug("Periodic payment body={}", payment);
            PeriodicPaymentTO request = paymentMapper.toPeriodicPaymentTO(payment);
            PeriodicPaymentTO response = ledgersRestClient.initiatePeriodicPayment(TokenUtils.read(initialAspspConsentData),PaymentType.PERIODIC, request).getBody();
            SpiPeriodicPaymentInitiationResponse spiInitiationResponse = Optional.ofNullable(response)
                                                                                 .map(paymentMapper::toSpiPeriodicResponse)
                                                                                 .orElseThrow(() -> FeignException.errorStatus("Request failed, Response was 201, but body was empty!", Response.builder().status(400).build()));
            return SpiResponse.<SpiPeriodicPaymentInitiationResponse>builder()
                           .aspspConsentData(initialAspspConsentData)
                           .payload(spiInitiationResponse)
                           .success();

        } catch (FeignException e) {
            return SpiResponse.<SpiPeriodicPaymentInitiationResponse>builder()
                           .aspspConsentData(initialAspspConsentData.respondWith(initialAspspConsentData.getAspspConsentData()))
                           .fail(getSpiFailureResponse(e));
        }
    }

    @Override
    public @NotNull SpiResponse<SpiPeriodicPayment> getPaymentById(@NotNull SpiPsuData psuData, @NotNull SpiPeriodicPayment payment, @NotNull AspspConsentData aspspConsentData) {
        try {
            logger.info("Get payment by id with type={}, and id={}", PaymentTypeTO.PERIODIC, payment.getPaymentId());
            logger.debug("Periodic payment body={}", payment);
            PeriodicPaymentTO response = ledgersRestClient.getPeriodicPaymentPaymentById(TokenUtils.read(aspspConsentData),PaymentTypeTO.PERIODIC, PaymentProductTO.valueOf(payment.getPaymentProduct()), payment.getPaymentId()).getBody();
            SpiPeriodicPayment spiPeriodicPayment = Optional.ofNullable(response)
                                                            .map(paymentMapper::mapToSpiPeriodicPayment)
                                                            .orElseThrow(() -> FeignException.errorStatus("Request failed, Response was 200, but body was empty!", Response.builder().status(400).build()));
            return SpiResponse.<SpiPeriodicPayment>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .payload(spiPeriodicPayment)
                           .success();

        } catch (FeignException e) {
            return SpiResponse.<SpiPeriodicPayment>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .fail(getSpiFailureResponse(e));
        }
    }

    @Override
    public @NotNull SpiResponse<SpiTransactionStatus> getPaymentStatusById(@NotNull SpiPsuData psuData, @NotNull SpiPeriodicPayment payment, @NotNull AspspConsentData aspspConsentData) {
        return paymentService.getPaymentStatusById(PaymentTypeTO.valueOf(payment.getPaymentType().name()), payment.getPaymentId(), aspspConsentData);
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> executePaymentWithoutSca(@NotNull SpiPsuData psuData, @NotNull SpiPeriodicPayment payment, @NotNull AspspConsentData aspspConsentData) {
        return paymentService.executePaymentWithoutSca(payment.getPaymentId(), PaymentProductTO.valueOf(payment.getPaymentProduct()), PaymentTypeTO.PERIODIC, aspspConsentData);
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> verifyScaAuthorisationAndExecutePayment(@NotNull SpiPsuData psuData, @NotNull SpiScaConfirmation spiScaConfirmation, @NotNull SpiPeriodicPayment payment, @NotNull AspspConsentData aspspConsentData) {
        return paymentService.verifyScaAuthorisationAndExecutePayment(
                payment.getPaymentId(),
                PaymentProductTO.valueOf(payment.getPaymentProduct()),
                PaymentTypeTO.PERIODIC,
                payment.toString(),
                spiScaConfirmation,
                aspspConsentData
        );
    }

    @NotNull
    private SpiResponseStatus getSpiFailureResponse(FeignException e) {
        logger.error(e.getMessage(), e);
        return e.status() == 500
                       ? SpiResponseStatus.TECHNICAL_FAILURE
                       : SpiResponseStatus.LOGICAL_FAILURE;
    }
}
