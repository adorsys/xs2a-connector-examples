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

import de.adorsys.ledgers.middleware.api.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.payment.TransactionStatusTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.ledgers.util.Ids;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiGetPaymentStatusResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import feign.FeignException;
import feign.Response;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class GeneralPaymentService {
    private static final Logger logger = LoggerFactory.getLogger(GeneralPaymentService.class);
    private final PaymentRestClient paymentRestClient;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;

    public GeneralPaymentService(PaymentRestClient ledgersRestClient,
                                 AuthRequestInterceptor authRequestInterceptor, AspspConsentDataService consentDataService) {
        this.paymentRestClient = ledgersRestClient;
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
    }

    public SpiResponse<SpiGetPaymentStatusResponse> getPaymentStatusById(@NotNull PaymentTypeTO paymentType, @NotNull String paymentId, @NotNull TransactionStatus spiTransactionStatus, @NotNull byte[] aspspConsentData) {
        if (!TransactionStatus.ACSP.equals(spiTransactionStatus)) {
            return SpiResponse.<SpiGetPaymentStatusResponse>builder()
                           .payload(new SpiGetPaymentStatusResponse(spiTransactionStatus, null))
                           .build();
        }
        try {
            SCAPaymentResponseTO sca = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            logger.info("Get payment status by ID with type: {} and ID: {}", paymentType, paymentId);
            TransactionStatusTO response = paymentRestClient.getPaymentStatusById(sca.getPaymentId()).getBody();
            TransactionStatus status = Optional.ofNullable(response)
                                               .map(r -> TransactionStatus.valueOf(r.name()))
                                               .orElseThrow(() -> FeignException.errorStatus("Request failed, response was 200, but body was empty!",
                                                                                             Response.builder().status(HttpStatus.BAD_REQUEST.value()).build()));
            logger.info("Transaction status: {}", status);
            return SpiResponse.<SpiGetPaymentStatusResponse>builder()
                           .payload(new SpiGetPaymentStatusResponse(status, null))
                           .build();
        } catch (FeignException e) {
            return SpiResponse.<SpiGetPaymentStatusResponse>builder()
                           .error(new TppMessage(MessageErrorCode.FORMAT_ERROR, "Couldn't get payment status by ID"))
                           .build();

        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    public SpiResponse<SpiPaymentExecutionResponse> verifyScaAuthorisationAndExecutePayment(@NotNull SpiScaConfirmation spiScaConfirmation, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        try {
            SCAPaymentResponseTO sca = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData(), SCAPaymentResponseTO.class);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            ResponseEntity<SCAPaymentResponseTO> authorizePaymentResponse = paymentRestClient.authorizePayment(sca.getPaymentId(), sca.getAuthorisationId(), spiScaConfirmation.getTanNumber());
            SCAPaymentResponseTO consentResponse = authorizePaymentResponse.getBody();

            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(consentResponse));

            String scaStatus = Optional.ofNullable(consentResponse)
                                       .map(SCAResponseTO::getScaStatus)
                                       .map(ScaStatusTO::name)
                                       .orElse(null);

            logger.info("SCA status is: {}", scaStatus);
            return SpiResponse.<SpiPaymentExecutionResponse>builder()
                           .payload(spiPaymentExecutionResponse(consentResponse.getTransactionStatus()))
                           .build();
        } catch (Exception e) {
            return SpiResponse.<SpiPaymentExecutionResponse>builder()
                           .error(new TppMessage(MessageErrorCode.FORMAT_ERROR, "Couldn't execute payment"))
                           .build();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    /**
     * Instantiating the very first response object.
     *
     * @param paymentType              the payment type
     * @param payment                  the payment object
     * @param aspspConsentDataProvider the credential data container access
     * @param responsePayload          the instantiated payload object
     */
    <T extends SpiPaymentInitiationResponse> SpiResponse<T> firstCallInstantiatingPayment(
            @NotNull PaymentTypeTO paymentType, @NotNull SpiPayment payment,
            @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider, T responsePayload
    ) {
        String paymentId = StringUtils.isNotBlank(payment.getPaymentId())
                                   ? payment.getPaymentId()
                                   : Ids.id();
        SCAPaymentResponseTO response = new SCAPaymentResponseTO();
        response.setPaymentId(paymentId);
        response.setTransactionStatus(TransactionStatusTO.RCVD);
        response.setPaymentProduct(PaymentProductTO.getByValue(payment.getPaymentProduct()).orElse(null));
        response.setPaymentType(paymentType);
        responsePayload.setPaymentId(paymentId);
//		responsePayload.setAspspAccountId();// TODO ID of the deposit account
        responsePayload.setTransactionStatus(TransactionStatus.valueOf(response.getTransactionStatus().name()));

        aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(response, false));

        return SpiResponse.<T>builder()
                       .payload(responsePayload)
                       .build();
    }

    @NotNull SpiResponse<SpiPaymentExecutionResponse> executePaymentWithoutSca(@NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        try {
            // First check if there is any payment response ongoing.
            SCAPaymentResponseTO response = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData(), SCAPaymentResponseTO.class);

            ScaStatusTO scaStatus = response.getScaStatus();
            String scaStatusName = scaStatus.name();

            if (ScaStatusTO.EXEMPTED.equals(scaStatus) || ScaStatusTO.FINALISED.equals(scaStatus)) {
                // Success

                logger.info("SCA status is: {}", scaStatusName);
                logger.info("Payment scheduled for execution. Transaction status is: {}. Also see SCA status", response.getTransactionStatus());

                return SpiResponse.<SpiPaymentExecutionResponse>builder()
                               .payload(spiPaymentExecutionResponse(response.getTransactionStatus()))
                               .build();
            }

            String message = String.format("Payment not executed. Transaction status is: %s. SCA status: %s."
                    ,response.getTransactionStatus(), scaStatusName);

            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(response));
            return SpiResponse.<SpiPaymentExecutionResponse>builder()
                           .error(new TppMessage(MessageErrorCode.FORMAT_ERROR, message))
                           .build();
        } catch (FeignException e) {
            return SpiResponse.<SpiPaymentExecutionResponse>builder()
                           .error(FeignExceptionHandler.getFailureMessage(e, MessageErrorCode.FORMAT_ERROR, "Couldn't execute payment"))
                           .build();
        }
    }

    Optional<Object> getPaymentById(String paymentId, String toString, byte[] aspspConsentData) {
        try {
            SCAPaymentResponseTO sca = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            logger.info("Get payment by ID with type: {} and ID: {}", PaymentTypeTO.SINGLE, paymentId);
            logger.debug("Single payment body: {}", toString);
            // Normally the paymentId contained here must match the payment id
            // String paymentId = sca.getPaymentId(); This could also be used.
            // TODO: store payment type in sca.
            return Optional.ofNullable(paymentRestClient.getPaymentById(sca.getPaymentId()).getBody());
        } catch (FeignException e) {
            logger.error(e.getMessage());
            return Optional.empty();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    private SpiPaymentExecutionResponse spiPaymentExecutionResponse(TransactionStatusTO transactionStatus) {
        return new SpiPaymentExecutionResponse(TransactionStatus.valueOf(transactionStatus.name()));
    }
}
