package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.aspsp.xs2a.connector.spi.converter.AisConsentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaLoginToConsentResponseMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAConsentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.middleware.api.service.TokenStorageService;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.ConsentRestClient;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class AisConsentSpiImplTest {

    @InjectMocks
    private AisConsentSpiImpl spi;

    @Mock private ConsentRestClient consentRestClient;
    @Mock private TokenStorageService tokenStorageService;
    @Mock private AisConsentMapper aisConsentMapper;
    @Mock private AuthRequestInterceptor authRequestInterceptor;
    @Mock private AspspConsentDataService consentDataService;
    @Mock private GeneralAuthorisationService authorisationService;
    @Mock private ScaMethodConverter scaMethodConverter;
    @Mock private ScaLoginToConsentResponseMapper scaLoginToConsentResponseMapper;

    @Test
    public void getConsentStatus() {
        assertEquals(ConsentStatus.VALID, spi.getConsentStatus(null));

        SCAConsentResponseTO consentResponse = new SCAConsentResponseTO();
        assertEquals(ConsentStatus.VALID, spi.getConsentStatus(consentResponse));

        consentResponse.setMultilevelScaRequired(true);
        assertEquals(ConsentStatus.VALID, spi.getConsentStatus(consentResponse));

        consentResponse.setPartiallyAuthorised(true);
        assertEquals(ConsentStatus.VALID, spi.getConsentStatus(consentResponse));

        consentResponse.setScaStatus(ScaStatusTO.FINALISED);
        assertEquals(ConsentStatus.PARTIALLY_AUTHORISED, spi.getConsentStatus(consentResponse));
    }
}