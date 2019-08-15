package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ScaMethodUtilsTest {

    private JsonReader jsonReader = new JsonReader();

    @Test
    public void toScaMethod_success() {
        ScaUserDataTO userData = jsonReader.getObjectFromFile("json/spi/converter/sca-user-data.json", ScaUserDataTO.class);
        String scaMethod = ScaMethodUtils.toScaMethod(userData);

        assertEquals("111", scaMethod);
    }

    @Test
    public void toScaMethods_success() {
        ScaUserDataTO userData = jsonReader.getObjectFromFile("json/spi/converter/sca-user-data.json", ScaUserDataTO.class);

        List<String> scaMethods = ScaMethodUtils.toScaMethods(Collections.singletonList(userData));

        assertEquals(1, scaMethods.size());
        assertEquals("111", scaMethods.get(0));

    }

    @Test
    public void toScaMethods_emptyList() {
        assertTrue(ScaMethodUtils.toScaMethods(null).isEmpty());
        assertTrue(ScaMethodUtils.toScaMethods(Collections.emptyList()).isEmpty());
    }
}