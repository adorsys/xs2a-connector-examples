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

import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaMethodUtilsTest {

    private JsonReader jsonReader = new JsonReader();

    @Test
    void toScaMethod_success() {
        ScaUserDataTO userData = jsonReader.getObjectFromFile("json/spi/converter/sca-user-data.json", ScaUserDataTO.class);
        String scaMethod = ScaMethodUtils.toScaMethod(userData);

        assertEquals("111", scaMethod);
    }

    @Test
    void toScaMethods_success() {
        ScaUserDataTO userData = jsonReader.getObjectFromFile("json/spi/converter/sca-user-data.json", ScaUserDataTO.class);

        List<String> scaMethods = ScaMethodUtils.toScaMethods(Collections.singletonList(userData));

        assertEquals(1, scaMethods.size());
        assertEquals("111", scaMethods.get(0));

    }

    @Test
    void toScaMethods_emptyList() {
        assertTrue(ScaMethodUtils.toScaMethods(null).isEmpty());
        assertTrue(ScaMethodUtils.toScaMethods(Collections.emptyList()).isEmpty());
    }
}