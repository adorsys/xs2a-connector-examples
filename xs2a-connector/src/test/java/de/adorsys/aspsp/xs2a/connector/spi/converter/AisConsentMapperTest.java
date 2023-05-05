/*
 * Copyright 2018-2023 adorsys GmbH & Co KG
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

package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.aspsp.xs2a.connector.mock.IbanResolverMockService;
import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.um.AisAccountAccessTypeTO;
import de.adorsys.ledgers.middleware.api.domain.um.AisConsentTO;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiAccountAccessType;
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

        assertEquals(AisAccountAccessTypeTO.ALL_ACCOUNTS, aisConsentMapper.mapToAccountAccessType(SpiAccountAccessType.ALL_ACCOUNTS));
        assertEquals(AisAccountAccessTypeTO.ALL_ACCOUNTS, aisConsentMapper.mapToAccountAccessType(SpiAccountAccessType.ALL_ACCOUNTS_WITH_OWNER_NAME));
    }
}