package de.adorsys.aspsp.xs2a.connector.spi.impl;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.ledgers.middleware.api.domain.sca.SCALoginResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.ledgers.middleware.api.domain.um.UserRoleTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.UserMgmtRestClient;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import feign.FeignException;
import feign.Response;

@Component
public class GeneralAuthorisationService {
    private static final Logger logger = LoggerFactory.getLogger(PaymentAuthorisationSpiImpl.class);
    private final UserMgmtRestClient userMgmtRestClient;
    private final ScaMethodConverter scaMethodConverter;
	private final AuthRequestInterceptor authRequestInterceptor;
	private final AspspConsentDataService tokenService;

	public GeneralAuthorisationService(UserMgmtRestClient userMgmtRestClient, ScaMethodConverter scaMethodConverter,
			AuthRequestInterceptor authRequestInterceptor, AspspConsentDataService tokenService) {
		super();
		this.userMgmtRestClient = userMgmtRestClient;
		this.scaMethodConverter = scaMethodConverter;
		this.authRequestInterceptor = authRequestInterceptor;
		this.tokenService = tokenService;
	}

	/**
	 * First authorization of the PSU.
	 * 
	 * The result of this authorization must contain an scaStatus with following options:
	 * - {@link ScaStatusTO#EXEMPTED} : There is no sca needed. The user does not have any sca method anyway.
	 * - {@link ScaStatusTO#SCAMETHODSELECTED} : The user has receive an authorization code and must enter it.
	 * - {@link ScaStatusTO#PSUIDENTIFIED} : the user must select a authorization method to complete auth.
	 * 
	 * In all three cases, we store the response object for reuse in an {@link AspspConsentData} object.
	 * 
	 * @param spiPsuData identification data for the psu
	 * @param pin : pis of the psu
	 * @param aspspConsentData : credential transport object.
	 * @return : the authorisation status
	 */
	public SpiResponse<SpiAuthorisationStatus> authorisePsu(@NotNull SpiPsuData spiPsuData, String pin, AspspConsentData aspspConsentData) {
        try {
            String login = spiPsuData.getPsuId();
            logger.info("Authorise user with login={} and password={}", login, StringUtils.repeat("*", 10));
            ResponseEntity<SCALoginResponseTO> response = userMgmtRestClient.authorise(login, pin, UserRoleTO.CUSTOMER);
            SpiAuthorisationStatus status = response!=null && response.getBody()!=null && response.getBody().getBearerToken()!=null
                                                    ? SpiAuthorisationStatus.SUCCESS
                                                    : SpiAuthorisationStatus.FAILURE;
            logger.info("Authorisation result is {}", status);
            return new SpiResponse<>(status, tokenService.store(response.getBody(), aspspConsentData));
        } catch (FeignException e) {
            return SpiResponse.<SpiAuthorisationStatus>builder()
                           .fail(getSpiFailureResponse(e));
		}
    }

	/**
	 * This call won't go to the server. The login process is supposed to have returned if necessary the list of 
	 * sca methods. But we return this only if a bearer token is available.
	 * 
	 * So we parse consent data and we return containing sca methods.
	 * 
	 * @param psuData identification data for the psu
	 * @param aspspConsentData : credential transport object.
	 * @return the authentication object.
	 */
    public SpiResponse<List<SpiAuthenticationObject>> requestAvailableScaMethods(@NotNull SpiPsuData psuData, AspspConsentData aspspConsentData) {
        try {
        	SCALoginResponseTO sca = tokenService.response(aspspConsentData, SCALoginResponseTO.class);
        	if(sca.getScaMethods()!=null) {
        		userMgmtRestClient.validate(sca.getBearerToken().getAccess_token());
        		List<ScaUserDataTO> scaMethods = sca.getScaMethods();
        		List<SpiAuthenticationObject> authenticationObjects = scaMethodConverter.toSpiAuthenticationObjectList(scaMethods);
        		return SpiResponse.<List<SpiAuthenticationObject>>builder()
        				.aspspConsentData(aspspConsentData)
        				.payload(authenticationObjects)
        				.success();
        	} else {
        		String message = String.format("Process mismatch. Current SCA Status is %s", sca.getScaStatus());
        		throw FeignException.errorStatus(message, Response.builder().status(HttpStatus.EXPECTATION_FAILED.value()).build());
        	}
        } catch (FeignException e) {
            return SpiResponse.<List<SpiAuthenticationObject>>builder()
                           .aspspConsentData(aspspConsentData)
                           .fail(getSpiFailureResponse(e));
        }
    }

    public SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(@NotNull SpiPsuData psuData, @NotNull String authenticationMethodId, @NotNull String businessObjId, @NotNull String businessObjectAsString, @NotNull AspspConsentData aspspConsentData) {
        try {
        	SCALoginResponseTO sca = tokenService.response(aspspConsentData, SCALoginResponseTO.class);
        	authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());
        	logger.info("Request to generate SCA {}", sca.getScaId());
        	ResponseEntity<SCALoginResponseTO> selectMethodResponse = userMgmtRestClient.selectMethod(sca.getScaId(), sca.getAuthorisationId(), authenticationMethodId);
        	logger.info("SCA was send, operationId is {}", sca.getScaId());
            return SpiResponse.<SpiAuthorizationCodeResult>builder()
                           .aspspConsentData(tokenService.store(selectMethodResponse.getBody(), aspspConsentData))
                           .success();
        } catch (FeignException e) {
            return SpiResponse.<SpiAuthorizationCodeResult>builder()
                           .aspspConsentData(aspspConsentData)
                           .fail(getSpiFailureResponse(e));
		} finally {
			authRequestInterceptor.setAccessToken(null);
		}
    }

    public @NotNull SpiResponse<SpiResponse.VoidResponse> verifyScaAuthorisation(@NotNull SpiScaConfirmation spiScaConfirmation, @NotNull String businessObjAsString, @NotNull AspspConsentData aspspConsentData) {
        logger.info("Verifying SCA code");
        try {
        	SCALoginResponseTO sca = tokenService.response(aspspConsentData, SCALoginResponseTO.class);
        	authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());
        	ResponseEntity<SCALoginResponseTO> authorizeLoginResponse = userMgmtRestClient.authorizeLogin(sca.getScaId(), sca.getAuthorisationId(), spiScaConfirmation.getTanNumber());
        	logger.info("Validation result is {}", authorizeLoginResponse.getBody().getBearerToken()!=null);
            if (authorizeLoginResponse.getBody().getBearerToken()!=null) {
                return SpiResponse.<SpiResponse.VoidResponse>builder()
                               .payload(SpiResponse.voidResponse())
                               .aspspConsentData(tokenService.store(authorizeLoginResponse.getBody(), aspspConsentData))
                               .success();
            } else {
                return SpiResponse.<SpiResponse.VoidResponse>builder()
                        .payload(SpiResponse.voidResponse())
                        .aspspConsentData(tokenService.store(sca, aspspConsentData))
                        .fail(SpiResponseStatus.UNAUTHORIZED_FAILURE);
            }
        } catch (FeignException e) {
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .fail(getSpiFailureResponse(e));
        }
    }

    private SpiResponseStatus getSpiFailureResponse(FeignException e) {
        logger.error(e.getMessage(), e);
        return e.status() == 500
                       ? SpiResponseStatus.TECHNICAL_FAILURE
                       : SpiResponseStatus.LOGICAL_FAILURE;
    }
}
