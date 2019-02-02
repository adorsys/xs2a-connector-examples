package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.ledgers.middleware.api.domain.sca.OpTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCALoginResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.middleware.api.domain.um.UserRoleTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.UserMgmtRestClient;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import feign.FeignException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class GeneralAuthorisationService {
    private static final Logger logger = LoggerFactory.getLogger(GeneralAuthorisationService.class);
    private final UserMgmtRestClient userMgmtRestClient;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService tokenService;

    public GeneralAuthorisationService(UserMgmtRestClient userMgmtRestClient, AuthRequestInterceptor authRequestInterceptor,
                                       AspspConsentDataService tokenService) {
        super();
        this.userMgmtRestClient = userMgmtRestClient;
        this.authRequestInterceptor = authRequestInterceptor;
        this.tokenService = tokenService;
    }

    /**
     * First authorization of the PSU.
     * <p>
     * The result of this authorization must contain an scaStatus with following options:
     * - {@link ScaStatusTO#EXEMPTED} : There is no sca needed. The user does not have any sca method anyway.
     * - {@link ScaStatusTO#SCAMETHODSELECTED} : The user has receive an authorization code and must enter it.
     * - {@link ScaStatusTO#PSUIDENTIFIED} : the user must select a authorization method to complete auth.
     * <p>
     * In all three cases, we store the response object for reuse in an {@link AspspConsentData} object.
     *
     * @param spiPsuData       identification data for the psu
     * @param pin              : pis of the psu
     * @param aspspConsentData : credential transport object.
     * @return : the authorisation status
     */
    public SpiResponse<SpiAuthorisationStatus> authorisePsu(@NotNull SpiPsuData spiPsuData, String pin, AspspConsentData aspspConsentData) {
        try {
            String login = spiPsuData.getPsuId();
            logger.info("Authorise user with login={} and password={}", login, StringUtils.repeat("*", 10));
            ResponseEntity<SCALoginResponseTO> response = userMgmtRestClient.authorise(login, pin, UserRoleTO.CUSTOMER);
            SpiAuthorisationStatus status = response != null && response.getBody() != null && response.getBody().getBearerToken() != null
                                                    ? SpiAuthorisationStatus.SUCCESS
                                                    : SpiAuthorisationStatus.FAILURE;
            logger.info("Authorisation result is {}", status);
            return new SpiResponse<>(status, tokenService.store(response.getBody(), aspspConsentData));
        } catch (FeignException e) {
            return SpiResponse.<SpiAuthorisationStatus>builder()
                           .fail(SpiFailureResponseHelper.getSpiFailureResponse(e, logger));
        }
    }

    public SpiResponse<SpiAuthorisationStatus> authorisePsuForConsent(@NotNull SpiPsuData spiPsuData, String pin, String consentId, String authorisationId, OpTypeTO opType, AspspConsentData aspspConsentData) {
        try {
            String login = spiPsuData.getPsuId();
            logger.info("Authorise user with login={} and password={}", login, StringUtils.repeat("*", 10));
            ResponseEntity<SCALoginResponseTO> response = userMgmtRestClient.authoriseForConsent(login, pin, consentId, authorisationId, opType);
            SpiAuthorisationStatus status = response != null && response.getBody() != null && response.getBody().getBearerToken() != null
                                                    ? SpiAuthorisationStatus.SUCCESS
                                                    : SpiAuthorisationStatus.FAILURE;
            logger.info("Authorisation result is {}", status);
            return new SpiResponse<>(status, tokenService.store(response.getBody(), aspspConsentData));
        } catch (FeignException e) {
            return SpiResponse.<SpiAuthorisationStatus>builder()
                           .aspspConsentData(aspspConsentData)
                           .fail(SpiFailureResponseHelper.getSpiFailureResponse(e, logger));
        }
    }

    public BearerTokenTO validateToken(String accessToken) {
        try {
            authRequestInterceptor.setAccessToken(accessToken);
            return userMgmtRestClient.validate(accessToken).getBody();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

}
