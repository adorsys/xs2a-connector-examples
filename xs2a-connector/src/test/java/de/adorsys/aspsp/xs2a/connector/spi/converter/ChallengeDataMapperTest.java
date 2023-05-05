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
import de.adorsys.ledgers.middleware.api.domain.sca.ChallengeDataTO;
import de.adorsys.psd2.xs2a.spi.domain.sca.SpiChallengeData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {ChallengeDataMapperImpl.class})
class ChallengeDataMapperTest {

    @Autowired
    private ChallengeDataMapper challengeDataMapper;
    private JsonReader jsonReader = new JsonReader();

    @Test
    void toChallengeDataWithRealData() {
        ChallengeDataTO inputData = jsonReader.getObjectFromFile("json/mappers/challenge-data-to.json", ChallengeDataTO.class);
        inputData.setImage("image".getBytes());
        SpiChallengeData actualResult = challengeDataMapper.toChallengeData(inputData);
        SpiChallengeData expectedResult = jsonReader.getObjectFromFile("json/mappers/challenge-data.json", SpiChallengeData.class);
        expectedResult.setImage("image".getBytes());
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void toChallengeDataWithNull() {
        SpiChallengeData actualResult = challengeDataMapper.toChallengeData(null);
        assertNull(actualResult);
    }
}