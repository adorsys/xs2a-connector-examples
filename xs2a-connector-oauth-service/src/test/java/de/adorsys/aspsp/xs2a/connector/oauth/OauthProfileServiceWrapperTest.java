/*
 * Copyright 2018-2020 adorsys GmbH & Co KG
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
import de.adorsys.psd2.aspsp.profile.service.AspspProfileService;
import de.adorsys.psd2.xs2a.core.mapper.ServiceType;
import de.adorsys.psd2.xs2a.core.profile.ScaApproach;
import de.adorsys.psd2.xs2a.core.profile.ScaRedirectFlow;
import de.adorsys.psd2.xs2a.service.RequestProviderService;
import de.adorsys.psd2.xs2a.service.discovery.ServiceTypeDiscoveryService;
import de.adorsys.xs2a.reader.JsonReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OauthProfileServiceWrapperTest {

    private static final String INSTANCE_ID = "test-instance-id";
    private static final String ACCESS_TOKEN = "token";
    private static final String DEFAULT_OAUTH_CONFIGURATION_URL = "oauth-configuration-url";
    private static final String DEFAULT_AIS_OBA_LINK = "ais-oba-link";
    private static final String DEFAULT_PIS_OBA_LINK = "pis-oba-link";
    private static final String DEFAULT_PIS_CANCELLATION_OBA_LINK = "pis-cancellation-oba-link";
    private static final String DEFAULT_PIIS_OBA_LINK = "piis-oba-link";

    @Mock
    private AspspProfileService aspspProfileService;
    @Mock
    private OauthDataHolder oauthDataHolder;
    @Mock
    private ServiceTypeDiscoveryService serviceTypeDiscoveryService;
    @Mock
    private RequestProviderService requestProviderService;
    private OAuthConfiguration configuration;

    private final JsonReader jsonReader = new JsonReader();
    private OauthProfileServiceWrapper wrapper;

    @BeforeEach
    void setUp() {
        configuration = jsonReader.getObjectFromFile("json/oauth-configuration.json", OAuthConfiguration.class);

        wrapper = new OauthProfileServiceWrapper(aspspProfileService, oauthDataHolder,
                                                 serviceTypeDiscoveryService, requestProviderService,
                                                 configuration);
    }

    @Test
    void getAspspSettings_redirect() {
        AspspSettings aspspSettings = jsonReader.getObjectFromFile("json/aspsp-settings-redirect.json", AspspSettings.class);
        assertEquals(ScaRedirectFlow.REDIRECT, aspspSettings.getCommon().getScaRedirectFlow());
        assertEquals(DEFAULT_OAUTH_CONFIGURATION_URL, aspspSettings.getCommon().getOauthConfigurationUrl());
        validateRedirectDefaultUrls(aspspSettings);

        when(aspspProfileService.getAspspSettings(INSTANCE_ID)).thenReturn(aspspSettings);

        AspspSettings actual = wrapper.getAspspSettings(INSTANCE_ID);

        assertEquals(aspspSettings, actual);
    }

    @Test
    void getAspspSettings_oauth() {
        AspspSettings aspspSettings = jsonReader.getObjectFromFile("json/aspsp-settings-oauth.json", AspspSettings.class);
        assertEquals(ScaRedirectFlow.OAUTH, aspspSettings.getCommon().getScaRedirectFlow());
        assertEquals(DEFAULT_OAUTH_CONFIGURATION_URL, aspspSettings.getCommon().getOauthConfigurationUrl());
        validateRedirectDefaultUrls(aspspSettings);

        when(aspspProfileService.getAspspSettings(INSTANCE_ID)).thenReturn(aspspSettings);
        when(serviceTypeDiscoveryService.getServiceType()).thenReturn(ServiceType.AIS);

        AspspSettings actual = wrapper.getAspspSettings(INSTANCE_ID);

        assertNotEquals(aspspSettings, actual);
        assertEquals(ScaRedirectFlow.OAUTH, actual.getCommon().getScaRedirectFlow());
        assertEquals(DEFAULT_OAUTH_CONFIGURATION_URL + configuration.getAisIntegratedOauthSuffix(), actual.getCommon().getOauthConfigurationUrl());
        validateRedirectDefaultUrls(actual);
    }

    @Test
    void getAspspSettings_prestep() {
        AspspSettings aspspSettings = jsonReader.getObjectFromFile("json/aspsp-settings-prestep.json", AspspSettings.class);
        assertEquals(ScaRedirectFlow.OAUTH_PRE_STEP, aspspSettings.getCommon().getScaRedirectFlow());
        assertEquals(DEFAULT_OAUTH_CONFIGURATION_URL, aspspSettings.getCommon().getOauthConfigurationUrl());
        validateRedirectDefaultUrls(aspspSettings);

        when(aspspProfileService.getAspspSettings(INSTANCE_ID)).thenReturn(aspspSettings);
        when(oauthDataHolder.getToken()).thenReturn(ACCESS_TOKEN);

        AspspSettings actual = wrapper.getAspspSettings(INSTANCE_ID);

        assertNotEquals(aspspSettings, actual);
        assertEquals(ScaRedirectFlow.OAUTH_PRE_STEP, actual.getCommon().getScaRedirectFlow());
        assertEquals(DEFAULT_OAUTH_CONFIGURATION_URL, actual.getCommon().getOauthConfigurationUrl());
        assertEquals(DEFAULT_AIS_OBA_LINK + configuration.getAisPreStepOauthSuffix() + ACCESS_TOKEN,
                     actual.getAis().getRedirectLinkToOnlineBanking().getAisRedirectUrlToAspsp());
        assertEquals(DEFAULT_PIS_OBA_LINK + configuration.getPisPreStepOauthSuffix() + ACCESS_TOKEN,
                     actual.getPis().getRedirectLinkToOnlineBanking().getPisRedirectUrlToAspsp());
        assertEquals(DEFAULT_PIS_CANCELLATION_OBA_LINK + configuration.getPisPreStepOauthSuffix() + ACCESS_TOKEN,
                     actual.getPis().getRedirectLinkToOnlineBanking().getPisPaymentCancellationRedirectUrlToAspsp());
        assertEquals(DEFAULT_PIIS_OBA_LINK,
                     actual.getPiis().getRedirectLinkToOnlineBanking().getPiisRedirectUrlToAspsp());

    }

    @Test
    void getScaApproachesWithInstanceId() {
        when(aspspProfileService.getScaApproaches(INSTANCE_ID)).thenReturn(Collections.singletonList(ScaApproach.REDIRECT));

        List<ScaApproach> actual = wrapper.getScaApproaches(INSTANCE_ID);

        assertEquals(Collections.singletonList(ScaApproach.REDIRECT), actual);
        verify(requestProviderService, never()).getInstanceId();
        verify(aspspProfileService, times(1)).getScaApproaches(INSTANCE_ID);
    }

    @Test
    void getScaApproaches() {
        when(requestProviderService.getInstanceId()).thenReturn(INSTANCE_ID);
        when(aspspProfileService.getScaApproaches(INSTANCE_ID)).thenReturn(Collections.singletonList(ScaApproach.REDIRECT));

        List<ScaApproach> actual = wrapper.getScaApproaches();

        assertEquals(Collections.singletonList(ScaApproach.REDIRECT), actual);
        verify(requestProviderService, times(1)).getInstanceId();
        verify(aspspProfileService, times(1)).getScaApproaches(INSTANCE_ID);
    }

    @Test
    void getScaRedirectFlow() {
        AspspSettings aspspSettings = jsonReader.getObjectFromFile("json/aspsp-settings-oauth.json", AspspSettings.class);

        when(requestProviderService.getInstanceId()).thenReturn(INSTANCE_ID);
        when(aspspProfileService.getAspspSettings(INSTANCE_ID)).thenReturn(aspspSettings);

        ScaRedirectFlow actual = wrapper.getScaRedirectFlow();

        assertEquals(ScaRedirectFlow.OAUTH, actual);
        verify(requestProviderService, times(1)).getInstanceId();
        verify(aspspProfileService, times(1)).getAspspSettings(INSTANCE_ID);
    }

    @Test
    void isMultitenancyEnabled() {
        when(aspspProfileService.isMultitenancyEnabled()).thenReturn(true);
        assertTrue(wrapper.isMultitenancyEnabled());
        verify(aspspProfileService, times(1)).isMultitenancyEnabled();
    }

    private void validateRedirectDefaultUrls(AspspSettings aspspSettings) {
        assertEquals(DEFAULT_AIS_OBA_LINK,
                     aspspSettings.getAis().getRedirectLinkToOnlineBanking().getAisRedirectUrlToAspsp());
        assertEquals(DEFAULT_PIS_OBA_LINK,
                     aspspSettings.getPis().getRedirectLinkToOnlineBanking().getPisRedirectUrlToAspsp());
        assertEquals(DEFAULT_PIS_CANCELLATION_OBA_LINK,
                     aspspSettings.getPis().getRedirectLinkToOnlineBanking().getPisPaymentCancellationRedirectUrlToAspsp());
        assertEquals(DEFAULT_PIIS_OBA_LINK,
                     aspspSettings.getPiis().getRedirectLinkToOnlineBanking().getPiisRedirectUrlToAspsp());
    }
}