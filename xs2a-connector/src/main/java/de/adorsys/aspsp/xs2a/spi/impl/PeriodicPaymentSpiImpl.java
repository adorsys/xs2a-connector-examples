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
import de.adorsys.ledgers.domain.PaymentType;
import de.adorsys.ledgers.domain.SCAValidationRequest;
import de.adorsys.ledgers.domain.TransactionStatus;
import de.adorsys.ledgers.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.domain.payment.PeriodicPaymentTO;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.domain.MessageErrorCode;
import de.adorsys.psd2.xs2a.exception.RestException;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiTransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPeriodicPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.PeriodicPaymentSpi;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PeriodicPaymentSpiImpl implements PeriodicPaymentSpi {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SinglePaymentSpiImpl.class);

    private final LedgersRestClient ledgersRestClient;
    private final LedgersSpiPaymentMapper paymentMapper;

    public PeriodicPaymentSpiImpl(LedgersRestClient ledgersRestClient, LedgersSpiPaymentMapper paymentMapper) {
        this.ledgersRestClient = ledgersRestClient;
        this.paymentMapper = paymentMapper;
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> executePaymentWithoutSca(@NotNull SpiPsuData psuData, @NotNull SpiPeriodicPayment payment, @NotNull AspspConsentData aspspConsentData) {
        logger.info("Executing periodic payment without SCA for paymentId={}, productName={} and paymentType={}", payment.getPaymentId(), payment.getPaymentProduct(), payment.getPaymentType());
        logger.debug("Periodic payment body={}", payment);
        try {
            TransactionStatus status = ledgersRestClient.executePaymentNoSca(payment.getPaymentId(),
                    PaymentProductTO.valueOf(payment.getPaymentProduct().name()),
                    PaymentTypeTO.PERIODIC).getBody();
            Optional.ofNullable(status)
                    .orElseThrow(() -> new RestException(MessageErrorCode.PAYMENT_FAILED));
            logger.info("The response status was:{}", status);
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .success();
        } catch (RestException e) {
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .fail(getSpiFailureResponse(e));
        }
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> verifyScaAuthorisationAndExecutePayment(@NotNull SpiPsuData psuData, @NotNull SpiScaConfirmation spiScaConfirmation, @NotNull SpiPeriodicPayment payment, @NotNull AspspConsentData aspspConsentData) {
        logger.info("Verifying SCA code");
        try {
            SCAValidationRequest validationRequest = new SCAValidationRequest(payment.toString(), spiScaConfirmation.getTanNumber());//TODO fix this! it is not correct!
            boolean isValid = ledgersRestClient.validate(spiScaConfirmation.getPaymentId(), validationRequest);
            logger.info("Validation result is {}", isValid);
            if (isValid) {
                return SpiResponse.<SpiResponse.VoidResponse>builder()
                               .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                               .success();
            }
            throw new RestException(MessageErrorCode.PAYMENT_FAILED);
        } catch (RestException e) {
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .fail(getSpiFailureResponse(e));
        }
    }

    @Override
    public @NotNull SpiResponse<SpiPeriodicPaymentInitiationResponse> initiatePayment(@NotNull SpiPsuData psuData, @NotNull SpiPeriodicPayment payment, @NotNull AspspConsentData initialAspspConsentData) {
        try {
            logger.info("Initiate periodic payment with type={}", PaymentTypeTO.PERIODIC);
            logger.debug("Periodic payment body={}", payment);
            PeriodicPaymentTO request = paymentMapper.toPeriodicPaymentTO(payment);
            PeriodicPaymentTO response = ledgersRestClient.initiatePeriodicPayment(PaymentType.PERIODIC, request).getBody();
            SpiPeriodicPaymentInitiationResponse spiInitiationResponse = Optional.ofNullable(response)
                                                                                 .map(paymentMapper::toSpiPeriodicResponse)
                                                                                 .orElseThrow(() -> new RestException(MessageErrorCode.FORMAT_ERROR));
            return SpiResponse.<SpiPeriodicPaymentInitiationResponse>builder()
                           .aspspConsentData(initialAspspConsentData)
                           .payload(spiInitiationResponse)
                           .success();

        } catch (RestException e) {
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
            PeriodicPaymentTO response = ledgersRestClient.getPeriodicPaymentPaymentById(PaymentTypeTO.PERIODIC, PaymentProductTO.valueOf(payment.getPaymentProduct().name()), payment.getPaymentId()).getBody();
            SpiPeriodicPayment spiPeriodicPayment = Optional.ofNullable(response)
                                                            .map(paymentMapper::mapToSpiPeriodicPayment)
                                                            .orElseThrow(() -> new RestException(MessageErrorCode.FORMAT_ERROR));
            return SpiResponse.<SpiPeriodicPayment>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .payload(spiPeriodicPayment)
                           .success();

        } catch (RestException e) {
            return SpiResponse.<SpiPeriodicPayment>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .fail(getSpiFailureResponse(e));
        }
    }

    @Override
    public @NotNull SpiResponse<SpiTransactionStatus> getPaymentStatusById(@NotNull SpiPsuData psuData, @NotNull SpiPeriodicPayment payment, @NotNull AspspConsentData aspspConsentData) {
        try {
            logger.info("Get payment status by id with type={}, and id={}", PaymentTypeTO.PERIODIC, payment.getPaymentId());
            logger.debug("Periodic payment body={}", payment);
            TransactionStatus response = ledgersRestClient.getPaymentStatusById(payment.getPaymentId()).getBody();
            SpiTransactionStatus status = Optional.ofNullable(response)
                                                  .map(r -> SpiTransactionStatus.valueOf(r.getName()))
                                                  .orElseThrow(() -> new RestException(MessageErrorCode.FORMAT_ERROR));
            logger.info("The status was:{}", status);
            return SpiResponse.<SpiTransactionStatus>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .payload(status)
                           .success();
        } catch (RestException e) {
            return SpiResponse.<SpiTransactionStatus>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .fail(getSpiFailureResponse(e));
        }
    }

    @NotNull
    private SpiResponseStatus getSpiFailureResponse(RestException e) {
        logger.error(e.getMessage(), e);
        return (e.getHttpStatus() == HttpStatus.INTERNAL_SERVER_ERROR)
                       ? SpiResponseStatus.TECHNICAL_FAILURE
                       : SpiResponseStatus.LOGICAL_FAILURE;
    }
}
