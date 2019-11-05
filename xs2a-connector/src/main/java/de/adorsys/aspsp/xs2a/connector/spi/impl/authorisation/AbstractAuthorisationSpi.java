package de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation;

import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionHandler;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.ledgers.middleware.api.domain.sca.OpTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAConsentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationDecoupledScaResponse;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.*;

import static de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO.*;
import static de.adorsys.psd2.xs2a.core.error.MessageErrorCode.*;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractAuthorisationSpi<T, R extends SCAResponseTO> {
    private static final String DECOUPLED_PSU_MESSAGE = "Please check your app to continue...";

    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final GeneralAuthorisationService authorisationService;
    private final ScaMethodConverter scaMethodConverter;
    private final FeignExceptionReader feignExceptionReader;

    public SpiResponse<SpiAuthorisationStatus> authorisePsu(@NotNull SpiContextData contextData,
                                                            @NotNull SpiPsuData psuLoginData, String password, T businessObject,
                                                            @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        R originalResponse;
        try {
            originalResponse = getSCAConsentResponse(aspspConsentDataProvider, false);
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            log.error("Read aspspConsentData in authorise PSU failed: consent ID {}, devMessage {}", getBusinessObjectId(businessObject), devMessage);
            return SpiResponse.<SpiAuthorisationStatus>builder()
                           .error(new TppMessage(PSU_CREDENTIALS_INVALID))
                           .build();
        }

        SpiResponse<SpiAuthorisationStatus> authorisePsu = authorisationService.authorisePsuForConsent(
                psuLoginData, password, getBusinessObjectId(businessObject), originalResponse, getOtpType(), aspspConsentDataProvider);

        if (!authorisePsu.isSuccessful()) {
            return SpiResponse.<SpiAuthorisationStatus>builder()
                           .payload(SpiAuthorisationStatus.FAILURE)
                           .build();
        }

        R scaBusinessObjectResponse;

        try {
            scaBusinessObjectResponse = mapToScaResponse(businessObject, aspspConsentDataProvider.loadAspspConsentData(), originalResponse);
        } catch (IOException e) {
            return SpiResponse.<SpiAuthorisationStatus>builder()
                           .error(new TppMessage(FORMAT_ERROR_RESPONSE_TYPE))
                           .build();
        }

        return onSuccessfulAuthorisation(businessObject, aspspConsentDataProvider, authorisePsu, scaBusinessObjectResponse);
    }

    /**
     * This call must follow an init consent request, therefore we are expecting the
     * {@link AspspConsentData} object to contain a {@link SCAConsentResponseTO}
     * response.
     */
    public SpiResponse<List<SpiAuthenticationObject>> requestAvailableScaMethods(@NotNull SpiContextData contextData,
                                                                                 T businessObject,
                                                                                 @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        try {
            R sca = getSCAConsentResponse(aspspConsentDataProvider, true);

            if (validateStatuses(businessObject, sca)) {
                return SpiResponse.<List<SpiAuthenticationObject>>builder()
                               .payload(Collections.emptyList())
                               .build();
            }

            Optional<List<ScaUserDataTO>> scaMethods = getScaMethods(sca);
            if (scaMethods.isPresent()) {
                // Validate the access token
                BearerTokenTO bearerTokenTO = authorisationService.validateToken(sca.getBearerToken().getAccess_token());
                sca.setBearerToken(bearerTokenTO);

                // Return contained sca methods.
                List<SpiAuthenticationObject> authenticationObjects = scaMethodConverter.toSpiAuthenticationObjectList(scaMethods.get());
                aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(sca));
                return SpiResponse.<List<SpiAuthenticationObject>>builder()
                               .payload(authenticationObjects)
                               .build();
            } else {
                return getForZeroScaMethods(sca.getScaStatus());
            }
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            log.error("Read available sca methods failed: consent ID {}, devMessage {}", getBusinessObjectId(businessObject), devMessage);
            return SpiResponse.<List<SpiAuthenticationObject>>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, FORMAT_ERROR_SCA_METHODS))
                           .build();
        }
    }

    SpiResponse<List<SpiAuthenticationObject>> getForZeroScaMethods(ScaStatusTO status) {
        log.error("Process mismatch. Current SCA Status is {}", status);
        return SpiResponse.<List<SpiAuthenticationObject>>builder()
                       .error(new TppMessage(SCA_METHOD_UNKNOWN_PROCESS_MISMATCH))
                       .build();
    }

    protected Optional<List<ScaUserDataTO>> getScaMethods(R sca) {
        return Optional.ofNullable(sca.getScaMethods());
    }

    public @NotNull SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(@NotNull SpiContextData contextData,
                                                                                     @NotNull String authenticationMethodId,
                                                                                     @NotNull T businessObject,
                                                                                     @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        R sca = getSCAConsentResponse(aspspConsentDataProvider, true);
        if (EnumSet.of(PSUIDENTIFIED, PSUAUTHENTICATED).contains(sca.getScaStatus())) {
            try {
                authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

                ResponseEntity<R> selectMethodResponse = getSelectMethodResponse(authenticationMethodId, sca);
                R authCodeResponse = selectMethodResponse.getBody();
                if (authCodeResponse != null && authCodeResponse.getBearerToken() == null) {
                    // TODO: hack. Core banking is supposed to always return a token. @fpo
                    authCodeResponse.setBearerToken(sca.getBearerToken());
                }
                return authorisationService.returnScaMethodSelection(aspspConsentDataProvider, authCodeResponse);
            } catch (FeignException feignException) {
                String devMessage = feignExceptionReader.getErrorMessage(feignException);
                log.error("Request authorisation code failed: consent ID {}, devMessage {}", getBusinessObjectId(businessObject), devMessage);
                TppMessage errorMessage = new TppMessage(getMessageErrorCodeByStatus(feignException.status()));
                return SpiResponse.<SpiAuthorizationCodeResult>builder()
                               .error(errorMessage)
                               .build();
            } finally {
                authRequestInterceptor.setAccessToken(null);
            }
        } else {
            return authorisationService.getResponseIfScaSelected(aspspConsentDataProvider, sca);
        }
    }

    public @NotNull SpiResponse<SpiAuthorisationDecoupledScaResponse> startScaDecoupled(@NotNull SpiContextData contextData,
                                                                                        @NotNull String authorisationId,
                                                                                        @Nullable String authenticationMethodId,
                                                                                        @NotNull T businessObject,
                                                                                        @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        if (authenticationMethodId == null) {
            return SpiResponse.<SpiAuthorisationDecoupledScaResponse>builder()
                           .error(new TppMessage(SERVICE_NOT_SUPPORTED))
                           .build();
        }

        SpiResponse<SpiAuthorizationCodeResult> response = requestAuthorisationCode(contextData, authenticationMethodId,
                                                                                    businessObject, aspspConsentDataProvider);

        if (response.hasError()) {
            return SpiResponse.<SpiAuthorisationDecoupledScaResponse>builder().error(response.getErrors()).build();
        }

        String psuMessage = generatePsuMessage(contextData, authorisationId, aspspConsentDataProvider, response);
        return SpiResponse.<SpiAuthorisationDecoupledScaResponse>builder().payload(new SpiAuthorisationDecoupledScaResponse(psuMessage)).build();

    }

    protected abstract ResponseEntity<R> getSelectMethodResponse(@NotNull String authenticationMethodId, R sca);

    protected abstract R getSCAConsentResponse(@NotNull SpiAspspConsentDataProvider aspspConsentDataProvider, boolean checkCredentials);

    protected abstract String getBusinessObjectId(T businessObject);

    protected abstract OpTypeTO getOtpType();

    protected abstract TppMessage getAuthorisePsuFailureMessage(T businessObject);

    protected abstract SCAResponseTO initiateBusinessObject(T businessObject, byte[] aspspConsentData);

    protected abstract R mapToScaResponse(T businessObject, byte[] aspspConsentData, R originalResponse) throws IOException;

    protected String generatePsuMessage(@NotNull SpiContextData contextData, @NotNull String authorisationId,
                                        @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider,
                                        SpiResponse<SpiAuthorizationCodeResult> response) {
        return DECOUPLED_PSU_MESSAGE;
    }


    protected boolean validateStatuses(T businessObject, R sca) {
        return false;
    }

    protected SpiResponse<SpiAuthorisationStatus> onSuccessfulAuthorisation(T businessObject,
                                                                            @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider,
                                                                            SpiResponse<SpiAuthorisationStatus> authorisePsu,
                                                                            R scaBusinessObjectResponse) {
        if (EnumSet.of(EXEMPTED, PSUAUTHENTICATED, PSUIDENTIFIED).contains(scaBusinessObjectResponse.getScaStatus())) {
            SCAResponseTO aisConsentResponse;
            try {
                aisConsentResponse = initiateBusinessObject(businessObject, aspspConsentDataProvider.loadAspspConsentData());
            } catch (FeignException feignException) {
                return SpiResponse.<SpiAuthorisationStatus>builder()
                               .error(FeignExceptionHandler.getFailureMessage(feignException, PSU_CREDENTIALS_INVALID))
                               .build();
            }

            if (aisConsentResponse == null) {
                return SpiResponse.<SpiAuthorisationStatus>builder()
                               .error(getAuthorisePsuFailureMessage(businessObject))
                               .build();
            }
            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(aisConsentResponse));

            String scaStatusName = aisConsentResponse.getScaStatus().name();
            log.info("SCA status is: {}", scaStatusName);
        }

        return SpiResponse.<SpiAuthorisationStatus>builder()
                       .payload(authorisePsu.getPayload())
                       .build();
    }

    private MessageErrorCode getMessageErrorCodeByStatus(int status) {
        if (status == 501) {
            return SCA_METHOD_UNKNOWN;
        }
        if (Arrays.asList(400, 401, 403).contains(status)) {
            return FORMAT_ERROR;
        }
        if (status == 404) {
            return PSU_CREDENTIALS_INVALID;
        }
        return INTERNAL_SERVER_ERROR;
    }
}
