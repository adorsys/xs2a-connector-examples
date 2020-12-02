package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.aspsp.xs2a.connector.mock.IbanResolverMockService;
import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.um.AisAccountAccessTypeTO;
import de.adorsys.ledgers.middleware.api.domain.um.AisConsentTO;
import de.adorsys.psd2.xs2a.core.ais.AccountAccessType;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {AisConsentMapperImpl.class, IbanResolverMockService.class})
class AisConsentMapperTest {

    @Autowired
    private AisConsentMapper aisConsentMapper;
    private JsonReader jsonReader = new JsonReader();

    @Test
    void mapToAisConsentWithRealData() {
        SpiAccountConsent inputData = jsonReader.getObjectFromFile("json/mappers/spi-account-consent.json", SpiAccountConsent.class);
        AisConsentTO expectedResponse = jsonReader.getObjectFromFile("json/mappers/ais-consent-to.json", AisConsentTO.class);
        AisConsentTO actualResponse = aisConsentMapper.mapToAisConsent(inputData);
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    void mapToAisConsentWithNull() {
        AisConsentTO actualResponse = aisConsentMapper.mapToAisConsent(null);
        assertNull(actualResponse);
    }

    @Test
    void toAccountList() {
        SpiAccountReference accountReference = jsonReader.getObjectFromFile("json/spi/converter/spi-account-reference.json", SpiAccountReference.class);
        List<String> actual = aisConsentMapper.toAccountList(accountReference);

        assertEquals(1, actual.size());
        assertTrue(actual.contains("DE52500105173911841934"));
    }

    @Test
    void mapToAccountAccessType() {
        assertNull(aisConsentMapper.mapToAccountAccessType(null));

        assertEquals(AisAccountAccessTypeTO.ALL_ACCOUNTS, aisConsentMapper.mapToAccountAccessType(AccountAccessType.ALL_ACCOUNTS));
        assertEquals(AisAccountAccessTypeTO.ALL_ACCOUNTS, aisConsentMapper.mapToAccountAccessType(AccountAccessType.ALL_ACCOUNTS_WITH_OWNER_NAME));
    }
}