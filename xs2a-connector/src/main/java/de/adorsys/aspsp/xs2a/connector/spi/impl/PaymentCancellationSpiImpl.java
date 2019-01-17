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

package de.adorsys.aspsp.xs2a.connector.spi.impl;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.ledgers.rest.client.UserMgmtRestClient;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentCancellationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.PaymentCancellationSpi;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import feign.FeignException;
import feign.Response;

@Component
public class PaymentCancellationSpiImpl implements PaymentCancellationSpi {
    private static final Logger logger = LoggerFactory.getLogger(PaymentCancellationSpiImpl.class);

    private final PaymentRestClient ledgersPayment;
	private final UserMgmtRestClient userMgmtRestClient;
    private final LedgersSpiPaymentMapper paymentMapper;
	private final ScaMethodConverter scaMethodConverter;
	private final AuthRequestInterceptor authRequestInterceptor;
	private final AspspConsentDataService tokenService;
	private final GeneralAuthorisationService authorisationService;

	public PaymentCancellationSpiImpl(PaymentRestClient ledgersRestClient, UserMgmtRestClient userMgmtRestClient,
			LedgersSpiPaymentMapper paymentMapper, ScaMethodConverter scaMethodConverter,
			AuthRequestInterceptor authRequestInterceptor, AspspConsentDataService tokenService,
			GeneralAuthorisationService authorisationService) {
		super();
		this.ledgersPayment = ledgersRestClient;
		this.userMgmtRestClient = userMgmtRestClient;
		this.paymentMapper = paymentMapper;
		this.scaMethodConverter = scaMethodConverter;
		this.authRequestInterceptor = authRequestInterceptor;
		this.tokenService = tokenService;
		this.authorisationService = authorisationService;
	}

	@Override
    public @NotNull SpiResponse<SpiPaymentCancellationResponse> initiatePaymentCancellation(@NotNull SpiContextData contextData, @NotNull SpiPayment payment, @NotNull AspspConsentData aspspConsentData) {
        try {
			SCAResponseTO sca = tokenService.response(aspspConsentData);
			authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            logger.info("Initiate payment cancellation payment:{}, userId:{}", payment.getPaymentId(), contextData.getPsuData().getPsuId());
            SCAPaymentResponseTO responseTO = ledgersPayment.initiatePmtCancellation(payment.getPaymentId()).getBody();
            SpiPaymentCancellationResponse response = paymentMapper.toSpiPaymentCancellationResponse(responseTO);
            logger.info("With response:{}", response.getTransactionStatus().name());
            return SpiResponse.<SpiPaymentCancellationResponse>builder()
                           .aspspConsentData(aspspConsentData)
                           .payload(response)
                           .success();
        } catch (FeignException e) {
            return SpiResponse.<SpiPaymentCancellationResponse>builder()
                           .aspspConsentData(aspspConsentData)
                           .fail(getSpiFailureResponse(e));
		} finally {
			authRequestInterceptor.setAccessToken(null);
		}
    }

	/**
	 * Makes no sense.
	 */
    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> cancelPaymentWithoutSca(@NotNull SpiContextData contextData, @NotNull SpiPayment payment, @NotNull AspspConsentData aspspConsentData) {
    	throw new UnsupportedOperationException("Can not proceed without sca.");
    }

    @Override
    public @NotNull SpiResponse<SpiResponse.VoidResponse> verifyScaAuthorisationAndCancelPayment(@NotNull SpiContextData contextData, @NotNull SpiScaConfirmation spiScaConfirmation, @NotNull SpiPayment payment, @NotNull AspspConsentData aspspConsentData) {
        SpiResponse<SpiResponse.VoidResponse> authResponse = authorisationService.verifyScaAuthorisation(spiScaConfirmation, payment.toString(), aspspConsentData);
        return authResponse.isSuccessful()
                       ? cancelPaymentWithoutSca(contextData, payment, aspspConsentData)
                       : authResponse;
    }

    @Override
    public SpiResponse<SpiAuthorisationStatus> authorisePsu(@NotNull SpiContextData contextData, @NotNull SpiPsuData psuData, String password, SpiPayment businessObject, AspspConsentData aspspConsentData) {
        return authorisationService.authorisePsu(psuData, password, aspspConsentData);
    }

    @Override
    public SpiResponse<List<SpiAuthenticationObject>> requestAvailableScaMethods(@NotNull SpiContextData contextData, SpiPayment businessObject, AspspConsentData aspspConsentData) {
		try {
			SCAPaymentResponseTO sca = tokenService.response(aspspConsentData, SCAPaymentResponseTO.class);
			if (sca.getScaMethods() != null) {

				// Validate the access token
				userMgmtRestClient.validate(sca.getBearerToken().getAccess_token());

				// Return contained sca methods.
				List<ScaUserDataTO> scaMethods = sca.getScaMethods();
				List<SpiAuthenticationObject> authenticationObjects = scaMethodConverter
						.toSpiAuthenticationObjectList(scaMethods);
				return SpiResponse.<List<SpiAuthenticationObject>>builder().aspspConsentData(aspspConsentData)
						.payload(authenticationObjects).success();
			} else {
				String message = String.format("Process mismatch. Current SCA Status is %s", sca.getScaStatus());
				throw FeignException.errorStatus(message,
						Response.builder().status(HttpStatus.EXPECTATION_FAILED.value()).build());
			}
		} catch (FeignException e) {
			return SpiResponse.<List<SpiAuthenticationObject>>builder().aspspConsentData(aspspConsentData)
					.fail(getSpiFailureResponse(e));
		}
    }

    @Override
    public @NotNull SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(@NotNull SpiContextData contextData, @NotNull String authenticationMethodId, @NotNull SpiPayment businessObject, @NotNull AspspConsentData aspspConsentData) {
		try {
			SCAPaymentResponseTO sca = tokenService.response(aspspConsentData, SCAPaymentResponseTO.class);
			authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

			SCAPaymentResponseTO consentResponse = ledgersPayment
					.selectMethod(sca.getPaymentId(), sca.getAuthorisationId(), authenticationMethodId).getBody();

			return SpiResponse.<SpiAuthorizationCodeResult>builder()
					.aspspConsentData(tokenService.store(consentResponse, aspspConsentData))
					.message(consentResponse.getScaStatus().name()).success();
		} finally {
			authRequestInterceptor.setAccessToken(null);
		}
    }

    @NotNull
    private SpiResponseStatus getSpiFailureResponse(FeignException e) {
        logger.error(e.getMessage(), e);
        return e.status() == 500
                       ? SpiResponseStatus.TECHNICAL_FAILURE
                       : SpiResponseStatus.LOGICAL_FAILURE;
    }
}
