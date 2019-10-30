package de.adorsys.aspsp.xs2a.remote.connector.oauth;

import de.adorsys.psd2.aspsp.profile.domain.AspspSettings;
import de.adorsys.psd2.aspsp.profile.domain.ais.AisAspspProfileSetting;
import de.adorsys.psd2.aspsp.profile.domain.ais.AisRedirectLinkSetting;
import de.adorsys.psd2.aspsp.profile.domain.common.CommonAspspProfileSetting;
import de.adorsys.psd2.aspsp.profile.domain.pis.PisAspspProfileSetting;
import de.adorsys.psd2.aspsp.profile.domain.pis.PisRedirectLinkSetting;
import de.adorsys.psd2.aspsp.profile.service.AspspProfileService;
import de.adorsys.psd2.xs2a.core.profile.ScaApproach;
import de.adorsys.psd2.xs2a.core.profile.ScaRedirectFlow;
import de.adorsys.psd2.xs2a.service.discovery.ServiceTypeDiscoveryService;
import de.adorsys.psd2.xs2a.service.mapper.psd2.ServiceType;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Primary
@Service
@RequiredArgsConstructor
public class OauthProfileServiceWrapper implements AspspProfileService {
    private final AspspProfileService aspspProfileService;
    private final OauthDataHolder oauthDataHolder;
    private final ServiceTypeDiscoveryService serviceTypeDiscoveryService;

    @Value("${oauth.integrated.ais.suffix:?consentId={encrypted-consent-id}&redirectId={redirect-id}}")
    private String aisIntegratedOauthSuffix;
    @Value("${oauth.integrated.pis.suffix:?paymentId={encrypted-payment-id}&redirectId={redirect-id}}")
    private String pisIntegratedOauthSuffix;
    @Value("${oauth.pre-step.ais.suffix:&token=}")
    private String aisPreStepOauthSuffix;
    @Value("${oauth.pre-step.pis.suffix:&token=}")
    private String pisPreStepOauthSuffix;

    @Override
    public AspspSettings getAspspSettings() {
        AspspSettings profileSettings = aspspProfileService.getAspspSettings();
        OauthType oauthType = oauthDataHolder.getOauthType();

        if (oauthType == OauthType.INTEGRATED) {
            CommonAspspProfileSetting existingCommonSetting = profileSettings.getCommon();
            String existingOauthLink = existingCommonSetting.getOauthConfigurationUrl();
            String customOauthLink = existingOauthLink + buildOauthLinkSuffix();
            CommonAspspProfileSetting customCommonSettings = buildCustomCommonSetting(existingCommonSetting, ScaRedirectFlow.OAUTH, customOauthLink);
            return new AspspSettings(profileSettings.getAis(), profileSettings.getPis(), profileSettings.getPiis(), customCommonSettings);
        } else if (oauthType == OauthType.PRE_STEP) {
            CommonAspspProfileSetting existingCommonSetting = profileSettings.getCommon();
            CommonAspspProfileSetting customCommonSetting = buildCustomCommonSetting(existingCommonSetting,
                                                                                     ScaRedirectFlow.OAUTH_PRE_STEP,
                                                                                     existingCommonSetting.getOauthConfigurationUrl());
            String aisSuffixWithToken = aisPreStepOauthSuffix + StringUtils.defaultString(oauthDataHolder.getToken());
            AisAspspProfileSetting customAisSetting = buildCustomAisAspspProfileSetting(profileSettings.getAis(), aisSuffixWithToken);
            String pisSuffixWithToken = pisPreStepOauthSuffix + StringUtils.defaultString(oauthDataHolder.getToken());
            PisAspspProfileSetting customPisSetting = buildCustomPisAspspProfileSetting(profileSettings.getPis(), pisSuffixWithToken);
            return new AspspSettings(customAisSetting, customPisSetting, profileSettings.getPiis(), customCommonSetting);
        }

        return profileSettings;
    }

    @Override
    public List<ScaApproach> getScaApproaches() {
        return aspspProfileService.getScaApproaches();
    }

    private String buildOauthLinkSuffix() {
        ServiceType serviceType = serviceTypeDiscoveryService.getServiceType();

        if (serviceType == ServiceType.AIS) {
            return aisIntegratedOauthSuffix;
        } else if (serviceType == ServiceType.PIS) {
            return pisIntegratedOauthSuffix;
        }

        return "";
    }

    private CommonAspspProfileSetting buildCustomCommonSetting(CommonAspspProfileSetting existingSetting,
                                                               ScaRedirectFlow scaRedirectFlow,
                                                               String oauthConfigurationUrl) {
        return new CommonAspspProfileSetting(scaRedirectFlow,
                                             oauthConfigurationUrl,
                                             existingSetting.getStartAuthorisationMode(),
                                             existingSetting.isTppSignatureRequired(),
                                             existingSetting.isPsuInInitialRequestMandated(),
                                             existingSetting.getRedirectUrlExpirationTimeMs(),
                                             existingSetting.getAuthorisationExpirationTimeMs(),
                                             existingSetting.isForceXs2aBaseLinksUrl(),
                                             existingSetting.getXs2aBaseLinksUrl(),
                                             existingSetting.getSupportedAccountReferenceFields(),
                                             existingSetting.getMulticurrencyAccountLevelSupported(),
                                             existingSetting.isAisPisSessionsSupported(),
                                             existingSetting.isSigningBasketSupported());
    }

    private AisAspspProfileSetting buildCustomAisAspspProfileSetting(AisAspspProfileSetting existingSetting, String redirectUrlSuffix) {
        String customRedirectUrl = existingSetting.getRedirectLinkToOnlineBanking().getAisRedirectUrlToAspsp() + redirectUrlSuffix;
        return new AisAspspProfileSetting(existingSetting.getConsentTypes(), new AisRedirectLinkSetting(customRedirectUrl), existingSetting.getTransactionParameters(), existingSetting.getDeltaReportSettings(), existingSetting.getScaRequirementsForOneTimeConsents());
    }

    private PisAspspProfileSetting buildCustomPisAspspProfileSetting(PisAspspProfileSetting existingSetting, String redirectUrlSuffix) {
        String customInitiationRedirectUrl = existingSetting.getRedirectLinkToOnlineBanking().getPisRedirectUrlToAspsp() + redirectUrlSuffix;
        String customCancellationRedirectUrl = existingSetting.getRedirectLinkToOnlineBanking().getPisPaymentCancellationRedirectUrlToAspsp() + redirectUrlSuffix;

        PisRedirectLinkSetting redirectLinkToOnlineBanking = existingSetting.getRedirectLinkToOnlineBanking();
        PisRedirectLinkSetting customRedirectLinkSetting = new PisRedirectLinkSetting(customInitiationRedirectUrl, customCancellationRedirectUrl, redirectLinkToOnlineBanking.getPaymentCancellationRedirectUrlExpirationTimeMs());
        return new PisAspspProfileSetting(existingSetting.getSupportedPaymentTypeAndProductMatrix(), existingSetting.getMaxTransactionValidityDays(), existingSetting.getNotConfirmedPaymentExpirationTimeMs(), existingSetting.isPaymentCancellationAuthorisationMandated(), customRedirectLinkSetting, existingSetting.getCountryValidationSupported());
    }
}
