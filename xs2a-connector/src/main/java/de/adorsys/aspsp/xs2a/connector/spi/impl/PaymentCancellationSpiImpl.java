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

import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.ledgers.middleware.api.domain.sca.OpTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.middleware.api.service.TokenStorageService;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.*;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentCancellationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.PaymentCancellationSpi;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import feign.FeignException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO.PSUAUTHENTICATED;
import static de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO.PSUIDENTIFIED;

@Component
public class PaymentCancellationSpiImpl implements PaymentCancellationSpi {
    private static final Logger logger = LoggerFactory.getLogger(PaymentCancellationSpiImpl.class);

    private static final String DECOUPLED_PSU_MESSAGE = "Please check your app to continue...";

    private final PaymentRestClient paymentRestClient;
    private final TokenStorageService tokenStorageService;
    private final ScaMethodConverter scaMethodConverter;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final GeneralAuthorisationService authorisationService;
    private final PaymentAuthorisationSpiImpl paymentAuthorisation;
    private final FeignExceptionReader feignExceptionReader;

    public PaymentCancellationSpiImpl(PaymentRestClient ledgersRestClient,
                                      TokenStorageService tokenStorageService, ScaMethodConverter scaMethodConverter,
                                      AuthRequestInterceptor authRequestInterceptor, AspspConsentDataService consentDataService,
                                      GeneralAuthorisationService authorisationService,
                                      PaymentAuthorisationSpiImpl paymentAuthorisation,
                                      FeignExceptionReader feignExceptionReader) {
        this.paymentRestClient = ledgersRestClient;
        this.tokenStorageService = tokenStorageService;
        this.scaMethodConverter = scaMethodConverter;
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
        this.authorisationService = authorisationService;
        this.paymentAuthorisation = paymentAuthorisation;
        this.feignExceptionReader = feignExceptionReader;
    }

    @Override
    public @NotNull SpiResponse<SpiPaymentCancellationResponse> initiatePaymentCancellation(@NotNull SpiContextData contextData,
                                                                                            @NotNull SpiPayment payment,
                                                                                            @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        SpiPaymentCancellationResponse response = new SpiPaymentCancellationResponse();
        boolean cancellationMandated = payment.getPaymentStatus() != TransactionStatus.RCVD;
        response.setCancellationAuthorisationMandated(cancellationMandated);
        response.setTransactionStatus(payment.getPaymentStatus());
        //TODO to be fixed after implementation of https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/633
        return SpiResponse.<SpiPaymentCancellationResponse>builder()
                       .payload(response).build();
    }

