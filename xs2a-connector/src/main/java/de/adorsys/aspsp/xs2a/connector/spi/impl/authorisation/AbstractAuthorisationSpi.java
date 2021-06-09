package de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation;

import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.aspsp.xs2a.connector.spi.converter.SpiScaStatusResponseMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.*;
import de.adorsys.ledgers.keycloak.client.api.KeycloakTokenService;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.OpTypeTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.RedirectScaRestClient;
import de.adorsys.psd2.xs2a.core.authorisation.AuthenticationObject;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.*;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO.*;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractAuthorisationSpi<T> {

    private static final String DECOUPLED_PSU_MESSAGE = "Please check your app to continue...";
    private static final String LOGIN_AMOUNT_ATTEMPTS_REMAINING_MESSAGE = "You have %s attempts to enter valid credentials";
    private static final String PSU_MESSAGE = "Mocked PSU message from SPI.";

    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final GeneralAuthorisationService authorisationService;
    private final ScaMethodConverter scaMethodConverter;
    private final FeignExceptionReader feignExceptionReader;
    private final KeycloakTokenService keycloakTokenService;
    private final RedirectScaRestClient redirectScaRestClient;
    private final SpiScaStatusResponseMapper spiScaStatusResponseMapper;

    protected ResponseEntity<GlobalScaResponseTO> getSelectMethodResponse(@NotNull String authenticationMethodId, GlobalScaResponseTO sca) {
        ResponseEntity<GlobalScaResponseTO> scaResponse = redirectScaRestClient.selectMethod(sca.getAuthorisationId(), authenticationMethodId);

        return scaResponse.getStatusCode() == HttpStatus.OK
                       ? ResponseEntity.ok(scaResponse.getBody())
                       : ResponseEntity.badRequest().build();
    }

    protected GlobalScaResponseTO getScaObjectResponse(SpiAspspConsentDataProvider aspspConsentDataProvider, boolean checkCredentials) {
        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();
        return consentDataService.response(aspspConsentData, checkCredentials);
    }

    protected abstract String getBusinessObjectId(T businessObject);

    protected abstract OpTypeTO getOpType();

    protected abstract TppMessage getAuthorisePsuFailureMessage(T businessObject);

    protected abstract GlobalScaResponseTO initiateBusinessObject(T businessObject, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider, String authorisationId);

    protected abstract boolean isFirstInitiationOfMultilevelSca(T businessObject, GlobalScaResponseTO scaBusinessObjectResponse);

    protected abstract GlobalScaResponseTO executeBusinessObject(T businessObject);

    protected abstract void updateStatusInCms(String businessObjectId, SpiAspspConsentDataProvider aspspConsentDataProvider);

    protected String generatePsuMessage(@NotNull SpiContextData contextData, @NotNull String authorisationId,
                                        @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider,
                                        SpiResponse<SpiAuthorizationCodeResult> response) {
        return DECOUPLED_PSU_MESSAGE;
    }

    protected boolean validateStatuses(T businessObject, GlobalScaResponseTO sca) {
        return false;
    }

    public SpiResponse<SpiPsuAuthorisationResponse> authorisePsu(@NotNull SpiContextData contextData,
                                                                 @NotNull String authorisationId,
                                                                 @NotNull SpiPsuData psuLoginData, String password, T businessObject,
                                                                 @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        try {
            BearerTokenTO loginToken = keycloakTokenService.login(contextData.getPsuData().getPsuId(), password);
            authRequestInterceptor.setAccessToken(loginToken.getAccess_token());

        } catch (FeignException feignException) {
            return handleLoginFailureError(businessObject, aspspConsentDataProvider, feignException);
        }

        GlobalScaResponseTO scaResponseTO;
        try {
            scaResponseTO = initiateBusinessObject(businessObject, aspspConsentDataProvider, authorisationId);

        } catch (FeignException feignException) {
            return resolveErrorResponse(businessObject, feignException);
        }

        if (scaResponseTO.getScaStatus() == EXEMPTED && isFirstInitiationOfMultilevelSca(businessObject, scaResponseTO)) {

            try {
                authRequestInterceptor.setAccessToken(scaResponseTO.getBearerToken().getAccess_token());
                GlobalScaResponseTO executionResponse = executeBusinessObject(businessObject);

                if (executionResponse == null) {
                    executionResponse = scaResponseTO;
                }

                executionResponse.setBearerToken(scaResponseTO.getBearerToken());
                executionResponse.setScaStatus(scaResponseTO.getScaStatus());

                aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(executionResponse));

                String scaStatusName = scaResponseTO.getScaStatus().name();
                log.info("SCA status is: {}", scaStatusName);

                return SpiResponse.<SpiPsuAuthorisationResponse>builder().payload(new SpiPsuAuthorisationResponse(true, SpiAuthorisationStatus.SUCCESS)).build();

            } catch (FeignException feignException) {
                String devMessage = feignExceptionReader.getErrorMessage(feignException);
                log.info("Processing of successful authorisation failed: devMessage '{}'", devMessage);
                return SpiResponse.<SpiPsuAuthorisationResponse>builder()
                               .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.FORMAT_ERROR))
                               .build();
            }
        }

        log.info("Authorising user with login: {}", psuLoginData.getPsuId());

        SpiResponse<SpiPsuAuthorisationResponse> authorisationResponse =
                authorisationService.authorisePsuInternal(getBusinessObjectId(businessObject),
                                                          authorisationId, getOpType(), scaResponseTO, aspspConsentDataProvider);

        if (isFirstInitiationOfMultilevelSca(businessObject, scaResponseTO)) {
            updateStatusInCms(getBusinessObjectId(businessObject), aspspConsentDataProvider);
        }

        return authorisationResponse;
    }

    protected SpiResponse<SpiPsuAuthorisationResponse> resolveErrorResponse(T businessObject, FeignException feignException) {
        String devMessage = feignExceptionReader.getErrorMessage(feignException);
        log.info("Initiate business object error: business object ID: {}, devMessage: {}", getBusinessObjectId(businessObject), devMessage);
        return SpiResponse.<SpiPsuAuthorisationResponse>builder()
                       .payload(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.FAILURE))
                       .build();
    }

    /**
     * This call must follow an init business object request, therefore we are expecting the
     * {@link AspspConsentData} object to contain a {@link GlobalScaResponseTO}
     * response.
     */
    public SpiResponse<SpiAvailableScaMethodsResponse> requestAvailableScaMethods(@NotNull SpiContextData contextData,
                                                                                  T businessObject,
                                                                                  @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        try {
            GlobalScaResponseTO sca = getScaObjectResponse(aspspConsentDataProvider, true);

            if (validateStatuses(businessObject, sca)) {
                return SpiResponse.<SpiAvailableScaMethodsResponse>builder()
                               .payload(new SpiAvailableScaMethodsResponse(Collections.emptyList()))
                               .build();
            }

            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            if (sca.getScaStatus() == EXEMPTED) {
                return SpiResponse.<SpiAvailableScaMethodsResponse>builder()
                               .payload(new SpiAvailableScaMethodsResponse(true, Collections.emptyList()))
                               .build();
            }

            ResponseEntity<GlobalScaResponseTO> availableMethodsResponse = redirectScaRestClient.getSCA(sca.getAuthorisationId());

            List<ScaUserDataTO> scaMethods = availableMethodsResponse != null ?
                                                     Optional.ofNullable(availableMethodsResponse.getBody())
                                                             .map(GlobalScaResponseTO::getScaMethods)
                                                             .orElse(Collections.emptyList()) : Collections.emptyList();

            if (!scaMethods.isEmpty()) {
                List<AuthenticationObject> authenticationObjects = scaMethodConverter.toAuthenticationObjectList(scaMethods);

                return SpiResponse.<SpiAvailableScaMethodsResponse>builder()
                               .payload(new SpiAvailableScaMethodsResponse(authenticationObjects))
                               .build();
            }

        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            log.error("Request available SCA methods failed: business object ID: {}, devMessage: {}", getBusinessObjectId(businessObject), devMessage);
            return SpiResponse.<SpiAvailableScaMethodsResponse>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.FORMAT_ERROR_SCA_METHODS))
                           .build();
        }

        return SpiResponse.<SpiAvailableScaMethodsResponse>builder()
                       .error(new TppMessage(MessageErrorCode.SCA_METHOD_UNKNOWN_PROCESS_MISMATCH))
                       .build();
    }

    protected Optional<List<ScaUserDataTO>> getScaMethods(GlobalScaResponseTO sca) {
        return Optional.ofNullable(sca.getScaMethods());
    }

    public @NotNull SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(@NotNull SpiContextData contextData,
                                                                                     @NotNull String authenticationMethodId,
                                                                                     @NotNull T businessObject,
                                                                                     @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        GlobalScaResponseTO sca = getScaObjectResponse(aspspConsentDataProvider, true);
        if (EnumSet.of(PSUIDENTIFIED, PSUAUTHENTICATED).contains(sca.getScaStatus())) {
            try {
                authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

                ResponseEntity<GlobalScaResponseTO> selectMethodResponse = getSelectMethodResponse(authenticationMethodId, sca);
                GlobalScaResponseTO authCodeResponse = selectMethodResponse.getBody();
                if (authCodeResponse != null && authCodeResponse.getBearerToken() == null) {
                    authCodeResponse.setBearerToken(sca.getBearerToken());
                }
                return authorisationService.returnScaMethodSelection(aspspConsentDataProvider, authCodeResponse, authenticationMethodId);
            } catch (FeignException feignException) {
                String devMessage = feignExceptionReader.getErrorMessage(feignException);
                log.error("Request authorisation code failed: business object ID: {}, devMessage: {}", getBusinessObjectId(businessObject), devMessage);
                TppMessage errorMessage = new TppMessage(getMessageErrorCodeByStatus(feignException.status()));
                return SpiResponse.<SpiAuthorizationCodeResult>builder()
                               .error(errorMessage)
                               .build();
            } finally {
                authRequestInterceptor.setAccessToken(null);
            }
        } else {
            return authorisationService.getResponseIfScaSelected(aspspConsentDataProvider, sca, authenticationMethodId);
        }
    }

    public @NotNull SpiResponse<SpiAuthorisationDecoupledScaResponse> startScaDecoupled(@NotNull SpiContextData contextData,
                                                                                        @NotNull String authorisationId,
                                                                                        @Nullable String authenticationMethodId,
                                                                                        @NotNull T businessObject,
                                                                                        @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        if (authenticationMethodId == null) {
            return SpiResponse.<SpiAuthorisationDecoupledScaResponse>builder()
                           .error(new TppMessage(MessageErrorCode.SERVICE_NOT_SUPPORTED))
                           .build();
        }

        SpiResponse<SpiAuthorizationCodeResult> response = requestAuthorisationCode(contextData, authenticationMethodId,
                                                                                    businessObject, aspspConsentDataProvider);

        if (response.hasError()) {
            return SpiResponse.<SpiAuthorisationDecoupledScaResponse>builder()
                           .error(response.getErrors())
                           .build();
        }

        String psuMessage = generatePsuMessage(contextData, authorisationId, aspspConsentDataProvider, response);
        return SpiResponse.<SpiAuthorisationDecoupledScaResponse>builder()
                       .payload(new SpiAuthorisationDecoupledScaResponse(response.getPayload().getScaStatus(), psuMessage))
                       .build();
    }

    public SpiResponse<SpiScaStatusResponse> getScaStatus(@NotNull ScaStatus scaStatus, @NotNull SpiContextData contextData,
                                                          @NotNull String authorisationId, @NotNull T businessObject,
                                                          @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        SpiScaStatusResponse payload;
        try {
            GlobalScaResponseTO scaResponseTO = getScaObjectResponse(aspspConsentDataProvider, false);
            if (scaResponseTO != null && scaResponseTO.getBearerToken() != null) {
                authRequestInterceptor.setAccessToken(scaResponseTO.getBearerToken().getAccess_token());
                ResponseEntity<GlobalScaResponseTO> responseEntity = redirectScaRestClient.getSCA(authorisationId);
                payload = spiScaStatusResponseMapper.toSpiScaStatusResponse(responseEntity.getBody());
            } else {
                log.info("Request ScaStatus from Bank failed: business object ID: {}", getBusinessObjectId(businessObject));
                payload = new SpiScaStatusResponse(scaStatus, false, PSU_MESSAGE);
            }
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            log.info("Request ScaStatus from Bank failed: business object ID: {}, devMessage: {}", getBusinessObjectId(businessObject), devMessage);
            payload = new SpiScaStatusResponse(scaStatus, false, PSU_MESSAGE);
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
        return SpiResponse.<SpiScaStatusResponse>builder()
                       .payload(new SpiScaStatusResponse(scaStatus, false, PSU_MESSAGE))
                       .payload(payload)
                       .build();
    }

    private SpiResponse<SpiPsuAuthorisationResponse> handleLoginFailureError(T businessObject,
                                                                             @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider,
                                                                             FeignException feignException) {
        String devMessage = feignExceptionReader.getErrorMessage(feignException);
        log.info("Login to IDP in authorise PSU failed: business object ID: {}, devMessage: {}", getBusinessObjectId(businessObject), devMessage);

        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();
        LoginAttemptAspspConsentDataService loginAttemptAspspConsentDataService = consentDataService.getLoginAttemptAspspConsentDataService();
        LoginAttemptResponse loginAttemptResponse = loginAttemptAspspConsentDataService.response(aspspConsentData);
        if (loginAttemptResponse == null) {
            loginAttemptResponse = new LoginAttemptResponse();
        }

        int remainingLoginAttempts = loginAttemptAspspConsentDataService.getRemainingLoginAttempts(loginAttemptResponse.getLoginFailedCount());
        loginAttemptResponse.incrementLoginFailedCount();
        aspspConsentDataProvider.updateAspspConsentData(loginAttemptAspspConsentDataService.store(loginAttemptResponse));
        if (remainingLoginAttempts > 0) {
            devMessage = String.format(LOGIN_AMOUNT_ATTEMPTS_REMAINING_MESSAGE, remainingLoginAttempts);
            log.info(devMessage);
            return SpiResponse.<SpiPsuAuthorisationResponse>builder()
                           .payload(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.ATTEMPT_FAILURE))
                           .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PSU_CREDENTIALS_INVALID, devMessage))
                           .build();
        }
        return SpiResponse.<SpiPsuAuthorisationResponse>builder()
                       .payload(new SpiPsuAuthorisationResponse(false, SpiAuthorisationStatus.FAILURE))
                       .build();
    }

    private MessageErrorCode getMessageErrorCodeByStatus(int status) {
        if (status == 501) {
            return MessageErrorCode.SCA_METHOD_UNKNOWN;
        }
        if (Arrays.asList(400, 401, 403).contains(status)) {
            return MessageErrorCode.FORMAT_ERROR;
        }
        if (status == 404) {
            return MessageErrorCode.PSU_CREDENTIALS_INVALID;
        }
        return MessageErrorCode.INTERNAL_SERVER_ERROR;
    }

}
