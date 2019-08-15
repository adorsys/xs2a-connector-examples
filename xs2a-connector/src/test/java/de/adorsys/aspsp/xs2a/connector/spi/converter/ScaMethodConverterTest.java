package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import org.junit.Before;
import org.junit.Test;
import org.mapstruct.factory.Mappers;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

public class ScaMethodConverterTest {

    private ScaMethodConverter mapper;
    private JsonReader jsonReader = new JsonReader();
    private ScaUserDataTO userData;
    private SpiAuthenticationObject expected;

    @Before
    public void setUp() {
        userData = jsonReader.getObjectFromFile("json/spi/converter/sca-user-data.json", ScaUserDataTO.class);
        expected = jsonReader.getObjectFromFile("json/spi/converter/spi-authentication-object.json", SpiAuthenticationObject.class);

        mapper = Mappers.getMapper(ScaMethodConverter.class);
    }

    @Test
    public void toSpiAuthenticationObject() {
        SpiAuthenticationObject authenticationObject = mapper.toSpiAuthenticationObject(userData);

        assertThat(authenticationObject, is(expected));
    }

    @Test
    public void toSpiAuthenticationObject_nullValue() {
        SpiAuthenticationObject authenticationObject = mapper.toSpiAuthenticationObject(null);
        assertNull(authenticationObject);
    }

    @Test
    public void toSpiAuthenticationObjectList() {
        List<SpiAuthenticationObject> objects = mapper.toSpiAuthenticationObjectList(Collections.singletonList(userData));

        assertThat(objects.size(), is(1));
        assertThat(objects.get(0), is(expected));
    }

    @Test
    public void toSpiAuthenticationObjectList_nullValue() {
        List<SpiAuthenticationObject> objects = mapper.toSpiAuthenticationObjectList(null);
        assertNull(objects);
    }
}