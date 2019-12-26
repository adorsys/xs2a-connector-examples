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

package de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation;

import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaLoginMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionHandler;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.*;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.ledgers.middleware.api.service.TokenStorageService;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiPsuAuthorisationResponse;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentCancellationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.PaymentCancellationSpi;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import feign.FeignException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class PaymentCancellationSpiImpl extends AbstractAuthorisationSpi<SpiPayment, SCAPaymentResponseTO> implements PaymentCancellationSpi {
    private static final Logger logger = LoggerFactory.getLogger(PaymentCancellationSpiImpl.class);

    private final PaymentRestClient paymentRestClient;
    private final TokenStorageService tokenStorageService;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final FeignExceptionReader feignExceptionReader;
    private final ScaLoginMapper scaLoginMapper;

    public PaymentCancellationSpiImpl(PaymentRestClient ledgersRestClient,
                                      TokenStorageService tokenStorageService, ScaMethodConverter scaMethodConverter,
                                      AuthRequestInterceptor authRequestInterceptor, AspspConsentDataService consentDataService,
                                      GeneralAuthorisationService authorisationService,
                                      FeignExceptionReader feignExceptionReader, ScaLoginMapper scaLoginMapper) {
        super(authRequestInterceptor, consentDataService, authorisationService, scaMethodConverter, feignExceptionReader, tokenStorageService);
        this.paymentRestClient = ledgersRestClient;
        this.tokenStorageService = tokenStorageService;
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
        this.feignExceptionReader = feignExceptionReader;
        this.scaLoginMapper = scaLoginMapper;
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

        SCAPaymentResponseTO sca = getSCAConsentResponse(aspspConsentDataProvider, true);
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
        try {
            SCAPaymentResponseTO sca = getSCAConsentResponse(aspspConsentDataProvider, true);
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
            logger.error("Verify sca authorisation and cancel payment failed: payment ID {}, devMessage {}", payment.getPaymentId(), devMessage);
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .error(new TppMessage(MessageErrorCode.PSU_CREDENTIALS_INVALID))
                           .build();
        }
    }

    @Override
    protected ResponseEntity<SCAPaymentResponseTO> getSelectMethodResponse(@NotNull String authenticationMethodId, SCAPaymentResponseTO sca) {
        return paymentRestClient.selecCancelPaymentSCAtMethod(sca.getPaymentId(), sca.getAuthorisationId(), authenticationMethodId);
    }

    @Override
    protected SCAPaymentResponseTO getSCAConsentResponse(@NotNull SpiAspspConsentDataProvider aspspConsentDataProvider, boolean checkCredentials) {
        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();
        return consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class, checkCredentials);
    }

    @Override
    protected String getBusinessObjectId(SpiPayment businessObject) {
        return businessObject.getPaymentId();
    }

    @Override
    protected OpTypeTO getOtpType() {
        return OpTypeTO.CANCEL_PAYMENT;
    }

    @Override
    protected TppMessage getAuthorisePsuFailureMessage(SpiPayment businessObject) {
        logger.error("Initiate single payment failed: payment ID {}", businessObject.getPaymentId());
        return new TppMessage(MessageErrorCode.PAYMENT_FAILED);

    }

    @Override
    protected SpiResponse<SpiPsuAuthorisationResponse> onSuccessfulAuthorisation(SpiPayment businessObject,
                                                                                 @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider,
                                                                                 SpiResponse<SpiPsuAuthorisationResponse> authorisePsu,
                                                                                 SCAPaymentResponseTO scaBusinessObjectResponse) {
        try {
            aspspConsentDataProvider.updateAspspConsentData(tokenStorageService.toBytes(scaBusinessObjectResponse));

            return SpiResponse.<SpiPsuAuthorisationResponse>builder()
                           .payload(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.SUCCESS))
                           .build();
        } catch (IOException e) {
            return SpiResponse.<SpiPsuAuthorisationResponse>builder()
                           .error(new TppMessage(MessageErrorCode.UNAUTHORIZED_CANCELLATION))
                           .build();
        }
    }

    @Override
    protected SCAResponseTO initiateBusinessObject(SpiPayment businessObject, byte[] aspspConsentData) {
        return null;
    }

    @Override
    protected SCAPaymentResponseTO mapToScaResponse(SpiPayment businessObject, byte[] aspspConsentData, SCAPaymentResponseTO originalResponse) throws IOException {
        String paymentTypeString = Optional.ofNullable(businessObject.getPaymentType()).orElseThrow(() -> new IOException("Missing payment type")).name();
        SCALoginResponseTO scaResponseTO = tokenStorageService.fromBytes(aspspConsentData, SCALoginResponseTO.class);
        SCAPaymentResponseTO paymentResponse = scaLoginMapper.toPaymentResponse(scaResponseTO);
        paymentResponse.setObjectType(SCAPaymentResponseTO.class.getSimpleName());
        paymentResponse.setPaymentId(businessObject.getPaymentId());
        paymentResponse.setPaymentType(PaymentTypeTO.valueOf(paymentTypeString));
        String paymentProduct = businessObject.getPaymentProduct();
        if (paymentProduct == null && originalResponse != null && originalResponse.getPaymentProduct() != null) {
            paymentProduct = originalResponse.getPaymentProduct();
        } else {
            throw new IOException("Missing payment product");
        }
        String unsupportedPaymentProductMessage = String.format("Unsupported payment product %s", paymentProduct);
        PaymentProductTO productTO = PaymentProductTO.getByValue(paymentProduct).orElseThrow(() -> new IOException(unsupportedPaymentProductMessage));
        paymentResponse.setPaymentProduct(productTO.getValue());
        paymentResponse.setMultilevelScaRequired(originalResponse.isMultilevelScaRequired());
        return paymentResponse;
    }

    @Override
    protected boolean validateStatuses(SpiPayment businessObject, SCAPaymentResponseTO sca) {
        return businessObject.getPaymentStatus() == TransactionStatus.RCVD ||
                       sca.getScaStatus() == ScaStatusTO.EXEMPTED;
    }

    @Override
    protected boolean isFirstInitiationOfMultilevelSca(SpiPayment businessObject, SCAPaymentResponseTO scaPaymentResponseTO) {
        return true;
    }

    @Override
    protected Optional<List<ScaUserDataTO>> getScaMethods(SCAPaymentResponseTO sca) {
        authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());
        ResponseEntity<SCAPaymentResponseTO> cancelSCA = paymentRestClient.getCancelSCA(sca.getPaymentId(), sca.getAuthorisationId());

        return Optional.ofNullable(cancelSCA.getBody())
                       .map(SCAPaymentResponseTO::getScaMethods);
    }
}
