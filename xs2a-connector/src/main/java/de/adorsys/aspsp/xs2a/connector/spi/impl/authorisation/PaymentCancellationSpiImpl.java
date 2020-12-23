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

import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaResponseMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionHandler;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.aspsp.xs2a.connector.spi.impl.LedgersErrorCode;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.ledgers.keycloak.client.api.KeycloakTokenService;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.OpTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.ledgers.rest.client.RedirectScaRestClient;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentCancellationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
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

import java.util.List;
import java.util.Optional;

@Component
public class PaymentCancellationSpiImpl extends AbstractAuthorisationSpi<SpiPayment> implements PaymentCancellationSpi {
    private static final Logger logger = LoggerFactory.getLogger(PaymentCancellationSpiImpl.class);

    private final PaymentRestClient paymentRestClient;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final FeignExceptionReader feignExceptionReader;
    private final RedirectScaRestClient redirectScaRestClient;
    private final ScaResponseMapper scaResponseMapper;
    private final GeneralPaymentService paymentService;

    public PaymentCancellationSpiImpl(PaymentRestClient ledgersRestClient, //NOSONAR
                                      ScaMethodConverter scaMethodConverter,
                                      AuthRequestInterceptor authRequestInterceptor, AspspConsentDataService consentDataService,
                                      GeneralAuthorisationService authorisationService,
                                      FeignExceptionReader feignExceptionReader,
                                      RedirectScaRestClient redirectScaRestClient,
                                      KeycloakTokenService keycloakTokenService,
                                      GeneralPaymentService paymentService,
                                      ScaResponseMapper scaResponseMapper) {
        super(authRequestInterceptor, consentDataService, authorisationService, scaMethodConverter, feignExceptionReader,
              keycloakTokenService, redirectScaRestClient);
        this.paymentRestClient = ledgersRestClient;
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
        this.feignExceptionReader = feignExceptionReader;
        this.redirectScaRestClient = redirectScaRestClient;
        this.paymentService = paymentService;
        this.scaResponseMapper = scaResponseMapper;
    }

    /**
     * Initiates payment cancellation process
     *
     * @param contextData              holder of call's context data (e.g. about PSU and TPP)
     * @param payment                  Payment to be cancelled
     * @param aspspConsentDataProvider Provides access to read/write encrypted data to be stored in the consent management system
     * @return Payment cancellation response with information about transaction status and whether authorisation of the request is required
     */
    @Override
    public @NotNull SpiResponse<SpiPaymentCancellationResponse> initiatePaymentCancellation(@NotNull SpiContextData contextData,
                                                                                            @NotNull SpiPayment payment,
                                                                                            @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        SpiPaymentCancellationResponse response = new SpiPaymentCancellationResponse();
        boolean cancellationMandated = payment.getPaymentStatus() != TransactionStatus.RCVD;
        response.setCancellationAuthorisationMandated(cancellationMandated);
        response.setTransactionStatus(payment.getPaymentStatus());
        return SpiResponse.<SpiPaymentCancellationResponse>builder()
                       .payload(response).build();
    }

