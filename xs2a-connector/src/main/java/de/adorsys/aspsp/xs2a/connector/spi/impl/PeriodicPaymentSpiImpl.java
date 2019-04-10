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

import com.fasterxml.jackson.databind.ObjectMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PeriodicPaymentTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPeriodicPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.PeriodicPaymentSpi;
import feign.FeignException;
import feign.Response;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PeriodicPaymentSpiImpl implements PeriodicPaymentSpi {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(PeriodicPaymentSpiImpl.class);

    private final PaymentRestClient ledgersRestClient;
    private final LedgersSpiPaymentMapper paymentMapper;
    private final GeneralPaymentService paymentService;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final ObjectMapper objectMapper;

    public PeriodicPaymentSpiImpl(PaymentRestClient ledgersRestClient, LedgersSpiPaymentMapper paymentMapper,
                                  GeneralPaymentService paymentService, AuthRequestInterceptor authRequestInterceptor,
                                  AspspConsentDataService consentDataService, ObjectMapper objectMapper) {
        this.ledgersRestClient = ledgersRestClient;
        this.paymentMapper = paymentMapper;
        this.paymentService = paymentService;
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
        this.objectMapper = objectMapper;
    }

    @Override
    public @NotNull SpiResponse<SpiPeriodicPaymentInitiationResponse> initiatePayment(@NotNull SpiContextData contextData, @NotNull SpiPeriodicPayment payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        byte[] initialAspspConsentData = aspspConsentDataProvider.loadAspspConsentData();
        if (ArrayUtils.isEmpty(initialAspspConsentData)) {
            return paymentService.firstCallInstantiatingPayment(PaymentTypeTO.PERIODIC, payment, aspspConsentDataProvider, new SpiPeriodicPaymentInitiationResponse());
        }
        try {
            SCAPaymentResponseTO response = initiatePaymentInternal(payment, initialAspspConsentData);
            SpiPeriodicPaymentInitiationResponse spiInitiationResponse = Optional.ofNullable(response)
                                                                                 .map(paymentMapper::toSpiPeriodicResponse)
                                                                                 .orElseThrow(() -> FeignException.errorStatus("Request failed, Response was 201, but body was empty!", Response.builder().status(400).build()));
            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(response));
            return SpiResponse.<SpiPeriodicPaymentInitiationResponse>builder()
                           .message(response.getScaStatus().name())
                           .payload(spiInitiationResponse)
                           .success();
        } catch (FeignException e) {
            return SpiResponse.<SpiPeriodicPaymentInitiationResponse>builder()
                           .fail(getSpiFailureResponse(e));
        }
    }

    @Override
    public @NotNull SpiResponse<SpiPeriodicPayment> getPaymentById(@NotNull SpiContextData contextData, @NotNull SpiPeriodicPayment payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        if (!TransactionStatus.ACSP.equals(payment.getPaymentStatus())) {
            return SpiResponse.<SpiPeriodicPayment>builder()
                           .payload(payment)
                           .success();
        }
        return paymentService.getPaymentById(payment.getPaymentId(), payment.toString(), aspspConsentDataProvider.loadAspspConsentData())
                       .map(p -> objectMapper.convertValue(p, PeriodicPaymentTO.class))
                       .map(paymentMapper::mapToSpiPeriodicPayment)
                       .map(p -> SpiResponse.<SpiPeriodicPayment>builder()
                                         .payload(p)
                                         .success())
                       .orElseGet(() -> SpiResponse.<SpiPeriodicPayment>builder()
                                                .fail(SpiResponseStatus.LOGICAL_FAILURE));
    }

    @Override
    public @NotNull SpiResponse<TransactionStatus> getPaymentStatusById(@NotNull SpiContextData contextData, @NotNull SpiPeriodicPayment payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return paymentService.getPaymentStatusById(PaymentTypeTO.valueOf(payment.getPaymentType().name()), payment.getPaymentId(), payment.getPaymentStatus(), aspspConsentDataProvider.loadAspspConsentData());
    }

    @Override
    public @NotNull SpiResponse<SpiPaymentExecutionResponse> executePaymentWithoutSca(@NotNull SpiContextData contextData, @NotNull SpiPeriodicPayment payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return paymentService.executePaymentWithoutSca(aspspConsentDataProvider);
    }

    @Override
    public @NotNull SpiResponse<SpiPaymentExecutionResponse> verifyScaAuthorisationAndExecutePayment(@NotNull SpiContextData contextData, @NotNull SpiScaConfirmation spiScaConfirmation, @NotNull SpiPeriodicPayment payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return paymentService.verifyScaAuthorisationAndExecutePayment(spiScaConfirmation, aspspConsentDataProvider);
    }

    @NotNull
    private SpiResponseStatus getSpiFailureResponse(FeignException e) {
        logger.error(e.getMessage(), e);
        return e.status() == 500
                       ? SpiResponseStatus.TECHNICAL_FAILURE
                       : SpiResponseStatus.LOGICAL_FAILURE;
    }

    private SCAPaymentResponseTO initiatePaymentInternal(SpiPeriodicPayment payment, byte[] initialAspspConsentData) {
        try {
            SCAPaymentResponseTO sca = consentDataService.response(initialAspspConsentData, SCAPaymentResponseTO.class, true);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            logger.info("Initiate periodic payment with type={}", PaymentTypeTO.PERIODIC);
            logger.debug("Periodic payment body={}", payment);
            PeriodicPaymentTO request = paymentMapper.toPeriodicPaymentTO(payment);
            // If the payment product is missing, get it from the sca object.
            if (request.getPaymentProduct() == null) {
                request.setPaymentProduct(sca.getPaymentProduct());
            }
            return ledgersRestClient.initiatePayment(PaymentTypeTO.PERIODIC, request).getBody();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }
}
