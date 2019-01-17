package de.adorsys.aspsp.xs2a.spi.converter;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mapstruct.factory.Mappers;

import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.ledgers.middleware.api.domain.um.ScaMethodTypeTO;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;

public class ScaMethodConverterTest {

    private ScaUserDataTO method;
    private ScaMethodTypeTO methodType;
    private ScaMethodConverter mapper;
    private SpiAuthenticationObject expected;
    
    private YamlMapper yamlMapper = new YamlMapper(ScaMethodConverterTest.class);

    @Before
    public void setUp() throws IOException {
        method = new ScaUserDataTO();
        methodType = ScaMethodTypeTO.EMAIL;
        method.setMethodValue("some@email.com");
		method.setScaMethod(methodType);

        mapper = Mappers.getMapper(ScaMethodConverter.class);
        expected = yamlMapper.readYml(SpiAuthenticationObject.class, "spi-authentication-object.yml");
    }

    @Test
    public void toSpiAuthenticationObject() {

        SpiAuthenticationObject authenticationObject = mapper.toSpiAuthenticationObject(method);

        assertThat(authenticationObject, is(expected));
    }

    @Test
    public void toSpiAuthenticationObjectList() {

        List<SpiAuthenticationObject> objects = mapper.toSpiAuthenticationObjectList(Collections.singletonList(method));

        assertThat(objects.size(), is(1));
        SpiAuthenticationObject authenticationObject = objects.get(0);

        assertThat(authenticationObject, is(expected));
    }

}