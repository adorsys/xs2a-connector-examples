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

package de.adorsys.aspsp.xs2a.connector.spi.util;

import de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation.SpiAspspConsentDataProviderWithEncryptedId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AspspConsentDataExtractorTest {

    @Test
    void extractEncryptedConsentId() {
        assertEquals("12345", AspspConsentDataExtractor.extractEncryptedConsentId(new SpiAspspConsentDataProviderWithEncryptedId(null, "12345")));
        assertEquals("", AspspConsentDataExtractor.extractEncryptedConsentId(null));
    }
}