    /**
     * Cancels payment without performing strong customer authentication
     *
     * @param contextData              holder of call's context data (e.g. about PSU and TPP)
     * @param payment                  Payment to be cancelled
     * @param aspspConsentDataProvider Provides access to read/write encrypted data to be stored in the consent management system
     * @return Return a positive or negative response as part of SpiResponse
     */
    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> cancelPaymentWithoutSca(@NotNull SpiContextData contextData,
                                                                                  @NotNull SpiPayment payment,
                                                                                  @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        if (payment.getPaymentStatus() == TransactionStatus.RCVD) {
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .payload(SpiResponse.voidResponse())
                           .build();
        }

        GlobalScaResponseTO sca = getScaObjectResponse(aspspConsentDataProvider, true);
        if (sca.getScaStatus() == ScaStatusTO.EXEMPTED) {
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());
            try {
                paymentRestClient.initiatePmtCancellation(payment.getPaymentId());
                return SpiResponse.<SpiResponse.VoidResponse>builder()
                               .payload(SpiResponse.voidResponse())
                               .build();
            } catch (FeignException feignException) {
                String devMessage = feignExceptionReader.getErrorMessage(feignException);
                logger.error("Cancel payment without SCA failed: payment ID: {}, devMessage: {}", payment.getPaymentId(), devMessage);
                return SpiResponse.<SpiResponse.VoidResponse>builder()
                               .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.FORMAT_ERROR_CANCELLATION, devMessage))
                               .build();
            }
        }
        return SpiResponse.<SpiResponse.VoidResponse>builder()
                       .error(new TppMessage(MessageErrorCode.CANCELLATION_INVALID))
                       .build();
    }

    /**
     * Sends authorisation confirmation information (secure code or such) to ASPSP and if case of successful validation cancels payment at ASPSP.
     *
     * @param contextData              holder of call's context data (e.g. about PSU and TPP)
     * @param spiScaConfirmation       payment cancellation confirmation information
     * @param payment                  Payment to be cancelled
     * @param aspspConsentDataProvider Provides access to read/write encrypted data to be stored in the consent management system
     * @return Return a positive or negative response as part of SpiResponse
     */
    @Override
    public @NotNull SpiResponse<SpiPaymentExecutionResponse> verifyScaAuthorisationAndCancelPaymentWithResponse(@NotNull SpiContextData contextData,
                                                                                                                @NotNull SpiScaConfirmation spiScaConfirmation,
                                                                                                                @NotNull SpiPayment payment,
                                                                                                                @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        try {
            GlobalScaResponseTO sca = consentDataService.response(aspspConsentDataProvider.loadAspspConsentData());
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            ResponseEntity<GlobalScaResponseTO> authorizeCancelPaymentResponse = redirectScaRestClient.validateScaCode(sca.getAuthorisationId(), spiScaConfirmation.getTanNumber());

            if (authorizeCancelPaymentResponse != null &&
                        authorizeCancelPaymentResponse.getBody() != null &&
                        authorizeCancelPaymentResponse.getStatusCode() == HttpStatus.OK) {

                GlobalScaResponseTO authorizeCancelPayment = authorizeCancelPaymentResponse.getBody();
                if (authorizeCancelPayment.getBearerToken() != null) { //NOSONAR
                    String authCancellationBearerToken = authorizeCancelPayment.getBearerToken().getAccess_token();
                    authRequestInterceptor.setAccessToken(authCancellationBearerToken);
                }

                paymentRestClient.executeCancelPayment(sca.getOperationObjectId());

                aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(authorizeCancelPaymentResponse.getBody()));

                return SpiResponse.<SpiPaymentExecutionResponse>builder()
                               .payload(new SpiPaymentExecutionResponse(SpiAuthorisationStatus.SUCCESS))
                               .build();
            }

            return SpiResponse.<SpiPaymentExecutionResponse>builder()
                           .error(new TppMessage(MessageErrorCode.UNAUTHORIZED_CANCELLATION))
                           .build();
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Verify SCA authorisation and cancel payment failed: payment ID: {}, devMessage: {}", payment.getPaymentId(), devMessage);

            LedgersErrorCode errorCode = feignExceptionReader.getLedgersErrorCode(feignException);
            if (LedgersErrorCode.SCA_VALIDATION_ATTEMPT_FAILED.equals(errorCode)) {
                return SpiResponse.<SpiPaymentExecutionResponse>builder()
                               .payload(new SpiPaymentExecutionResponse(SpiAuthorisationStatus.ATTEMPT_FAILURE))
                               .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PSU_CREDENTIALS_INVALID, devMessage))
                               .build();
            }
            return SpiResponse.<SpiPaymentExecutionResponse>builder()
                           .error(new TppMessage(MessageErrorCode.PSU_CREDENTIALS_INVALID))
                           .build();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    @Override
    protected String getBusinessObjectId(SpiPayment businessObject) {
        return businessObject.getPaymentId();
    }

    @Override
    protected OpTypeTO getOpType() {
        return OpTypeTO.CANCEL_PAYMENT;
    }

    @Override
    protected TppMessage getAuthorisePsuFailureMessage(SpiPayment businessObject) {
        logger.error("Authorising payment cancellation failed, payment ID: {}", businessObject.getPaymentId());
        return new TppMessage(MessageErrorCode.PAYMENT_FAILED);
    }

    @Override
    protected GlobalScaResponseTO initiateBusinessObject(SpiPayment businessObject, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider, String authorisationId) {
        return paymentService.initiatePaymentCancellationInLedgers(businessObject.getPaymentId());
    }

    @Override
    protected boolean validateStatuses(SpiPayment businessObject, GlobalScaResponseTO sca) {
        return businessObject.getPaymentStatus() == TransactionStatus.RCVD ||
                       sca.getScaStatus() == ScaStatusTO.EXEMPTED;
    }

    @Override
    protected boolean isFirstInitiationOfMultilevelSca(SpiPayment businessObject, GlobalScaResponseTO scaPaymentResponseTO) {
        return true;
    }

    @Override
    protected GlobalScaResponseTO executeBusinessObject(SpiPayment businessObject) {
        return scaResponseMapper.toGlobalScaResponse(paymentRestClient.executeCancelPayment(businessObject.getPaymentId()).getBody());
    }

    @Override
    protected void updateStatusInCms(String businessObjectId, SpiAspspConsentDataProvider aspspConsentDataProvider) {
    }

    @Override
    protected Optional<List<ScaUserDataTO>> getScaMethods(GlobalScaResponseTO sca) {
        authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());
        ResponseEntity<GlobalScaResponseTO> cancelScaResponse = redirectScaRestClient.getSCA(sca.getAuthorisationId());

        return Optional.ofNullable(cancelScaResponse.getBody()).map(GlobalScaResponseTO::getScaMethods);
    }

    @Override
    public @NotNull SpiResponse<Boolean> requestTrustedBeneficiaryFlag(@NotNull SpiContextData spiContextData, @NotNull SpiPayment payment, @NotNull String authorisationId, @NotNull SpiAspspConsentDataProvider spiAspspConsentDataProvider) {
        // TODO replace with real response from ledgers https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/-/issues/1263
        logger.info("Retrieving mock trusted beneficiaries flag for payment cancellation: {}", payment);
        return SpiResponse.<Boolean>builder()
                       .payload(true)
                       .build();
    }
}
