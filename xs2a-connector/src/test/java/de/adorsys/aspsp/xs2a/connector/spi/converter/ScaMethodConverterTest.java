package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.psd2.xs2a.core.authorisation.AuthenticationObject;
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
    private AuthenticationObject expected;

    @Before
    public void setUp() {
        userData = jsonReader.getObjectFromFile("json/spi/converter/sca-user-data.json", ScaUserDataTO.class);
        expected = jsonReader.getObjectFromFile("json/spi/converter/spi-authentication-object.json", AuthenticationObject.class);

        mapper = Mappers.getMapper(ScaMethodConverter.class);
    }

    @Test
    public void toAuthenticationObject() {
        AuthenticationObject authenticationObject = mapper.toAuthenticationObject(userData);

        assertThat(authenticationObject, is(expected));
    }

    @Test
    public void toAuthenticationObject_nullValue() {
        AuthenticationObject authenticationObject = mapper.toAuthenticationObject(null);
        assertNull(authenticationObject);
    }

    @Test
    public void toAuthenticationObjectList() {
        List<AuthenticationObject> objects = mapper.toAuthenticationObjectList(Collections.singletonList(userData));

        assertThat(objects.size(), is(1));
        assertThat(objects.get(0), is(expected));
    }

    @Test
    public void toAuthenticationObjectList_nullValue() {
        List<AuthenticationObject> objects = mapper.toAuthenticationObjectList(null);
        assertNull(objects);
    }
}