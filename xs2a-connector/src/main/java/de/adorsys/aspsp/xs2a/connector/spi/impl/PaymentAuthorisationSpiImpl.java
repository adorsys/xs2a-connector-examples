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

import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaLoginMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.OpTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCALoginResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.ledgers.middleware.api.service.TokenStorageService;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.profile.PaymentType;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationDecoupledScaResponse;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiPsuAuthorisationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiBulkPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.*;
import feign.FeignException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO.*;

@Component
public class PaymentAuthorisationSpiImpl implements PaymentAuthorisationSpi {
    private static final Logger logger = LoggerFactory.getLogger(PaymentAuthorisationSpiImpl.class);

    private static final String DECOUPLED_PSU_MESSAGE = "Please check your app to continue...";
    private static final String DECOUPLED_NOT_SUPPORTED_MESSAGE = "Service is not supported";

    private final GeneralAuthorisationService authorisationService;
    private final TokenStorageService tokenStorageService;
    private final ScaMethodConverter scaMethodConverter;
    private final ScaLoginMapper scaLoginMapper;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final PaymentRestClient paymentRestClient;
    private final SinglePaymentSpi singlePaymentSpi;
    private final BulkPaymentSpi bulkPaymentSpi;
    private final PeriodicPaymentSpi periodicPaymentSpi;
    private final CmsPaymentStatusUpdateService cmsPaymentStatusUpdateService;
    private final FeignExceptionReader feignExceptionReader;

    public PaymentAuthorisationSpiImpl(GeneralAuthorisationService authorisationService,
                                       TokenStorageService tokenStorageService, ScaMethodConverter scaMethodConverter,
                                       ScaLoginMapper scaLoginMapper,
                                       AuthRequestInterceptor authRequestInterceptor, AspspConsentDataService consentDataService,
                                       PaymentRestClient paymentRestClient, SinglePaymentSpi singlePaymentSpi, BulkPaymentSpi bulkPaymentSpi,
                                       PeriodicPaymentSpi periodicPaymentSpi, CmsPaymentStatusUpdateService cmsPaymentStatusUpdateService,
                                       FeignExceptionReader feignExceptionReader) {
        this.authorisationService = authorisationService;
        this.tokenStorageService = tokenStorageService;
        this.scaMethodConverter = scaMethodConverter;
        this.scaLoginMapper = scaLoginMapper;
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
        this.paymentRestClient = paymentRestClient;
        this.singlePaymentSpi = singlePaymentSpi;
        this.bulkPaymentSpi = bulkPaymentSpi;
        this.periodicPaymentSpi = periodicPaymentSpi;
        this.cmsPaymentStatusUpdateService = cmsPaymentStatusUpdateService;
        this.feignExceptionReader = feignExceptionReader;
    }

    @Override
    public SpiResponse<SpiPsuAuthorisationResponse> authorisePsu(@NotNull SpiContextData contextData, @NotNull SpiPsuData psuLoginData, String pin, SpiPayment spiPayment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();

        SCAPaymentResponseTO originalResponse = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class, false);

        SpiResponse<SpiPsuAuthorisationResponse> authorisePsu = authorisationService.authorisePsuForConsent(
                psuLoginData, pin, originalResponse.getPaymentId(), originalResponse, OpTypeTO.PAYMENT, aspspConsentDataProvider);

        if (!authorisePsu.isSuccessful()) {
            return authorisePsu;
        }

