package de.adorsys.aspsp.xs2a.spi.impl;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.adorsys.aspsp.xs2a.spi.converter.ScaMethodConverter;
import de.adorsys.ledgers.LedgersRestClient;
import de.adorsys.ledgers.domain.SCAValidationRequest;
import de.adorsys.ledgers.domain.sca.AuthCodeDataTO;
import de.adorsys.ledgers.domain.sca.SCAGenerationResponse;
import de.adorsys.ledgers.domain.sca.SCAMethodTO;
import de.adorsys.ledgers.domain.um.BearerTokenTO;
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
    private static final String TEST_ASPSP_DATA = "Test aspsp data";
    private static final String TEST_MESSAGE = "Test message";
    private final LedgersRestClient ledgersRestClient;
    private final ScaMethodConverter scaMethodConverter;

    public GeneralAuthorisationService(LedgersRestClient ledgersRestClient, ScaMethodConverter scaMethodConverter) {
        this.ledgersRestClient = ledgersRestClient;
        this.scaMethodConverter = scaMethodConverter;
    }

    public SpiResponse<SpiAuthorisationStatus> authorisePsu(@NotNull SpiPsuData spiPsuData, String pin, AspspConsentData aspspConsentData) {
        try {
            String login = spiPsuData.getPsuId();
            logger.info("Authorise user with login={} and password={}", login, StringUtils.repeat("*", pin.length()));
//            boolean isAuthorised = ledgersRestClient.authorise(login, pin);
            BearerTokenTO bearerToken = ledgersRestClient.authorise(login, pin, "CUSTOMER");
            SpiAuthorisationStatus status = bearerToken!=null
                                                    ? SpiAuthorisationStatus.SUCCESS
                                                    : SpiAuthorisationStatus.FAILURE;
            logger.info("Authorisation result is {}", status);
            return new SpiResponse<>(status, TokenUtils.store(bearerToken.getAccess_token(), aspspConsentData));
        } catch (FeignException e) {
            return SpiResponse.<SpiAuthorisationStatus>builder()
                           .aspspConsentData(aspspConsentData.respondWith(TEST_ASPSP_DATA.getBytes()))            // added for test purposes TODO remove if some requirements will be received https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/394
                           .fail(getSpiFailureResponse(e));
		}
    }

    public SpiResponse<List<SpiAuthenticationObject>> requestAvailableScaMethods(@NotNull SpiPsuData psuData, AspspConsentData aspspConsentData) {
        try {
            String userLogin = psuData.getPsuId();
            logger.info("Retrieving sca methods for user {}", userLogin);
            List<SCAMethodTO> scaMethods = ledgersRestClient.getUserScaMethods(TokenUtils.read(aspspConsentData),userLogin);
            logger.debug("These are sca methods that were found {}", scaMethods);

            List<SpiAuthenticationObject> authenticationObjects = scaMethodConverter.toSpiAuthenticationObjectList(scaMethods);
            return SpiResponse.<List<SpiAuthenticationObject>>builder()
                           .aspspConsentData(aspspConsentData)
                           .payload(authenticationObjects)
                           .success();
        } catch (FeignException e) {
            return SpiResponse.<List<SpiAuthenticationObject>>builder()
                           .aspspConsentData(aspspConsentData)
                           .fail(getSpiFailureResponse(e));
        }
    }

    public SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(@NotNull SpiPsuData psuData, @NotNull String authenticationMethodId, @NotNull String businessObjId, @NotNull String businessObjectAsString, @NotNull AspspConsentData aspspConsentData) {
        try {
            String userLogin = psuData.getPsuId();
            AuthCodeDataTO data = new AuthCodeDataTO(userLogin, authenticationMethodId, businessObjId, businessObjectAsString, null, -1);
            logger.info("Request to generate SCA {}", data);

            SCAGenerationResponse response = ledgersRestClient.generate(TokenUtils.read(aspspConsentData),data);
            logger.info("SCA was send, operationId is {}", response.getOpId());

            return SpiResponse.<SpiAuthorizationCodeResult>builder()
                           .aspspConsentData(aspspConsentData)
                           .success();
        } catch (FeignException e) {
            return SpiResponse.<SpiAuthorizationCodeResult>builder()
                           .aspspConsentData(aspspConsentData)
                           .fail(getSpiFailureResponse(e));
        }
    }

    public @NotNull SpiResponse<SpiResponse.VoidResponse> verifyScaAuthorisation(@NotNull SpiScaConfirmation spiScaConfirmation, @NotNull String businessObjAsString, @NotNull AspspConsentData aspspConsentData) {
        logger.info("Verifying SCA code");
        try {
            SCAValidationRequest validationRequest = new SCAValidationRequest(businessObjAsString, spiScaConfirmation.getTanNumber());//TODO fix this! it is not correct!
            BearerTokenTO bearerToken = ledgersRestClient.validate(TokenUtils.read(aspspConsentData),spiScaConfirmation.getPaymentId(), validationRequest);
            logger.info("Validation result is {}", bearerToken!=null);
            if (bearerToken!=null) {
                return SpiResponse.<SpiResponse.VoidResponse>builder()
                               .payload(SpiResponse.voidResponse())
                               .aspspConsentData(aspspConsentData.respondWith(TEST_ASPSP_DATA.getBytes()))            // added for test purposes TODO remove if some requirements will be received https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/394
                               .message(Collections.singletonList(TEST_MESSAGE))                                      // added for test purposes TODO remove if some requirements will be received https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/394
                               .success();
            }
            throw FeignException.errorStatus("Request failed, Response was 200, but body was empty!", Response.builder().status(400).build());
        } catch (FeignException e) {
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .aspspConsentData(aspspConsentData.respondWith(TEST_ASPSP_DATA.getBytes()))            // added for test purposes TODO remove if some requirements will be received https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/394
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
