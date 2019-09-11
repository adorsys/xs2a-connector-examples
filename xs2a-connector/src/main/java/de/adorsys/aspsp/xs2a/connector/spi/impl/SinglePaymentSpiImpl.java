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

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.payment.SinglePaymentTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiGetPaymentStatusResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.SinglePaymentSpi;
import feign.FeignException;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SinglePaymentSpiImpl implements SinglePaymentSpi {
    private static final Logger logger = LoggerFactory.getLogger(SinglePaymentSpiImpl.class);

    private final LedgersSpiPaymentMapper paymentMapper;
    private final GeneralPaymentService paymentService;
    private final AspspConsentDataService consentDataService;
    private final FeignExceptionReader feignExceptionReader;

    public SinglePaymentSpiImpl(LedgersSpiPaymentMapper paymentMapper, GeneralPaymentService paymentService,
                                AspspConsentDataService consentDataService, FeignExceptionReader feignExceptionReader) {
        this.paymentMapper = paymentMapper;
        this.paymentService = paymentService;
        this.consentDataService = consentDataService;
        this.feignExceptionReader = feignExceptionReader;
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
                                                                               .orElseThrow(() -> FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "Request failed, Response was 201, but body was empty!"));
            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(response));


            String scaStatusName = response.getScaStatus().name();
            logger.info("SCA status is: {}", scaStatusName);

            return SpiResponse.<SpiSinglePaymentInitiationResponse>builder()
                           .payload(spiInitiationResponse)
                           .build();
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Initiate single payment failed: payment ID {}, devMessage {}", payment.getPaymentId(), devMessage);
            return SpiResponse.<SpiSinglePaymentInitiationResponse>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PAYMENT_FAILED, devMessage, "The payment initiation request failed during the initial process."))
                           .build();
        } catch (IllegalStateException e) {
            return SpiResponse.<SpiSinglePaymentInitiationResponse>builder()
                           .error(new TppMessage(MessageErrorCode.PAYMENT_FAILED, "The payment initiation request failed during the initial process."))
                           .build();
        }
    }

    @Override
    public @NotNull SpiResponse<SpiSinglePayment> getPaymentById(@NotNull SpiContextData contextData, @NotNull SpiSinglePayment payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return paymentService.getPaymentById(payment, aspspConsentDataProvider, SinglePaymentTO.class, paymentMapper::toSpiSinglePayment, PaymentTypeTO.SINGLE);
    }

    @Override
    public @NotNull SpiResponse<SpiGetPaymentStatusResponse> getPaymentStatusById(@NotNull SpiContextData contextData, @NotNull SpiSinglePayment payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
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


    private SCAPaymentResponseTO initiatePaymentInternal(SpiSinglePayment payment, byte[] initialAspspConsentData) {
        SinglePaymentTO request = paymentMapper.toSinglePaymentTO(payment);
        if (request.getPaymentProduct() == null) {
            request.setPaymentProduct(paymentService.getSCAPaymentResponseTO(initialAspspConsentData).getPaymentProduct());
        }
        return paymentService.initiatePaymentInternal(payment, initialAspspConsentData, PaymentTypeTO.SINGLE, request);
    }
}
