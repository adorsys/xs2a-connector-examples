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
import de.adorsys.aspsp.xs2a.spi.converter.ScaMethodConverter;
import de.adorsys.ledgers.LedgersRestClient;
import de.adorsys.ledgers.domain.payment.PaymentCancellationResponseTO;
import de.adorsys.ledgers.domain.sca.AuthCodeDataTO;
import de.adorsys.ledgers.domain.sca.SCAGenerationResponse;
import de.adorsys.ledgers.domain.sca.SCAMethodTO;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.exception.RestException;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentCancellationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.PaymentCancellationSpi;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentCancellationSpiImpl implements PaymentCancellationSpi {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SinglePaymentSpiImpl.class);

    private final LedgersRestClient ledgersRestClient;
    private final ScaMethodConverter scaMethodConverter;
    private final LedgersSpiPaymentMapper paymentMapper;

    public PaymentCancellationSpiImpl(LedgersRestClient ledgersRestClient, ScaMethodConverter scaMethodConverter, LedgersSpiPaymentMapper paymentMapper) {
        this.ledgersRestClient = ledgersRestClient;
        this.scaMethodConverter = scaMethodConverter;
        this.paymentMapper = paymentMapper;
    }

    @Override
    public @NotNull SpiResponse<SpiPaymentCancellationResponse> initiatePaymentCancellation(@NotNull SpiPsuData psuData, @NotNull SpiPayment payment, @NotNull AspspConsentData aspspConsentData) {
        try {
            logger.info("Initiate payment cancellation payment:{}, userId:{}", payment.getPaymentId(), psuData.getPsuId());
            PaymentCancellationResponseTO aspspResponse = ledgersRestClient.initiatePmtCancellation(psuData.getPsuId(), payment.getPaymentId()).getBody();
            SpiPaymentCancellationResponse response = paymentMapper.toSpiPaymentCancellationResponse(aspspResponse);
            logger.info("With response:{}", response.getTransactionStatus().name());
            return SpiResponse.<SpiPaymentCancellationResponse>builder()
                           .aspspConsentData(aspspConsentData)
                           .payload(response)
                           .success();
        } catch (RestException e) {
            return SpiResponse.<SpiPaymentCancellationResponse>builder()
                           .aspspConsentData(aspspConsentData)
                           .fail(getSpiFailureResponse(e));
        }
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> executePaymentCancellationWithoutSca(@NotNull SpiPsuData psuData, @NotNull SpiPayment payment, @NotNull AspspConsentData aspspConsentData) {
        return null; //TODO is removed in next release of xs2a
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> cancelPaymentWithoutSca(@NotNull SpiPsuData psuData, @NotNull SpiPayment payment, @NotNull AspspConsentData aspspConsentData) {
        try {
            logger.info("Cancel payment:{}, userId:{}", payment.getPaymentId(), psuData.getPsuId());
            ledgersRestClient.cancelPaymentNoSca(payment.getPaymentId());
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .aspspConsentData(aspspConsentData)
                           .payload(SpiResponse.voidResponse())
                           .success();
        } catch (RestException e) {
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .aspspConsentData(aspspConsentData)
                           .fail(getSpiFailureResponse(e));
        }
    }

    @Override
    public SpiResponse<SpiAuthorisationStatus> authorisePsu(@NotNull SpiPsuData psuData, String password, SpiPayment businessObject, AspspConsentData aspspConsentData) {
        try {
            String login = psuData.getPsuId();
            logger.info("Authorise user with login={} and password={}", login, StringUtils.repeat("*", password.length()));
            boolean isAuthorised = ledgersRestClient.authorise(login, password);
            SpiAuthorisationStatus status = isAuthorised ? SpiAuthorisationStatus.SUCCESS : SpiAuthorisationStatus.FAILURE;
            logger.info("Authorisation result is {}", status);
            return SpiResponse.<SpiAuthorisationStatus>builder()
                           .aspspConsentData(aspspConsentData)
                           .payload(status)
                           .success();
        } catch (RestException e) {
            return SpiResponse.<SpiAuthorisationStatus>builder()
                           .aspspConsentData(aspspConsentData)
                           .fail(getSpiFailureResponse(e));
        }
    }

    @Override
    public SpiResponse<List<SpiAuthenticationObject>> requestAvailableScaMethods(@NotNull SpiPsuData psuData, SpiPayment businessObject, AspspConsentData aspspConsentData) {
        try {
            String userLogin = psuData.getPsuId();
            logger.info("Retrieving sca methods for user {}", userLogin);
            List<SCAMethodTO> scaMethods = ledgersRestClient.getUserScaMethods(userLogin);
            logger.debug("These are sca methods that were found {}", scaMethods);

            List<SpiAuthenticationObject> authenticationObjects = scaMethodConverter.toSpiAuthenticationObjectList(scaMethods);
            return SpiResponse.<List<SpiAuthenticationObject>>builder()
                           .aspspConsentData(aspspConsentData)
                           .payload(authenticationObjects)
                           .success();
        } catch (RestException e) {
            return SpiResponse.<List<SpiAuthenticationObject>>builder()
                           .aspspConsentData(aspspConsentData)
                           .fail(getSpiFailureResponse(e));
        }
    }

    @Override
    public @NotNull SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(@NotNull SpiPsuData psuData, @NotNull String authenticationMethodId, @NotNull SpiPayment businessObject, @NotNull AspspConsentData aspspConsentData) {
        try {
            String userLogin = psuData.getPsuId();
            String paymentId = businessObject.getPaymentId();
            AuthCodeDataTO data = new AuthCodeDataTO(userLogin, authenticationMethodId, paymentId, businessObject.toString());
            logger.info("Request to generate SCA {}", data);

            SCAGenerationResponse response = ledgersRestClient.generate(data);
            logger.info("SCA was send, operationId is {}", response.getOpId());

            return SpiResponse.<SpiAuthorizationCodeResult>builder()
                           .aspspConsentData(aspspConsentData)
                           .success();
        } catch (RestException e) {
            return SpiResponse.<SpiAuthorizationCodeResult>builder()
                           .aspspConsentData(aspspConsentData)
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
