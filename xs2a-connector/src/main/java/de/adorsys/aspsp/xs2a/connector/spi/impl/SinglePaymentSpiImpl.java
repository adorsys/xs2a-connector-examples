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

import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.payment.SinglePaymentTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiTransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.SinglePaymentSpi;
import feign.FeignException;
import feign.Response;

@Component
@SuppressWarnings("PMD.TooManyMethods")
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
        super();
        this.ledgersRestClient = ledgersRestClient;
        this.paymentMapper = paymentMapper;
        this.paymentService = paymentService;
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
        this.objectMapper = objectMapper;
    }

    /*
     * Initiating a payment you need a valid bearer token if not we just return ok.
     *
     * TODO: discuss access with xs2a team: we are receiving a call without authentication of
     * the user. So we take no action here.
     */
    @Override
    public @NotNull SpiResponse<SpiSinglePaymentInitiationResponse> initiatePayment(@NotNull SpiContextData contextData, @NotNull SpiSinglePayment payment, @NotNull AspspConsentData initialAspspConsentData) {
        if (initialAspspConsentData.getAspspConsentData() == null) {
            return paymentService.firstCallInstantiatingPayment(PaymentTypeTO.SINGLE, payment, initialAspspConsentData, new SpiSinglePaymentInitiationResponse());
        }
        try {
            SCAPaymentResponseTO response = initiatePaymentInternal(payment, initialAspspConsentData);
            SpiSinglePaymentInitiationResponse spiInitiationResponse = Optional.ofNullable(response)
                                                                               .map(paymentMapper::toSpiSingleResponse)
                                                                               .orElseThrow(() -> FeignException.errorStatus("Request failed, Response was 201, but body was empty!", Response.builder().status(400).build()));
            return SpiResponse.<SpiSinglePaymentInitiationResponse>builder()
                           .aspspConsentData(consentDataService.store(response, initialAspspConsentData))
                           .message(response.getScaStatus().name())
                           .payload(spiInitiationResponse)
                           .success();
        } catch (FeignException e) {
            return SpiResponse.<SpiSinglePaymentInitiationResponse>builder()
                           .aspspConsentData(initialAspspConsentData.respondWith(initialAspspConsentData.getAspspConsentData()))
                           .fail(getSpiFailureResponse(e));
        } catch (IllegalStateException e) {
            return SpiResponse.<SpiSinglePaymentInitiationResponse>builder()
                           .aspspConsentData(initialAspspConsentData.respondWith(initialAspspConsentData.getAspspConsentData()))
                           .fail(SpiResponseStatus.TECHNICAL_FAILURE);
        }
    }

    @Override
    public @NotNull SpiResponse<SpiSinglePayment> getPaymentById(@NotNull SpiContextData contextData, @NotNull SpiSinglePayment payment, @NotNull AspspConsentData aspspConsentData) {
        try {
            SCAPaymentResponseTO sca = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            logger.info("Get payment by id with type={}, and id={}", PaymentTypeTO.SINGLE, payment.getPaymentId());
            logger.debug("Single payment body={}", payment);
            // Normally the paymentId contained here must match the payment id
            // String paymentId = sca.getPaymentId(); This could also be used.
            // TODO: store payment type in sca.
            SinglePaymentTO response = objectMapper.convertValue(ledgersRestClient.getPaymentById(sca.getPaymentId()).getBody(), SinglePaymentTO.class);
            SpiSinglePayment spiPayment = Optional.ofNullable(response)
                                                  .map(paymentMapper::toSpiSinglePayment)
                                                  .orElseThrow(() -> FeignException.errorStatus("Request failed, Response was 200, but body was empty!", Response.builder().status(400).build()));
            return SpiResponse.<SpiSinglePayment>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .payload(spiPayment)
                           .success();
        } catch (FeignException e) {
            return SpiResponse.<SpiSinglePayment>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .fail(getSpiFailureResponse(e));
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    @Override
    public @NotNull SpiResponse<SpiTransactionStatus> getPaymentStatusById(@NotNull SpiContextData contextData, @NotNull SpiSinglePayment payment, @NotNull AspspConsentData aspspConsentData) {
        return paymentService.getPaymentStatusById(PaymentTypeTO.valueOf(payment.getPaymentType().name()), payment.getPaymentId(), aspspConsentData);
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
    public @NotNull SpiResponse<SpiPaymentExecutionResponse> executePaymentWithoutSca(@NotNull SpiContextData contextData, @NotNull SpiSinglePayment payment, @NotNull AspspConsentData aspspConsentData) {
        return paymentService.executePaymentWithoutSca(contextData, payment, aspspConsentData);
    }

    @Override
    public @NotNull SpiResponse<SpiPaymentExecutionResponse> verifyScaAuthorisationAndExecutePayment(@NotNull SpiContextData contextData, @NotNull SpiScaConfirmation spiScaConfirmation, @NotNull SpiSinglePayment payment, @NotNull AspspConsentData aspspConsentData) {
        return paymentService.verifyScaAuthorisationAndExecutePayment(
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

    private SCAPaymentResponseTO initiatePaymentInternal(SpiSinglePayment payment,
                                                         AspspConsentData initialAspspConsentData) throws FeignException {
        try {
            SCAResponseTO sca = consentDataService.response(initialAspspConsentData);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            logger.info("Initiate single payment with type={}", PaymentTypeTO.SINGLE);
            logger.debug("Single payment body={}", payment);
            SinglePaymentTO request = paymentMapper.toSinglePaymentTO(payment);
            // If the payment product is missing, get it from the sca object.
            if (request.getPaymentProduct() == null) {
                if (sca instanceof SCAPaymentResponseTO) {
                    SCAPaymentResponseTO scaPaymentResponse = (SCAPaymentResponseTO) sca;
                    request.setPaymentProduct(scaPaymentResponse.getPaymentProduct());
                } else {
                    throw new IllegalStateException(String.format("Missing payment product for payment with id %s ", payment.getPaymentId()));
                }
            }
            return ledgersRestClient.initiatePayment(PaymentTypeTO.SINGLE, request).getBody();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }


}
