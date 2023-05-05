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

package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.psd2.xs2a.spi.domain.common.SpiHrefType;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiLinks;
import de.adorsys.psd2.xs2a.spi.domain.error.SpiMessageErrorCode;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiTppMessageInformation;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpiMockDataTest {

    @Test
    void getPsuMessage_mockDataEnabled() {
        // When
        String actual = SpiMockData.PSU_MESSAGE;

        // Then
        assertEquals("Mocked PSU message from SPI", actual);
    }

    @Test
    void getTppMessages_mockDataEnabled() {
        // When
        Set<SpiTppMessageInformation> actual = SpiMockData.TPP_MESSAGES;
        Set<SpiTppMessageInformation> expected = getTppMessages();

        // Then
        assertEquals(expected, actual);
    }

    @Test
    void getSpiLinks_mockDataEnabled() {
        // When
        SpiLinks actual = SpiMockData.SPI_LINKS;
        SpiLinks expected = getSpiLinks();

        // Then
        assertEquals(expected, actual);
    }

    private SpiLinks getSpiLinks() {
        SpiLinks spiLinks = new SpiLinks();
        spiLinks.setAccount(new SpiHrefType("Mocked account link from SPI"));

        return spiLinks;
    }

    private Set<SpiTppMessageInformation> getTppMessages() {
        return Collections.singleton(SpiTppMessageInformation.buildWithCustomWarning(SpiMessageErrorCode.FORMAT_ERROR, "Mocked tpp message from SPI"));
    }
}
