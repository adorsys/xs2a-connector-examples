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
import de.adorsys.ledgers.middleware.api.domain.payment.PeriodicPaymentTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiGetPaymentStatusResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPeriodicPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.PeriodicPaymentSpi;
import feign.FeignException;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PeriodicPaymentSpiImpl implements PeriodicPaymentSpi {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(PeriodicPaymentSpiImpl.class);

    private final LedgersSpiPaymentMapper paymentMapper;
    private final GeneralPaymentService paymentService;
    private final AspspConsentDataService consentDataService;
    private final FeignExceptionReader feignExceptionReader;

    public PeriodicPaymentSpiImpl(LedgersSpiPaymentMapper paymentMapper, GeneralPaymentService paymentService,
                                  AspspConsentDataService consentDataService, FeignExceptionReader feignExceptionReader) {
        this.paymentMapper = paymentMapper;
        this.paymentService = paymentService;
        this.consentDataService = consentDataService;
        this.feignExceptionReader = feignExceptionReader;
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
                                                                                 .orElseThrow(() -> FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "Request failed, Response was 201, but body was empty!"));
            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(response));

            String scaStatusName = response.getScaStatus().name();
            logger.info("SCA status is: {}", scaStatusName);

            return SpiResponse.<SpiPeriodicPaymentInitiationResponse>builder()
                           .payload(spiInitiationResponse)
                           .build();
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Initiate periodic payment failed: payment ID {}, devMessage {}", payment.getPaymentId(), devMessage);
            return SpiResponse.<SpiPeriodicPaymentInitiationResponse>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PAYMENT_FAILED, devMessage))
                           .build();
        } catch (IllegalStateException e) {
            return SpiResponse.<SpiPeriodicPaymentInitiationResponse>builder()
                           .error(new TppMessage(MessageErrorCode.PAYMENT_FAILED))
                           .build();
        }
    }

    @Override
    public @NotNull SpiResponse<SpiPeriodicPayment> getPaymentById(@NotNull SpiContextData contextData, @NotNull SpiPeriodicPayment payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return paymentService.getPaymentById(payment, aspspConsentDataProvider, PeriodicPaymentTO.class, paymentMapper::mapToSpiPeriodicPayment, PaymentTypeTO.PERIODIC);
    }

    @Override
    public @NotNull SpiResponse<SpiGetPaymentStatusResponse> getPaymentStatusById(@NotNull SpiContextData contextData, @NotNull SpiPeriodicPayment payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
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

    private SCAPaymentResponseTO initiatePaymentInternal(SpiPeriodicPayment payment, byte[] initialAspspConsentData) {
        PeriodicPaymentTO request = paymentMapper.toPeriodicPaymentTO(payment);
        if (request.getPaymentProduct() == null) {
            request.setPaymentProduct(paymentService.getSCAPaymentResponseTO(initialAspspConsentData).getPaymentProduct());
        }
        return paymentService.initiatePaymentInternal(payment, initialAspspConsentData, PaymentTypeTO.PERIODIC, request);
    }
}
