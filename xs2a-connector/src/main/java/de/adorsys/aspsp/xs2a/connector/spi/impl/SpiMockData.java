/*
 * Copyright 2018-2021 adorsys GmbH & Co KG
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

import de.adorsys.psd2.xs2a.core.domain.TppMessageInformation;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiHrefType;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiLinks;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SpiMockData {
    public static final SpiLinks SPI_LINKS = buildSpiLinks();
    public static final Set<TppMessageInformation> TPP_MESSAGES = buildTppMessages();
    public static final Set<TppMessageInformation> TPP_MESSAGES_START_AUTHORISATION = buildTppMessagesStartAuthorisation();
    public static final List<SpiAuthenticationObject> SCA_METHODS = buildScaMethods();
    public static final String PSU_MESSAGE = "mocked PSU message from the bank";
    public static final String PSU_MESSAGE_START_AUTHORISATION = "Start authorisation mocked PSU message from the bank";
    public static final String DECOUPLED_PSU_MESSAGE = "Please check your app to continue...";
    public static final boolean FUNDS_AVAILABLE = true;

    private static SpiLinks buildSpiLinks() {
        SpiLinks spiLinks = new SpiLinks();
        spiLinks.setAccount(new SpiHrefType("Mock spi account link from the bank"));
        return spiLinks;
    }

    private static Set<TppMessageInformation> buildTppMessages() {
        HashSet<TppMessageInformation> tppInformationSet = new HashSet<>();
        tppInformationSet.add(TppMessageInformation.buildWithCustomWarning(MessageErrorCode.FORMAT_ERROR, "Mocked tpp message from the bank"));
        return tppInformationSet;
    }

    private static Set<TppMessageInformation> buildTppMessagesStartAuthorisation() {
        HashSet<TppMessageInformation> tppInformationSet = new HashSet<>();
        tppInformationSet.add(TppMessageInformation.buildWithCustomWarning(MessageErrorCode.FORMAT_ERROR, "Start authorisation Mocked tpp message from the bank"));
        return tppInformationSet;
    }

    private static List<SpiAuthenticationObject> buildScaMethods() {
        SpiAuthenticationObject psi = new SpiAuthenticationObject();
        psi.setAuthenticationType("Mocked Authentication type from the bank");
        psi.setAuthenticationMethodId("Mocked Authentication id from the bank");
        psi.setDecoupled(false);
        psi.setName("Mocked name from the bank");
        psi.setAuthenticationVersion("Mocked Authentication version from the bank");

        return Collections.singletonList(psi);
    }
}