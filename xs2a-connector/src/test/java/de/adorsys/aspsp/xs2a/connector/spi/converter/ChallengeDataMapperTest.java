package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.sca.ChallengeDataTO;
import de.adorsys.psd2.xs2a.core.sca.ChallengeData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {ChallengeDataMapperImpl.class})
public class ChallengeDataMapperTest {

    @Autowired
    private ChallengeDataMapper challengeDataMapper;
    private JsonReader jsonReader = new JsonReader();

    @Test
    public void toChallengeDataWithRealData() {
        ChallengeDataTO inputData = jsonReader.getObjectFromFile("json/mappers/challenge-data-to.json", ChallengeDataTO.class);
        inputData.setImage("image".getBytes());
        ChallengeData actualResult = challengeDataMapper.toChallengeData(inputData);
        ChallengeData expectedResult = jsonReader.getObjectFromFile("json/mappers/challenge-data.json", ChallengeData.class);
        expectedResult.setImage("image".getBytes());
        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void toChallengeDataWithNull() {
        ChallengeData actualResult = challengeDataMapper.toChallengeData(null);
        assertNull(actualResult);
    }
}