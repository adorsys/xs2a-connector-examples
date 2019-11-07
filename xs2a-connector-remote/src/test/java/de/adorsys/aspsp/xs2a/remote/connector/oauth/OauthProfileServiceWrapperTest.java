package de.adorsys.aspsp.xs2a.remote.connector.oauth;

import de.adorsys.psd2.aspsp.profile.domain.AspspSettings;
import de.adorsys.psd2.aspsp.profile.service.AspspProfileService;
import de.adorsys.psd2.xs2a.core.profile.ScaApproach;
import de.adorsys.psd2.xs2a.service.discovery.ServiceTypeDiscoveryService;
import de.adorsys.psd2.xs2a.service.mapper.psd2.ServiceType;
import de.adorsys.xs2a.reader.JsonReader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OauthProfileServiceWrapperTest {
    private static final String ASPSP_SETTINGS_JSON_PATH = "json/oauth/aspsp-settings.json";
    private static final String ASPSP_SETTINGS_INTEGRATED_AIS_JSON_PATH = "json/oauth/aspsp-settings-oauth-integrated-ais.json";
    private static final String ASPSP_SETTINGS_INTEGRATED_PIS_JSON_PATH = "json/oauth/aspsp-settings-oauth-integrated-pis.json";
    private static final String ASPSP_SETTINGS_INTEGRATED_JSON_PATH = "json/oauth/aspsp-settings-oauth-integrated.json";
    private static final String ASPSP_SETTINGS_PRESTEP_JSON_PATH = "json/oauth/aspsp-settings-oauth-pre-step.json";
    private static final String OAUTH_TOKEN_VALUE = "some-valid-token";

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
    private AspspSettings realAspspSettings;

    @Before
    public void setUp() {
        oauthProfileServiceWrapper = new OauthProfileServiceWrapper(aspspProfileService, oauthDataHolder, serviceTypeDiscoveryService, AIS_INTEGRATED_OAUTH_SUFFIX, PIS_INTEGRATED_OAUTH_SUFFIX, AIS_PRESTEP_OAUTH_SUFFIX, PIS_PRESTEP_OAUTH_SUFFIX);

        realAspspSettings = jsonReader.getObjectFromFile(ASPSP_SETTINGS_JSON_PATH, AspspSettings.class);

        when(aspspProfileService.getAspspSettings())
                .thenReturn(realAspspSettings);
    }

    @Test
    public void getAspspSettings_withNullOauthType_shouldNotChangeProfile() {
        // When
        AspspSettings aspspSettings = oauthProfileServiceWrapper.getAspspSettings();

        // Then
        assertEquals(realAspspSettings, aspspSettings);
    }

    @Test
    public void getAspspSettings_withIntegratedOauthType_ais_shouldChangeFlowAndLinks() {
        // Given
        AspspSettings modifiedSettings = jsonReader.getObjectFromFile(ASPSP_SETTINGS_INTEGRATED_AIS_JSON_PATH, AspspSettings.class);

        when(oauthDataHolder.getOauthType())
                .thenReturn(OauthType.INTEGRATED);
        when(serviceTypeDiscoveryService.getServiceType())
                .thenReturn(ServiceType.AIS);

        // When
        AspspSettings aspspSettings = oauthProfileServiceWrapper.getAspspSettings();

        // Then
        assertEquals(modifiedSettings, aspspSettings);
    }

    @Test
    public void getAspspSettings_withIntegratedOauthType_pis_shouldChangeFlowAndLinks() {
        // Given
        AspspSettings modifiedSettings = jsonReader.getObjectFromFile(ASPSP_SETTINGS_INTEGRATED_PIS_JSON_PATH, AspspSettings.class);

        when(oauthDataHolder.getOauthType())
                .thenReturn(OauthType.INTEGRATED);
        when(serviceTypeDiscoveryService.getServiceType())
                .thenReturn(ServiceType.PIS);

        // When
        AspspSettings aspspSettings = oauthProfileServiceWrapper.getAspspSettings();

        // Then
        assertEquals(modifiedSettings, aspspSettings);
    }

    @Test
    public void getAspspSettings_withIntegratedOauthType_otherServiceType_shouldChangeFlow() {
        // Given
        AspspSettings modifiedSettings = jsonReader.getObjectFromFile(ASPSP_SETTINGS_INTEGRATED_JSON_PATH, AspspSettings.class);

        when(oauthDataHolder.getOauthType())
                .thenReturn(OauthType.INTEGRATED);
        when(serviceTypeDiscoveryService.getServiceType())
                .thenReturn(ServiceType.PIIS);

        // When
        AspspSettings aspspSettings = oauthProfileServiceWrapper.getAspspSettings();

        // Then
        assertEquals(modifiedSettings, aspspSettings);
    }

    @Test
    public void getAspspSettings_withPreStepOauthType_pis_shouldChangeFlowAndLinks() {
        // Given
        AspspSettings modifiedSettings = jsonReader.getObjectFromFile(ASPSP_SETTINGS_PRESTEP_JSON_PATH, AspspSettings.class);

        when(oauthDataHolder.getOauthType())
                .thenReturn(OauthType.PRE_STEP);
        when(oauthDataHolder.getToken())
                .thenReturn(OAUTH_TOKEN_VALUE);

        // When
        AspspSettings aspspSettings = oauthProfileServiceWrapper.getAspspSettings();

        // Then
        assertEquals(modifiedSettings, aspspSettings);
    }

    @Test
    public void getScaApproaches() {
        // Given
        List<ScaApproach> expectedScaApproaches = Collections.singletonList(ScaApproach.REDIRECT);
        when(aspspProfileService.getScaApproaches())
                .thenReturn(expectedScaApproaches);

        // When
        List<ScaApproach> actualScaApproaches = oauthProfileServiceWrapper.getScaApproaches();

        // Then
        assertEquals(expectedScaApproaches, actualScaApproaches);

        verify(aspspProfileService).getScaApproaches();
    }
}