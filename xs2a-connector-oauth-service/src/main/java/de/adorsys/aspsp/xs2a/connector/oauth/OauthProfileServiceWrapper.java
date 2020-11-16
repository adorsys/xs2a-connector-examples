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

package de.adorsys.aspsp.xs2a.connector.oauth;

import de.adorsys.psd2.aspsp.profile.domain.AspspSettings;
import de.adorsys.psd2.aspsp.profile.domain.ais.AisAspspProfileSetting;
import de.adorsys.psd2.aspsp.profile.domain.ais.AisRedirectLinkSetting;
import de.adorsys.psd2.aspsp.profile.domain.common.CommonAspspProfileSetting;
import de.adorsys.psd2.aspsp.profile.domain.pis.PisAspspProfileSetting;
import de.adorsys.psd2.aspsp.profile.domain.pis.PisRedirectLinkSetting;
import de.adorsys.psd2.aspsp.profile.service.AspspProfileService;
import de.adorsys.psd2.xs2a.core.profile.ScaApproach;
import de.adorsys.psd2.xs2a.core.profile.ScaRedirectFlow;
import de.adorsys.psd2.xs2a.service.RequestProviderService;
import de.adorsys.psd2.xs2a.service.discovery.ServiceTypeDiscoveryService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ASPSP profile wrapper, responsible for substituting real scaRedirectFlow value and modifying redirect and oauth links from profile.
 * Needed for dynamic resolution of scaRedirectFlow without changing the profile itself.
 */
@Primary
@Service
public class OauthProfileServiceWrapper implements AspspProfileService {
    private final AspspProfileService aspspProfileService;
    private final OauthDataHolder oauthDataHolder;
    private final ServiceTypeDiscoveryService serviceTypeDiscoveryService;
    private final RequestProviderService requestProviderService;
    private final OAuthConfiguration oAuthConfiguration;

    public OauthProfileServiceWrapper(AspspProfileService aspspProfileService,
                                      OauthDataHolder oauthDataHolder,
                                      ServiceTypeDiscoveryService serviceTypeDiscoveryService,
                                      RequestProviderService requestProviderService,
                                      OAuthConfiguration oAuthConfiguration) {
        this.aspspProfileService = aspspProfileService;
        this.oauthDataHolder = oauthDataHolder;
        this.serviceTypeDiscoveryService = serviceTypeDiscoveryService;
        this.requestProviderService = requestProviderService;
        this.oAuthConfiguration = oAuthConfiguration;
    }

    @Override
    public AspspSettings getAspspSettings(String instanceId) {
        AspspSettings profileSettings = aspspProfileService.getAspspSettings(instanceId);
        ScaRedirectFlow scaRedirectFlow = profileSettings.getCommon().getScaRedirectFlow();

        CommonAspspProfileSetting existingCommonSetting = profileSettings.getCommon();
        if (scaRedirectFlow == ScaRedirectFlow.OAUTH) {
            String customOauthLink = existingCommonSetting.getOauthConfigurationUrl() + oAuthConfiguration.getIntegratedOauthSuffix(serviceTypeDiscoveryService.getServiceType());
            CommonAspspProfileSetting customCommonSettings = buildCustomCommonSetting(existingCommonSetting, ScaRedirectFlow.OAUTH, customOauthLink);
            return new AspspSettings(profileSettings.getAis(), profileSettings.getPis(), profileSettings.getPiis(), profileSettings.getSb(), customCommonSettings);
        } else if (scaRedirectFlow == ScaRedirectFlow.OAUTH_PRE_STEP) {
            CommonAspspProfileSetting customCommonSetting = buildCustomCommonSetting(existingCommonSetting,
                                                                                     ScaRedirectFlow.OAUTH_PRE_STEP,
                                                                                     existingCommonSetting.getOauthConfigurationUrl());
            String aisSuffixWithToken = oAuthConfiguration.getAisPreStepOauthSuffix() + StringUtils.defaultString(oauthDataHolder.getToken());
            AisAspspProfileSetting customAisSetting = buildCustomAisAspspProfileSetting(profileSettings.getAis(), aisSuffixWithToken);
            String pisSuffixWithToken = oAuthConfiguration.getPisPreStepOauthSuffix() + StringUtils.defaultString(oauthDataHolder.getToken());
            PisAspspProfileSetting customPisSetting = buildCustomPisAspspProfileSetting(profileSettings.getPis(), pisSuffixWithToken);
            return new AspspSettings(customAisSetting, customPisSetting, profileSettings.getPiis(), profileSettings.getSb(), customCommonSetting);
        }

        return profileSettings;
    }

    @Override
    public List<ScaApproach> getScaApproaches(String instanceId) {
        return aspspProfileService.getScaApproaches(instanceId);
    }

    /**
     * Reads list of sca approaches from ASPSP profile service
     *
     * @return List of Available SCA approaches for tpp
     */
    public List<ScaApproach> getScaApproaches() {
        return aspspProfileService.getScaApproaches(requestProviderService.getInstanceId());
    }

    /**
     * Reads the variant of redirect approach to be used.
     *
     * @return the variant of redirect approach to be used.
     */
    public ScaRedirectFlow getScaRedirectFlow() {
        return aspspProfileService.getAspspSettings(requestProviderService.getInstanceId())
                       .getCommon()
                       .getScaRedirectFlow();
    }

    @Override
    public boolean isMultitenancyEnabled() {
        return aspspProfileService.isMultitenancyEnabled();
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
                                             existingSetting.isCheckTppRolesFromCertificateSupported(),
                                             existingSetting.getAspspNotificationsSupported(),
                                             existingSetting.isAuthorisationConfirmationRequestMandated(),
                                             existingSetting.isAuthorisationConfirmationCheckByXs2a(),
                                             existingSetting.isCheckUriComplianceToDomainSupported(),
                                             existingSetting.getTppUriComplianceResponse());
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
        return new PisAspspProfileSetting(existingSetting.getSupportedPaymentTypeAndProductMatrix(), existingSetting.getMaxTransactionValidityDays(), existingSetting.getNotConfirmedPaymentExpirationTimeMs(), existingSetting.isPaymentCancellationAuthorisationMandated(), customRedirectLinkSetting, existingSetting.getCountryValidationSupported(), existingSetting.getSupportedTransactionStatusFormats());
    }
}
