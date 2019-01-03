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

import java.time.LocalDateTime;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import de.adorsys.aspsp.xs2a.spi.converter.AisConsentMapper;
import de.adorsys.aspsp.xs2a.spi.converter.ScaMethodConverter;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAConsentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.middleware.api.domain.um.AisConsentTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.ConsentRestClient;
import de.adorsys.ledgers.rest.client.UserMgmtRestClient;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiAccountAccess;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiInitiateAisConsentResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse.VoidResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.AisConsentSpi;
import feign.FeignException;
import feign.Response;

@Component
public class AisConsentSpiImpl implements AisConsentSpi {
	private static final Logger logger = LoggerFactory.getLogger(AisConsentSpiImpl.class);

	private final ConsentRestClient consentRestClient;
	private final UserMgmtRestClient userMgmtRestClient;
	private final AisConsentMapper aisConsentMapper;
	private final AuthRequestInterceptor authRequestInterceptor;
	private final AspspConsentDataService tokenService;
	private final GeneralAuthorisationService authorisationService;
	private final ScaMethodConverter scaMethodConverter;

	public AisConsentSpiImpl(ConsentRestClient consentRestClient, UserMgmtRestClient userMgmtRestClient,
			AisConsentMapper aisConsentMapper, AuthRequestInterceptor authRequestInterceptor,
			AspspConsentDataService tokenService, GeneralAuthorisationService authorisationService,
			ScaMethodConverter scaMethodConverter) {
		super();
		this.consentRestClient = consentRestClient;
		this.userMgmtRestClient = userMgmtRestClient;
		this.aisConsentMapper = aisConsentMapper;
		this.authRequestInterceptor = authRequestInterceptor;
		this.tokenService = tokenService;
		this.authorisationService = authorisationService;
		this.scaMethodConverter = scaMethodConverter;
	}

	/*
	 * Initiates an ais consent. Initiation request assumes that the psu id at least
	 * identified. THis is, we read a {@link SCAResponseTO} object from the {@link AspspConsentData} input.
	 */
	@Override
	public SpiResponse<SpiInitiateAisConsentResponse> initiateAisConsent(@NotNull SpiContextData contextData,
			SpiAccountConsent accountConsent, AspspConsentData initialAspspConsentData) {

		try {
			SCAResponseTO sca = tokenService.response(initialAspspConsentData);
			authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

			AisConsentTO aisConsent = aisConsentMapper.toTo(accountConsent);

			ResponseEntity<SCAConsentResponseTO> cosentResponse = consentRestClient.startSCA(accountConsent.getId(),
					aisConsent);
			SCAConsentResponseTO consentResponse = cosentResponse.getBody();

			SpiAccountAccess accountAccess = aisConsentMapper.toSpi(accountConsent);
			return SpiResponse.<SpiInitiateAisConsentResponse>builder()
					.payload(new SpiInitiateAisConsentResponse(accountAccess))
					.aspspConsentData(tokenService.store(consentResponse, initialAspspConsentData))
					// Pass sca status as message.
					.message(consentResponse.getScaStatus().name()).success();
		} finally {
			authRequestInterceptor.setAccessToken(null);
		}
	}

	/*
	 * Maybe store the corresponding token in the list of revoked token.
	 * 
	 * TODO: Implement this functionality
	 * 
	 */
	@Override
	public SpiResponse<VoidResponse> revokeAisConsent(@NotNull SpiContextData contextData,
			SpiAccountConsent accountConsent, AspspConsentData aspspConsentData) {
		SCAConsentResponseTO sca = tokenService.response(aspspConsentData, SCAConsentResponseTO.class);
		sca.setScaStatus(ScaStatusTO.FINALISED);
		sca.setStatusDate(LocalDateTime.now());
		sca.setBearerToken(new BearerTokenTO());// remove existing token.
		return SpiResponse.<SpiResponse.VoidResponse>builder().payload(SpiResponse.voidResponse())
				.aspspConsentData(tokenService.store(sca, aspspConsentData)).message(sca.getScaStatus().name())
				.success();
	}

	/*
	 * Verify tan, store resulting token in the returned accountConsent. The token
	 * must be presented when TPP requests account information.
	 * 
	 */
	@Override
	public @NotNull SpiResponse<VoidResponse> verifyScaAuthorisation(@NotNull SpiContextData contextData,
			@NotNull SpiScaConfirmation spiScaConfirmation, @NotNull SpiAccountConsent accountConsent,
			@NotNull AspspConsentData aspspConsentData) {
		try {
			SCAConsentResponseTO sca = tokenService.response(aspspConsentData, SCAConsentResponseTO.class);
			authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

			ResponseEntity<SCAConsentResponseTO> authorizeConsentResponse = consentRestClient
					.authorizeConsent(sca.getConsentId(), sca.getAuthorisationId(), spiScaConfirmation.getTanNumber());
			SCAConsentResponseTO consentResponse = authorizeConsentResponse.getBody();

			return SpiResponse.<SpiResponse.VoidResponse>builder().payload(SpiResponse.voidResponse())
					.aspspConsentData(tokenService.store(consentResponse, aspspConsentData))
					.message(consentResponse.getScaStatus().name()).success();
		} finally {
			authRequestInterceptor.setAccessToken(null);
		}
	}

	/*
	 * Login the user. On successful login, store the token in the resulting status
	 * object.
	 */
	@Override
	public SpiResponse<SpiAuthorisationStatus> authorisePsu(@NotNull SpiContextData contextData,
			@NotNull SpiPsuData psuLoginData, String password, SpiAccountConsent businessObject,
			@NotNull AspspConsentData aspspConsentData) {
		return authorisationService.authorisePsu(psuLoginData, password, aspspConsentData);
	}

	/**
	 * This call must follow an init consent request, therefore we are expecting the
	 * {@link AspspConsentData} object to contain a {@link SCAConsentResponseTO}
	 * response.
	 * 
	 */
	@Override
	public SpiResponse<List<SpiAuthenticationObject>> requestAvailableScaMethods(@NotNull SpiContextData contextData,
			SpiAccountConsent businessObject, @NotNull AspspConsentData aspspConsentData) {
		try {
			SCAConsentResponseTO sca = tokenService.response(aspspConsentData, SCAConsentResponseTO.class);
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
	public @NotNull SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(
			@NotNull SpiContextData contextData, @NotNull String authenticationMethodId,
			@NotNull SpiAccountConsent businessObject, @NotNull AspspConsentData aspspConsentData) {
		try {
			SCAConsentResponseTO sca = tokenService.response(aspspConsentData, SCAConsentResponseTO.class);
			authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

			ResponseEntity<SCAConsentResponseTO> selectMethodResponse = consentRestClient
					.selectMethod(sca.getConsentId(), sca.getAuthorisationId(), authenticationMethodId);
			SCAConsentResponseTO consentResponse = selectMethodResponse.getBody();

			return SpiResponse.<SpiAuthorizationCodeResult>builder()
					.aspspConsentData(tokenService.store(consentResponse, aspspConsentData))
					.message(consentResponse.getScaStatus().name()).success();
		} finally {
			authRequestInterceptor.setAccessToken(null);
		}
	}

	private SpiResponseStatus getSpiFailureResponse(FeignException e) {
		logger.error(e.getMessage(), e);
		return e.status() == 500 ? SpiResponseStatus.TECHNICAL_FAILURE : SpiResponseStatus.LOGICAL_FAILURE;
	}
}
