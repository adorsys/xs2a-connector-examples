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
import de.adorsys.psd2.aspsp.profile.service.AspspProfileService;
import de.adorsys.psd2.xs2a.core.mapper.ServiceType;
import de.adorsys.psd2.xs2a.core.profile.ScaApproach;
import de.adorsys.psd2.xs2a.service.discovery.ServiceTypeDiscoveryService;
import de.adorsys.xs2a.reader.JsonReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OauthProfileServiceWrapperTest {
    private static final String ASPSP_SETTINGS_JSON_PATH = "json/oauth/aspsp-settings.json";
    private static final String ASPSP_SETTINGS_INTEGRATED_AIS_JSON_PATH = "json/oauth/aspsp-settings-oauth-integrated-ais.json";
    private static final String ASPSP_SETTINGS_INTEGRATED_PIS_JSON_PATH = "json/oauth/aspsp-settings-oauth-integrated-pis.json";
    private static final String ASPSP_SETTINGS_INTEGRATED_JSON_PATH = "json/oauth/aspsp-settings-oauth-integrated.json";
    private static final String ASPSP_SETTINGS_PRESTEP_JSON_PATH = "json/oauth/aspsp-settings-oauth-pre-step.json";
    private static final String OAUTH_TOKEN_VALUE = "some-valid-token";
    private static final String INSTANCE_ID = "bank1";

    private static final String AIS_INTEGRATED_OAUTH_SUFFIX = "?consentId={encrypted-consent-id}&redirectId={redirect-id}";
    private static final String PIS_INTEGRATED_OAUTH_SUFFIX = "?paymentId={encrypted-payment-id}&redirectId={redirect-id}";
    private static final String AIS_PRESTEP_OAUTH_SUFFIX = "&token=";
    private static final String PIS_PRESTEP_OAUTH_SUFFIX = "&token=";

    @Mock
    private AspspProfileService aspspProfileService;
    @Mock
    private OauthDataHolder oauthDataHolder;
    @Mock
    private ServiceTypeDiscoveryService serviceTypeDiscoveryService;

    private OauthProfileServiceWrapper oauthProfileServiceWrapper;

    private JsonReader jsonReader = new JsonReader();
    private AspspSettings realAspspSettings = jsonReader.getObjectFromFile(ASPSP_SETTINGS_JSON_PATH, AspspSettings.class);

    @BeforeEach
    void setUp() {
        oauthProfileServiceWrapper = new OauthProfileServiceWrapper(aspspProfileService, oauthDataHolder, serviceTypeDiscoveryService, AIS_INTEGRATED_OAUTH_SUFFIX, PIS_INTEGRATED_OAUTH_SUFFIX, AIS_PRESTEP_OAUTH_SUFFIX, PIS_PRESTEP_OAUTH_SUFFIX);
    }

    @Test
    void getAspspSettings_withNullOauthType_shouldNotChangeProfile() {
        // When
        when(aspspProfileService.getAspspSettings(INSTANCE_ID)).thenReturn(realAspspSettings);

        AspspSettings aspspSettings = oauthProfileServiceWrapper.getAspspSettings(INSTANCE_ID);

        // Then
        assertEquals(realAspspSettings, aspspSettings);
    }

    @Test
    void getAspspSettings_withIntegratedOauthType_ais_shouldChangeFlowAndLinks() {
        // Given
        when(aspspProfileService.getAspspSettings(INSTANCE_ID)).thenReturn(realAspspSettings);

        AspspSettings modifiedSettings = jsonReader.getObjectFromFile(ASPSP_SETTINGS_INTEGRATED_AIS_JSON_PATH, AspspSettings.class);

        when(oauthDataHolder.getOauthType())
                .thenReturn(OauthType.INTEGRATED);
        when(serviceTypeDiscoveryService.getServiceType())
                .thenReturn(ServiceType.AIS);

        // When
        AspspSettings aspspSettings = oauthProfileServiceWrapper.getAspspSettings(INSTANCE_ID);

        // Then
        assertEquals(modifiedSettings, aspspSettings);
    }

    @Test
    void getAspspSettings_withIntegratedOauthType_pis_shouldChangeFlowAndLinks() {
        // Given
        when(aspspProfileService.getAspspSettings(INSTANCE_ID)).thenReturn(realAspspSettings);

        AspspSettings modifiedSettings = jsonReader.getObjectFromFile(ASPSP_SETTINGS_INTEGRATED_PIS_JSON_PATH, AspspSettings.class);

        when(oauthDataHolder.getOauthType())
                .thenReturn(OauthType.INTEGRATED);
        when(serviceTypeDiscoveryService.getServiceType())
                .thenReturn(ServiceType.PIS);

        // When
        AspspSettings aspspSettings = oauthProfileServiceWrapper.getAspspSettings(INSTANCE_ID);

        // Then
        assertEquals(modifiedSettings, aspspSettings);
    }

    @Test
    void getAspspSettings_withIntegratedOauthType_otherServiceType_shouldChangeFlow() {
        // Given
        when(aspspProfileService.getAspspSettings(INSTANCE_ID)).thenReturn(realAspspSettings);

        AspspSettings modifiedSettings = jsonReader.getObjectFromFile(ASPSP_SETTINGS_INTEGRATED_JSON_PATH, AspspSettings.class);

        when(oauthDataHolder.getOauthType())
                .thenReturn(OauthType.INTEGRATED);
        when(serviceTypeDiscoveryService.getServiceType())
                .thenReturn(ServiceType.PIIS);

        // When
        AspspSettings aspspSettings = oauthProfileServiceWrapper.getAspspSettings(INSTANCE_ID);

        // Then
        assertEquals(modifiedSettings, aspspSettings);
    }

    @Test
    void getAspspSettings_withPreStepOauthType_pis_shouldChangeFlowAndLinks() {
        // Given
        when(aspspProfileService.getAspspSettings(INSTANCE_ID)).thenReturn(realAspspSettings);

        AspspSettings modifiedSettings = jsonReader.getObjectFromFile(ASPSP_SETTINGS_PRESTEP_JSON_PATH, AspspSettings.class);

        when(oauthDataHolder.getOauthType())
                .thenReturn(OauthType.PRE_STEP);
        when(oauthDataHolder.getToken())
                .thenReturn(OAUTH_TOKEN_VALUE);

        // When
        AspspSettings aspspSettings = oauthProfileServiceWrapper.getAspspSettings(INSTANCE_ID);

        // Then
        assertEquals(modifiedSettings, aspspSettings);
    }

    @Test
    void getScaApproaches() {
        // Given
        List<ScaApproach> expectedScaApproaches = Collections.singletonList(ScaApproach.REDIRECT);
        when(aspspProfileService.getScaApproaches(INSTANCE_ID))
                .thenReturn(expectedScaApproaches);

        // When
        List<ScaApproach> actualScaApproaches = oauthProfileServiceWrapper.getScaApproaches(INSTANCE_ID);

        // Then
        assertEquals(expectedScaApproaches, actualScaApproaches);

        verify(aspspProfileService).getScaApproaches(INSTANCE_ID);
    }
}