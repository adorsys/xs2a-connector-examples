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

import de.adorsys.aspsp.xs2a.spi.mappers.LedgersSpiPaymentMapper;
import de.adorsys.ledgers.LedgersRestClient;
import de.adorsys.ledgers.domain.PaymentProduct;
import de.adorsys.ledgers.domain.PaymentType;
import de.adorsys.ledgers.domain.SCAValidationRequest;
import de.adorsys.ledgers.domain.TransactionStatus;
import de.adorsys.ledgers.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.domain.payment.SinglePaymentTO;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.domain.MessageErrorCode;
import de.adorsys.psd2.xs2a.exception.RestException;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiTransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.SinglePaymentSpi;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SinglePaymentSpiImpl implements SinglePaymentSpi {
    private static final Logger logger = LoggerFactory.getLogger(SinglePaymentSpiImpl.class);

    private final LedgersRestClient ledgersRestClient;
    private final LedgersSpiPaymentMapper paymentMapper;

    public SinglePaymentSpiImpl(LedgersRestClient ledgersRestClient, LedgersSpiPaymentMapper paymentMapper) {
        this.ledgersRestClient = ledgersRestClient;
        this.paymentMapper = paymentMapper;
    }

    @Override
    public @NotNull SpiResponse<SpiSinglePaymentInitiationResponse> initiatePayment(@NotNull SpiPsuData psuData, @NotNull SpiSinglePayment payment, @NotNull AspspConsentData initialAspspConsentData) {
        PaymentType paymentType = PaymentType.valueOf(payment.getPaymentType().name());
        logger.info("Initiate single payment with type={}", paymentType.name());
        logger.debug("Single payment body={}", payment);

        SinglePaymentTO response = ledgersRestClient.initiateSinglePayment(PaymentType.SINGLE, paymentMapper.toSinglePaymentTO(payment)).getBody();
        logger.debug("Response from Ledgers = {}", response);

        return SpiResponse.<SpiSinglePaymentInitiationResponse>builder()
                       .aspspConsentData(initialAspspConsentData)
                       .payload(buildPaymentInitializationResponse(response.getPaymentId()))
                       .success();

    }

    @Override
    public @NotNull SpiResponse<SpiSinglePayment> getPaymentById(@NotNull SpiPsuData psuData, @NotNull SpiSinglePayment payment, @NotNull AspspConsentData aspspConsentData) {
        try {
            logger.info("Get payment by id with type={}", PaymentTypeTO.SINGLE);
            logger.debug("Single payment body={}", payment);
            SinglePaymentTO response = ledgersRestClient.getPeriodicPaymentPaymentById(PaymentTypeTO.SINGLE, PaymentProductTO.valueOf(payment.getPaymentProduct().name()), payment.getPaymentId()).getBody();
            SpiSinglePayment spiPayment = Optional.ofNullable(response)
                                                  .map(paymentMapper::toSpiSinglePayment)
                                                  .orElseThrow(() -> new RestException(MessageErrorCode.FORMAT_ERROR));
            return SpiResponse.<SpiSinglePayment>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .payload(spiPayment)
                           .success();

        } catch (RestException e) {
            return SpiResponse.<SpiSinglePayment>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .fail(getSpiFailureResponse(e));
        }
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

        ledgersRestClient.executePaymentNoSca(paymentId,
                PaymentProductTO.valueOf(paymentProduct.name()), PaymentTypeTO.SINGLE);

        return SpiResponse.<SpiResponse.VoidResponse>builder().aspspConsentData(aspspConsentData).success();
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> verifyScaAuthorisationAndExecutePayment(
            @NotNull SpiPsuData spiPsuData,
            @NotNull SpiScaConfirmation spiScaConfirmation,
            @NotNull SpiSinglePayment spiSinglePayment,
            @NotNull AspspConsentData aspspConsentData
    ) {
        SCAValidationRequest request = new SCAValidationRequest();
        request.setAuthCode(spiScaConfirmation.getTanNumber());
        //TODO: @fpo what is really should be set as data?
        request.setData(spiSinglePayment.toString());
        logger.info("Verifying SCA code");
        //TODO: @fpo where is we have get an operation ID
        boolean isValid = ledgersRestClient.validate(spiSinglePayment.getPaymentId(), request);

        logger.info("Validation result is {}", isValid);
        if (isValid) {
            executePaymentWithoutSca(spiPsuData, spiSinglePayment, aspspConsentData);
        }

        return SpiResponse.<SpiResponse.VoidResponse>builder().aspspConsentData(aspspConsentData).success();
    }

    @Override
    public @NotNull SpiResponse<SpiTransactionStatus> getPaymentStatusById(
            @NotNull SpiPsuData psuData,
            @NotNull SpiSinglePayment payment,
            @NotNull AspspConsentData aspspConsentData
    ) {
        String paymentId = payment.getPaymentId();
        TransactionStatus status = ledgersRestClient.getPaymentStatusById(paymentId).getBody();
        String paymentStatus = status.getName();

        logger.info("Payment with id={} has status {}", paymentId, paymentStatus);
        return SpiResponse.<SpiTransactionStatus>builder()
                       .aspspConsentData(aspspConsentData)
                       .payload(SpiTransactionStatus.valueOf(paymentStatus))
                       .success();
    }

    @NotNull
    private SpiResponseStatus getSpiFailureResponse(RestException e) {
        logger.error(e.getMessage(), e);
        return (e.getHttpStatus() == HttpStatus.INTERNAL_SERVER_ERROR)
                       ? SpiResponseStatus.TECHNICAL_FAILURE
                       : SpiResponseStatus.LOGICAL_FAILURE;
    }
}