    /**
     * Makes no sense.
     */
    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> cancelPaymentWithoutSca(@NotNull SpiContextData contextData,
                                                                                  @NotNull SpiPayment payment,
                                                                                  @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        // TODO: current implementation of Ledgers doesn't support the payment cancellation without authorisation,
        // maybe this will be implemented in the future: https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/669

        if (payment.getPaymentStatus() == TransactionStatus.RCVD) {
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .payload(SpiResponse.voidResponse())
                           .build();
        }

        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();

        SCAPaymentResponseTO sca = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class);
        if (sca.getScaStatus() == ScaStatusTO.EXEMPTED) {
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());
            try {
                paymentRestClient.initiatePmtCancellation(payment.getPaymentId());
                return SpiResponse.<SpiResponse.VoidResponse>builder()
                               .payload(SpiResponse.voidResponse())
                               .build();
            } catch (FeignException feignException) {
                String devMessage = feignExceptionReader.getErrorMessage(feignException);
                logger.error("Cancel payment without sca failed: payment ID {}, devMessage {}", payment.getPaymentId(), devMessage);
                return SpiResponse.<SpiResponse.VoidResponse>builder()
                               .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.FORMAT_ERROR_CANCELLATION, devMessage))
                               .build();
            }
        }
        return SpiResponse.<SpiResponse.VoidResponse>builder()
                       .error(new TppMessage(MessageErrorCode.CANCELLATION_INVALID))
                       .build();
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> verifyScaAuthorisationAndCancelPayment(@NotNull SpiContextData contextData,
                                                                                                 @NotNull SpiScaConfirmation spiScaConfirmation,
                                                                                                 @NotNull SpiPayment payment,
                                                                                                 @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();

        try {
            SCAPaymentResponseTO sca = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            ResponseEntity<SCAPaymentResponseTO> response = paymentRestClient.authorizeCancelPayment(sca.getPaymentId(), sca.getAuthorisationId(), spiScaConfirmation.getTanNumber());
            return response.getStatusCode() == HttpStatus.OK
                           ? SpiResponse.<SpiResponse.VoidResponse>builder()
                                     .payload(SpiResponse.voidResponse())
                                     .build()
                           : SpiResponse.<SpiResponse.VoidResponse>builder()
                                     .error(new TppMessage(MessageErrorCode.UNAUTHORIZED_CANCELLATION))
                                     .build();
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Verify SCA authorisation and cancel payment failed: payment ID {}, devMessage {}", payment.getPaymentId(), devMessage);
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .error(new TppMessage(MessageErrorCode.PSU_CREDENTIALS_INVALID))
                           .build();
        }
    }

    @Override
    public SpiResponse<SpiAuthorisationStatus> authorisePsu(@NotNull SpiContextData contextData,
                                                            @NotNull SpiPsuData psuData,
                                                            String password,
                                                            SpiPayment businessObject,
                                                            @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();

        SCAPaymentResponseTO originalResponse = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class, false);

        SpiResponse<SpiAuthorisationStatus> authorisePsu = authorisationService.authorisePsuForConsent(
                psuData, password, businessObject.getPaymentId(), originalResponse, OpTypeTO.CANCEL_PAYMENT, aspspConsentDataProvider);

        if (!authorisePsu.isSuccessful()) {
            return authorisePsu;
        }

        try {
            SCAPaymentResponseTO scaPaymentResponse = paymentAuthorisation.toPaymentConsent(businessObject, aspspConsentDataProvider, originalResponse);
            aspspConsentDataProvider.updateAspspConsentData(tokenStorageService.toBytes(scaPaymentResponse));

            return SpiResponse.<SpiAuthorisationStatus>builder()
                           .payload(SpiAuthorisationStatus.SUCCESS)
                           .build();
        } catch (IOException e) {
            return SpiResponse.<SpiAuthorisationStatus>builder()
                           .error(new TppMessage(MessageErrorCode.UNAUTHORIZED_CANCELLATION))
                           .build();
        }
    }

    @Override
    public SpiResponse<List<SpiAuthenticationObject>> requestAvailableScaMethods(@NotNull SpiContextData contextData,
                                                                                 SpiPayment businessObject,
                                                                                 @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();

        SCAPaymentResponseTO sca = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class);
        if (businessObject.getPaymentStatus() == TransactionStatus.RCVD || sca.getScaStatus() == ScaStatusTO.EXEMPTED) {
            return SpiResponse.<List<SpiAuthenticationObject>>builder()
                           .payload(Collections.emptyList())
                           .build();
        }
        authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());
        ResponseEntity<SCAPaymentResponseTO> cancelSCA = paymentRestClient.getCancelSCA(sca.getPaymentId(), sca.getAuthorisationId());

        List<SpiAuthenticationObject> authenticationObjectList = Optional.ofNullable(cancelSCA.getBody())
                                                                         .map(SCAResponseTO::getScaMethods)
                                                                         .map(scaMethodConverter::toSpiAuthenticationObjectList)
                                                                         .orElseGet(Collections::emptyList);
        return authenticationObjectList.isEmpty()
                       ? SpiResponse.<List<SpiAuthenticationObject>>builder()
                                 .error(new TppMessage(MessageErrorCode.UNAUTHORIZED, "Getting SCA methods failed"))
                                 .build()
                       : SpiResponse.<List<SpiAuthenticationObject>>builder()
                                 .payload(authenticationObjectList)
                                 .build();
    }

    @Override
    public @NotNull SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(@NotNull SpiContextData contextData,
                                                                                     @NotNull String authenticationMethodId,
                                                                                     @NotNull SpiPayment businessObject,
                                                                                     @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();

        SCAPaymentResponseTO response = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class);
        if (EnumSet.of(PSUIDENTIFIED, PSUAUTHENTICATED).contains(response.getScaStatus())) {
            try {
                authRequestInterceptor.setAccessToken(response.getBearerToken().getAccess_token());
                logger.info("Request to generate SCA {}", response.getPaymentId());
                ResponseEntity<SCAPaymentResponseTO> selectMethodResponse = paymentRestClient.selecCancelPaymentSCAtMethod(response.getPaymentId(), response.getAuthorisationId(), authenticationMethodId);
                logger.info("SCA was send, operationId is {}", response.getPaymentId());
                response = selectMethodResponse.getBody();
                return authorisationService.returnScaMethodSelection(aspspConsentDataProvider, response);
            } catch (FeignException feignException) {
                String devMessage = feignExceptionReader.getErrorMessage(feignException);
                logger.error("Request authorisation code failed: payment ID {}, authentication method ID {}, devMessage {}", businessObject.getPaymentId(), authenticationMethodId, devMessage);
                return SpiResponse.<SpiAuthorizationCodeResult>builder()
                               .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PSU_CREDENTIALS_INVALID_FOR_CANCELLATION, devMessage))
                               .build();
            } finally {
                authRequestInterceptor.setAccessToken(null);
            }
        } else {
            return authorisationService.getResponseIfScaSelected(aspspConsentDataProvider, response);
        }
    }

    @Override
    public @NotNull SpiResponse<SpiAuthorisationDecoupledScaResponse> startScaDecoupled(@NotNull SpiContextData contextData, @NotNull String authorisationId, @Nullable String authenticationMethodId, @NotNull SpiPayment businessObject, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        if (authenticationMethodId == null) {
            return SpiResponse.<SpiAuthorisationDecoupledScaResponse>builder()
                           .error(new TppMessage(MessageErrorCode.SERVICE_NOT_SUPPORTED))
                           .build();
        }

        SpiResponse<SpiAuthorizationCodeResult> response = requestAuthorisationCode(contextData, authenticationMethodId, businessObject, aspspConsentDataProvider);
        return response.hasError()
                       ? SpiResponse.<SpiAuthorisationDecoupledScaResponse>builder().error(response.getErrors()).build()
                       : SpiResponse.<SpiAuthorisationDecoupledScaResponse>builder().payload(new SpiAuthorisationDecoupledScaResponse(DECOUPLED_PSU_MESSAGE)).build();
    }
}
