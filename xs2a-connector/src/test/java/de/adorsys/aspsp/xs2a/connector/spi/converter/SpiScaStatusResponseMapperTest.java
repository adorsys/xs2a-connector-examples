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

package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaStatusResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;


@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {SpiScaStatusResponseMapper.class})
class SpiScaStatusResponseMapperTest {
    private final JsonReader jsonReader = new JsonReader();
    private final String PSU_MESSAGE = "Your Login for CONSENT id: cd9eb664-660c-48a6-878d-ddc74273315c is successful";

    @Autowired
    SpiScaStatusResponseMapper mapper;

    @Test
    void toSpiScaStatusResponse_null() {
        //When
        SpiScaStatusResponse actual = mapper.toSpiScaStatusResponse(null);

        //Then
        assertEquals(null, actual);
    }

    @Test
    void toSpiScaStatusResponse() {
        //Given
        GlobalScaResponseTO globalScaResponseTO =
                jsonReader.getObjectFromFile("json/mappers/global-sca-response-to.json", GlobalScaResponseTO.class);
        SpiScaStatusResponse expected = getTestData();


        //When
        SpiScaStatusResponse actual = mapper.toSpiScaStatusResponse(globalScaResponseTO);

        //Then
        assertEquals(expected, actual);
    }

    private SpiScaStatusResponse getTestData() {
        return new SpiScaStatusResponse(ScaStatus.PSUAUTHENTICATED,
                                        false,
                                        PSU_MESSAGE);
    }
}
