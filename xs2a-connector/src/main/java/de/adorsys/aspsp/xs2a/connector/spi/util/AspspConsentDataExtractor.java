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

import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AspspConsentDataExtractor {

    private static final Logger logger = LoggerFactory.getLogger(AspspConsentDataExtractor.class);

    private AspspConsentDataExtractor() {
    }

    public static String extractEncryptedConsentId(SpiAspspConsentDataProvider aspspConsentDataProvider) {
        try {
            return (String) FieldUtils.readField(aspspConsentDataProvider, "encryptedConsentId", true);
        } catch (IllegalAccessException e) {
            logger.error("could not read encrypted consent id");
            return "";
        }
    }
}
