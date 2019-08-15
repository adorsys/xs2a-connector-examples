package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.um.AisConsentTO;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.junit.Test;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {AisConsentMapperImpl.class})
public class AisConsentMapperTest {

    @Autowired
    private AisConsentMapper aisConsentMapper;
    private JsonReader jsonReader = new JsonReader();

    @Test
    public void mapToAisConsentWithRealData() {
        SpiAccountConsent inputData = jsonReader.getObjectFromFile("json/mappers/spi-account-consent.json", SpiAccountConsent.class);
        AisConsentTO expectedResponse = jsonReader.getObjectFromFile("json/mappers/ais-consent-to.json", AisConsentTO.class);
        AisConsentTO actualResponse = aisConsentMapper.mapToAisConsent(inputData);
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    public void mapToAisConsentWithNull() {
        AisConsentTO actualResponse = aisConsentMapper.mapToAisConsent(null);
        assertNull(actualResponse);
    }
}