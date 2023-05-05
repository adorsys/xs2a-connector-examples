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
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ScaMethodConverterTest {

    private ScaMethodConverter mapper;
    private JsonReader jsonReader = new JsonReader();
    private ScaUserDataTO userData;
    private SpiAuthenticationObject expected;

    @BeforeEach
    void setUp() {
        userData = jsonReader.getObjectFromFile("json/spi/converter/sca-user-data.json", ScaUserDataTO.class);
        expected = jsonReader.getObjectFromFile("json/spi/converter/spi-authentication-object.json", SpiAuthenticationObject.class);

        mapper = Mappers.getMapper(ScaMethodConverter.class);
    }

    @Test
    void toAuthenticationObject() {
        SpiAuthenticationObject authenticationObject = mapper.toAuthenticationObject(userData);

        assertEquals(expected, authenticationObject);
    }

    @Test
    void toAuthenticationObject_nullValue() {
        SpiAuthenticationObject authenticationObject = mapper.toAuthenticationObject(null);
        assertNull(authenticationObject);
    }

    @Test
    void toAuthenticationObjectList() {
        List<SpiAuthenticationObject> objects = mapper.toAuthenticationObjectList(Collections.singletonList(userData));

        assertEquals(1, objects.size());
        assertEquals(expected, objects.get(0));
    }

    @Test
    void toAuthenticationObjectList_nullValue() {
        List<SpiAuthenticationObject> objects = mapper.toAuthenticationObjectList(null);
        assertNull(objects);
    }
}