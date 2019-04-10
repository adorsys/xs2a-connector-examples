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
import de.adorsys.ledgers.middleware.api.domain.payment.SinglePaymentTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.SinglePaymentSpi;
import feign.FeignException;
import feign.Response;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SinglePaymentSpiImpl implements SinglePaymentSpi {
    private static final Logger logger = LoggerFactory.getLogger(SinglePaymentSpiImpl.class);

    private final PaymentRestClient ledgersRestClient;
    private final LedgersSpiPaymentMapper paymentMapper;
    private final GeneralPaymentService paymentService;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final ObjectMapper objectMapper;

    public SinglePaymentSpiImpl(PaymentRestClient ledgersRestClient, LedgersSpiPaymentMapper paymentMapper,
                                GeneralPaymentService paymentService, AuthRequestInterceptor authRequestInterceptor,
                                AspspConsentDataService consentDataService, ObjectMapper objectMapper) {
        this.ledgersRestClient = ledgersRestClient;
        this.paymentMapper = paymentMapper;
        this.paymentService = paymentService;
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
        this.objectMapper = objectMapper;
    }

    /*
     * Initiating a payment you need a valid bearer token if not we just return ok.
     */
    @Override
    public @NotNull SpiResponse<SpiSinglePaymentInitiationResponse> initiatePayment(@NotNull SpiContextData contextData, @NotNull SpiSinglePayment payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        byte[] initialAspspConsentData = aspspConsentDataProvider.loadAspspConsentData();
        if (ArrayUtils.isEmpty(initialAspspConsentData)) {
            return paymentService.firstCallInstantiatingPayment(PaymentTypeTO.SINGLE, payment, aspspConsentDataProvider, new SpiSinglePaymentInitiationResponse());
        }
        try {
            SCAPaymentResponseTO response = initiatePaymentInternal(payment, initialAspspConsentData);
            SpiSinglePaymentInitiationResponse spiInitiationResponse = Optional.ofNullable(response)
                                                                               .map(paymentMapper::toSpiSingleResponse)
                                                                               .orElseThrow(() -> FeignException.errorStatus("Request failed, Response was 201, but body was empty!", Response.builder().status(400).build()));
            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(response));
            return SpiResponse.<SpiSinglePaymentInitiationResponse>builder()
                           .message(response.getScaStatus().name())
                           .payload(spiInitiationResponse)
                           .success();
        } catch (FeignException e) {
            return SpiResponse.<SpiSinglePaymentInitiationResponse>builder()
                           .fail(getSpiFailureResponse(e));
        } catch (IllegalStateException e) {
            return SpiResponse.<SpiSinglePaymentInitiationResponse>builder()
                           .fail(SpiResponseStatus.TECHNICAL_FAILURE);
        }
    }

    @Override
    public @NotNull SpiResponse<SpiSinglePayment> getPaymentById(@NotNull SpiContextData contextData, @NotNull SpiSinglePayment payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        if (!TransactionStatus.ACSP.equals(payment.getPaymentStatus())) {
            return SpiResponse.<SpiSinglePayment>builder()
                           .payload(payment)
                           .success();
        }
        return paymentService.getPaymentById(payment.getPaymentId(), payment.toString(), aspspConsentDataProvider.loadAspspConsentData())
                       .map(p -> objectMapper.convertValue(p, SinglePaymentTO.class))
                       .map(paymentMapper::toSpiSinglePayment)
                       .map(p -> SpiResponse.<SpiSinglePayment>builder()
                                         .payload(p)
                                         .success())
                       .orElseGet(() -> SpiResponse.<SpiSinglePayment>builder()
                                                .fail(SpiResponseStatus.LOGICAL_FAILURE));
    }

    @Override
    public @NotNull SpiResponse<TransactionStatus> getPaymentStatusById(@NotNull SpiContextData contextData, @NotNull SpiSinglePayment payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return paymentService.getPaymentStatusById(PaymentTypeTO.valueOf(payment.getPaymentType().name()), payment.getPaymentId(), payment.getPaymentStatus(), aspspConsentDataProvider.loadAspspConsentData());
    }

    /*
     * This attempt to execute payment without sca can only work if the core banking system decides that there is no
     * sca required. If this is the case, the payment would have been executed in the initiation phase after
     * the first login of the user.
     *
     * In sure, the core banking considers it like any other payment initiation. If the status is ScsStatus.EXEMPTED
     * then we are fine. If not the user will be required to proceed with sca.
     *
     */
    @Override
    public @NotNull SpiResponse<SpiPaymentExecutionResponse> executePaymentWithoutSca(@NotNull SpiContextData contextData, @NotNull SpiSinglePayment payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return paymentService.executePaymentWithoutSca(aspspConsentDataProvider);
    }

    @Override
    public @NotNull SpiResponse<SpiPaymentExecutionResponse> verifyScaAuthorisationAndExecutePayment(@NotNull SpiContextData contextData, @NotNull SpiScaConfirmation spiScaConfirmation, @NotNull SpiSinglePayment payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return paymentService.verifyScaAuthorisationAndExecutePayment(spiScaConfirmation, aspspConsentDataProvider);
    }

    @NotNull
    private SpiResponseStatus getSpiFailureResponse(FeignException e) {
        logger.error(e.getMessage(), e);
        return e.status() == 500
                       ? SpiResponseStatus.TECHNICAL_FAILURE
                       : SpiResponseStatus.LOGICAL_FAILURE;
    }

    private SCAPaymentResponseTO initiatePaymentInternal(SpiSinglePayment payment, byte[] initialAspspConsentData) {
        try {
            SCAPaymentResponseTO sca = consentDataService.response(initialAspspConsentData, SCAPaymentResponseTO.class, true);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            logger.info("Initiate single payment with type={}", PaymentTypeTO.SINGLE);
            logger.debug("Single payment body={}", payment);
            SinglePaymentTO request = paymentMapper.toSinglePaymentTO(payment);
            // If the payment product is missing, get it from the sca object.
            if (request.getPaymentProduct() == null) {
                request.setPaymentProduct(sca.getPaymentProduct());
            }
            return ledgersRestClient.initiatePayment(PaymentTypeTO.SINGLE, request).getBody();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }
}