        try {
            SCAPaymentResponseTO scaPaymentResponse = toPaymentConsent(spiPayment, aspspConsentDataProvider, originalResponse);
            cmsPaymentStatusUpdateService.updatePaymentStatus(spiPayment.getPaymentId(), aspspConsentDataProvider);
            aspspConsentDataProvider.updateAspspConsentData(tokenStorageService.toBytes(scaPaymentResponse));
            return initiatePmtOnExemptedIfRequired(contextData, spiPayment, authorisePsu, scaPaymentResponse, aspspConsentDataProvider);
        } catch (IOException e) {
            return SpiResponse.<SpiPsuAuthorisationResponse>builder()
                           .error(new TppMessage(MessageErrorCode.TOKEN_UNKNOWN, "Getting PSU token was failed"))
                           .build();
        }
    }

    @Override
    public SpiResponse<List<SpiAuthenticationObject>> requestAvailableScaMethods(@NotNull SpiContextData contextData, SpiPayment spiPayment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();
        SCAPaymentResponseTO sca = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class);
        List<ScaUserDataTO> scaMethods = Optional.ofNullable(sca.getScaMethods()).orElse(Collections.emptyList());
        return SpiResponse.<List<SpiAuthenticationObject>>builder()
                       .payload(scaMethodConverter.toSpiAuthenticationObjectList(scaMethods))
                       .build();
    }

    @Override
    public @NotNull SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(@NotNull SpiContextData contextData, @NotNull String authenticationMethodId, @NotNull SpiPayment spiPayment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();
        SCAPaymentResponseTO sca = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class);
        if (EnumSet.of(PSUIDENTIFIED, PSUAUTHENTICATED).contains(sca.getScaStatus())) {
            try {
                authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());
                logger.info("Request to generate SCA {}", sca.getPaymentId());
                ResponseEntity<SCAPaymentResponseTO> selectMethodResponse = paymentRestClient.selectMethod(sca.getPaymentId(), sca.getAuthorisationId(), authenticationMethodId);
                logger.info("SCA was send, operationId is {}", sca.getPaymentId());
                sca = selectMethodResponse.getBody();
                return authorisationService.returnScaMethodSelection(aspspConsentDataProvider, sca);
            } catch (FeignException feignException) {
                String devMessage = feignExceptionReader.getErrorMessage(feignException);
                logger.error("Request authorisation code failed: payment ID {}, authentication method ID {}, devMessage {}", spiPayment.getPaymentId(), authenticationMethodId, devMessage);
                return SpiResponse.<SpiAuthorizationCodeResult>builder()
                               // TODO fix response form ledgers https://git.adorsys.de/adorsys/xs2a/psd2-dynamic-sandbox/issues/185
                               .error(new TppMessage(MessageErrorCode.SCA_METHOD_UNKNOWN, StringUtils.defaultIfBlank(devMessage, "Sending SCA via phone not implemented yet")))
                               .build();
            } finally {
                authRequestInterceptor.setAccessToken(null);
            }
        } else {
            return authorisationService.getResponseIfScaSelected(aspspConsentDataProvider, sca);
        }
    }

    @Override
    public @NotNull SpiResponse<SpiAuthorisationDecoupledScaResponse> startScaDecoupled(@NotNull SpiContextData contextData, @NotNull String authorisationId, @Nullable String authenticationMethodId, @NotNull SpiPayment businessObject, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        if (authenticationMethodId == null) {
            return SpiResponse.<SpiAuthorisationDecoupledScaResponse>builder()
                           .error(new TppMessage(MessageErrorCode.SERVICE_NOT_SUPPORTED, DECOUPLED_NOT_SUPPORTED_MESSAGE))
                           .build();
        }
        SpiResponse<SpiAuthorizationCodeResult> response = requestAuthorisationCode(contextData, authenticationMethodId, businessObject, aspspConsentDataProvider);
        return response.hasError()
                       ? SpiResponse.<SpiAuthorisationDecoupledScaResponse>builder().error(response.getErrors()).build()
                       : SpiResponse.<SpiAuthorisationDecoupledScaResponse>builder().payload(new SpiAuthorisationDecoupledScaResponse(DECOUPLED_PSU_MESSAGE)).build();
    }

    public SCAPaymentResponseTO toPaymentConsent(SpiPayment spiPayment, SpiAspspConsentDataProvider aspspConsentDataProvider, SCAPaymentResponseTO originalResponse) throws IOException {
        String paymentTypeString = Optional.ofNullable(spiPayment.getPaymentType()).orElseThrow(() -> new IOException("Missing payment type")).name();
        SCALoginResponseTO scaResponseTO = tokenStorageService.fromBytes(aspspConsentDataProvider.loadAspspConsentData(), SCALoginResponseTO.class);
        SCAPaymentResponseTO paymentResponse = scaLoginMapper.toPaymentResponse(scaResponseTO);
        paymentResponse.setObjectType(SCAPaymentResponseTO.class.getSimpleName());
        paymentResponse.setPaymentId(spiPayment.getPaymentId());
        paymentResponse.setPaymentType(PaymentTypeTO.valueOf(paymentTypeString));
        String paymentProduct = spiPayment.getPaymentProduct();
        if (paymentProduct == null && originalResponse != null && originalResponse.getPaymentProduct() != null) {
            paymentProduct = originalResponse.getPaymentProduct().getValue();
        } else {
            throw new IOException("Missing payment product");
        }
        String unsupportedPaymentProductMessage = String.format("Unsupported payment product %s", paymentProduct);
        paymentResponse.setPaymentProduct(PaymentProductTO.getByValue(paymentProduct).orElseThrow(() -> new IOException(unsupportedPaymentProductMessage)));
        return paymentResponse;
    }

    private SpiResponse<SpiPsuAuthorisationResponse> initiatePmtOnExemptedIfRequired(@NotNull SpiContextData contextData, SpiPayment spiPayment, SpiResponse<SpiPsuAuthorisationResponse> authorisePsu, SCAPaymentResponseTO scaPaymentResponse, SpiAspspConsentDataProvider paymentAspspConsentDataProvider) {
        if (EnumSet.of(EXEMPTED, PSUAUTHENTICATED, PSUIDENTIFIED)
                    .contains(scaPaymentResponse.getScaStatus())) {
            return initiatePaymentOnExemptedSCA(contextData, spiPayment, authorisePsu, paymentAspspConsentDataProvider);
        }
        return SpiResponse.<SpiPsuAuthorisationResponse>builder()
                       .payload(authorisePsu.getPayload())
                       .build();
    }

    private SpiResponse<SpiPsuAuthorisationResponse> initiatePaymentOnExemptedSCA(SpiContextData contextData, SpiPayment spiPayment,
                                                                                  SpiResponse<SpiPsuAuthorisationResponse> authorisePsu, SpiAspspConsentDataProvider paymentAspspConsentDataProvider) {

        Function<SpiResponse<? extends SpiPaymentInitiationResponse>, SpiResponse<SpiPsuAuthorisationResponse>> buildResponse =
                spiResponse -> spiResponse.hasError()
                                       ? SpiResponse.<SpiPsuAuthorisationResponse>builder().error(spiResponse.getErrors()).build()
                                       : SpiResponse.<SpiPsuAuthorisationResponse>builder().payload(authorisePsu.getPayload()).build();

        // Payment initiation can only be called if exemption.
        PaymentType paymentType = spiPayment.getPaymentType();

        // Don't know who came to idea to call external API internally, but it causes now to bring this tricky hack in play
        switch (paymentType) {
            case SINGLE:
                return buildResponse.apply(
                        singlePaymentSpi.initiatePayment(contextData, (@NotNull SpiSinglePayment) spiPayment, paymentAspspConsentDataProvider));
            case BULK:
                return buildResponse.apply(
                        bulkPaymentSpi.initiatePayment(contextData, (@NotNull SpiBulkPayment) spiPayment, paymentAspspConsentDataProvider));
            case PERIODIC:
                return buildResponse.apply(
                        periodicPaymentSpi.initiatePayment(contextData, (@NotNull SpiPeriodicPayment) spiPayment, paymentAspspConsentDataProvider));
            default:
                // throw unsupported payment type
                return SpiResponse.<SpiPsuAuthorisationResponse>builder()
                               .error(new TppMessage(MessageErrorCode.PAYMENT_FAILED, String.format("Unknown payment type: %s", paymentType.getValue())))
                               .build();
        }
    }
}
