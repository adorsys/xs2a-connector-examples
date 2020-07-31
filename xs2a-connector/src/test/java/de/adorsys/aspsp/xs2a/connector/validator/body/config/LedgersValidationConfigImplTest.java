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

package de.adorsys.aspsp.xs2a.connector.validator.body.config;

import de.adorsys.aspsp.xs2a.util.JsonReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LedgersValidationConfigImplTest {
    private JsonReader jsonReader = new JsonReader();
    private LedgersValidationConfigImpl actual;

    @BeforeEach
    void setUp() {
        actual = new LedgersValidationConfigImpl();
    }

    @Test
    void checkConfiguration() {
        LedgersValidationConfigImpl expected = jsonReader.getObjectFromFile("json/validation/payment-validation-config.json",
                                                                            LedgersValidationConfigImpl.class);
        assertEquals(expected, actual);
    }
}