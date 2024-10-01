/*
 * Copyright 2018-2024 adorsys GmbH & Co KG
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

import de.adorsys.psd2.xs2a.spi.domain.common.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiHrefType;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiLinks;
import de.adorsys.psd2.xs2a.spi.domain.error.SpiMessageErrorCode;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiTppMessageInformation;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;//NOPMD

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SpiMockData {
    public static final boolean IS_MOCK_DATA_ENABLED = isMockedDataEnabled();
    public static final SpiLinks SPI_LINKS = buildSpiLinks();
    public static final Set<SpiTppMessageInformation> TPP_MESSAGES = buildTppMessages();
    public static final Set<SpiTppMessageInformation> TPP_MESSAGES_START_AUTHORISATION = buildTppMessagesStartAuthorisation();
    public static final List<SpiAuthenticationObject> SCA_METHODS = buildScaMethods();
    public static final String PSU_MESSAGE = IS_MOCK_DATA_ENABLED
                                                     ? "Mocked PSU message from SPI"
                                                     : null;
    public static final String PSU_MESSAGE_START_AUTHORISATION = IS_MOCK_DATA_ENABLED
                                                                         ? "Start authorisation mocked PSU message from SPI"
                                                                         : null;
    public static final String DECOUPLED_PSU_MESSAGE = "Please check your app to continue...";
    public static final boolean FUNDS_AVAILABLE = IS_MOCK_DATA_ENABLED;
    public static final boolean TRUSTED_BENEFICIARY_FLAG = false;

    private static SpiLinks buildSpiLinks() {
        if (!IS_MOCK_DATA_ENABLED) {
            return null;
        }

        SpiLinks spiLinks = new SpiLinks();
        spiLinks.setAccount(new SpiHrefType("Mocked account link from SPI"));
        return spiLinks;
    }

    private static Set<SpiTppMessageInformation> buildTppMessages() {
        if (!IS_MOCK_DATA_ENABLED) {
            return null;
        }

        HashSet<SpiTppMessageInformation> tppInformationSet = new HashSet<>();
        tppInformationSet.add(SpiTppMessageInformation.buildWithCustomWarning(SpiMessageErrorCode.FORMAT_ERROR, "Mocked tpp message from SPI"));
        return tppInformationSet;
    }

    private static Set<SpiTppMessageInformation> buildTppMessagesStartAuthorisation() {
        if (!IS_MOCK_DATA_ENABLED) {
            return null;
        }

        HashSet<SpiTppMessageInformation> tppInformationSet = new HashSet<>();
        tppInformationSet.add(SpiTppMessageInformation.buildWithCustomWarning(SpiMessageErrorCode.FORMAT_ERROR, "Start authorisation mocked tpp message from SPI"));
        return tppInformationSet;
    }

    private static List<SpiAuthenticationObject> buildScaMethods() {
        if (!IS_MOCK_DATA_ENABLED) {
            return null;
        }

        SpiAuthenticationObject spiAuthObject = new SpiAuthenticationObject();
        spiAuthObject.setAuthenticationType("Mocked Authentication type from SPI");
        spiAuthObject.setAuthenticationMethodId("Mocked Authentication id from SPI");
        spiAuthObject.setDecoupled(false);
        spiAuthObject.setName("Mocked name from SPI");
        spiAuthObject.setAuthenticationVersion("Mocked Authentication version from SPI");

        return Collections.singletonList(spiAuthObject);
    }

    private static boolean isMockedDataEnabled() {
        InputStream resourceAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("application.yml");

        try {
            Properties props = new Properties();
            props.load(resourceAsStream);
            String property = props.getProperty("xs2a.spi-mock-data.enabled");
            return Boolean.parseBoolean(property);
        } catch (IOException e) {
            return false;
        }
    }
}
