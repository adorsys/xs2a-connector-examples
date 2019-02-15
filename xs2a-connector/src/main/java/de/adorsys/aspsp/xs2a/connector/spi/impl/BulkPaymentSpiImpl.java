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

package de.adorsys.aspsp.xs2a.connector.spi.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.ledgers.middleware.api.domain.payment.BulkPaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiTransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiBulkPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiBulkPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.BulkPaymentSpi;
import feign.FeignException;
import feign.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;


@Component
public class BulkPaymentSpiImpl implements BulkPaymentSpi {
    private static final Logger logger = LoggerFactory.getLogger(BulkPaymentSpiImpl.class);

    private final PaymentRestClient ledgersRestClient;
    private final LedgersSpiPaymentMapper paymentMapper;
    private final GeneralPaymentService paymentService;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final ObjectMapper objectMapper;

    public BulkPaymentSpiImpl(PaymentRestClient ledgersRestClient, LedgersSpiPaymentMapper paymentMapper,
                              GeneralPaymentService paymentService, AuthRequestInterceptor authRequestInterceptor,
                              AspspConsentDataService consentDataService, ObjectMapper objectMapper) {
        this.ledgersRestClient = ledgersRestClient;
        this.paymentMapper = paymentMapper;
        this.paymentService = paymentService;
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
        this.objectMapper = objectMapper;
    }

    @NotNull
    @Override
    public SpiResponse<SpiBulkPaymentInitiationResponse> initiatePayment(@NotNull SpiContextData contextData, @NotNull SpiBulkPayment payment, @NotNull AspspConsentData initialAspspConsentData) {
        if (initialAspspConsentData.getAspspConsentData() == null) {
            return paymentService.firstCallInstantiatingPayment(PaymentTypeTO.BULK, payment, initialAspspConsentData, new SpiBulkPaymentInitiationResponse());
        }
        try {
            SCAPaymentResponseTO response = initiatePaymentInternal(payment, initialAspspConsentData);
            SpiBulkPaymentInitiationResponse spiInitiationResponse = Optional.ofNullable(response)
                                                                             .map(paymentMapper::toSpiBulkResponse)
                                                                             .orElseThrow(() -> FeignException.errorStatus("Request failed, Response was 201, but body was empty!", Response.builder().status(400).build()));
            return SpiResponse.<SpiBulkPaymentInitiationResponse>builder()
                           .aspspConsentData(consentDataService.store(response, initialAspspConsentData))
                           .message(response.getScaStatus().name())
                           .payload(spiInitiationResponse)
                           .success();
        } catch (FeignException e) {
            return SpiResponse.<SpiBulkPaymentInitiationResponse>builder()
                           .aspspConsentData(initialAspspConsentData.respondWith(initialAspspConsentData.getAspspConsentData()))
                           .fail(getSpiFailureResponse(e));
        }
    }

    @Override
    public @NotNull SpiResponse<SpiBulkPayment> getPaymentById(@NotNull SpiContextData contextData, @NotNull SpiBulkPayment payment, @NotNull AspspConsentData aspspConsentData) {
        if (!SpiTransactionStatus.ACSP.equals(payment.getPaymentStatus())) {
            return SpiResponse.<SpiBulkPayment>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .payload(payment)
                           .success();
        }
        return paymentService.getPaymentById(payment.getPaymentId(), payment.toString(), aspspConsentData)
                       .map(p -> objectMapper.convertValue(p, BulkPaymentTO.class))
                       .map(paymentMapper::mapToSpiBulkPayment)
                       .map(p -> SpiResponse.<SpiBulkPayment>builder()
                                         .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                                         .payload(p)
                                         .success())
                       .orElseGet(() -> SpiResponse.<SpiBulkPayment>builder()
                                                .fail(SpiResponseStatus.LOGICAL_FAILURE));
    }

    @Override
    public @NotNull SpiResponse<SpiTransactionStatus> getPaymentStatusById(@NotNull SpiContextData contextData, @NotNull SpiBulkPayment payment, @NotNull AspspConsentData aspspConsentData) {
        return paymentService.getPaymentStatusById(PaymentTypeTO.valueOf(payment.getPaymentType().name()), payment.getPaymentId(), payment.getPaymentStatus(), aspspConsentData);
    }

    @Override
    public @NotNull SpiResponse<SpiPaymentExecutionResponse> executePaymentWithoutSca(@NotNull SpiContextData contextData,
                                                                                      @NotNull SpiBulkPayment payment, @NotNull AspspConsentData aspspConsentData) {
        return paymentService.executePaymentWithoutSca(contextData, payment, aspspConsentData);
    }

    @Override
    public @NotNull SpiResponse<SpiPaymentExecutionResponse> verifyScaAuthorisationAndExecutePayment(
            @NotNull SpiContextData contextData, @NotNull SpiScaConfirmation spiScaConfirmation,
            @NotNull SpiBulkPayment payment, @NotNull AspspConsentData aspspConsentData) {
        return paymentService.verifyScaAuthorisationAndExecutePayment(spiScaConfirmation, aspspConsentData);
    }

    @NotNull
    private SpiResponseStatus getSpiFailureResponse(FeignException e) {
        logger.error(e.getMessage(), e);
        return e.status() == 500
                       ? SpiResponseStatus.TECHNICAL_FAILURE
                       : SpiResponseStatus.LOGICAL_FAILURE;
    }


    private SCAPaymentResponseTO initiatePaymentInternal(SpiBulkPayment payment, AspspConsentData initialAspspConsentData) {
        try {
            SCAPaymentResponseTO sca = consentDataService.response(initialAspspConsentData, SCAPaymentResponseTO.class, true);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            logger.info("Initiate bulk payment with type={}", PaymentTypeTO.BULK);
            logger.debug("Bulk payment body={}", payment);
            BulkPaymentTO request = paymentMapper.toBulkPaymentTO(payment);
            // If the payment product is missing, get it from the sca object.
            if (request.getPaymentProduct() == null) {
                request.setPaymentProduct(sca.getPaymentProduct());
            }
            return ledgersRestClient.initiatePayment(PaymentTypeTO.BULK, request).getBody();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }
}
