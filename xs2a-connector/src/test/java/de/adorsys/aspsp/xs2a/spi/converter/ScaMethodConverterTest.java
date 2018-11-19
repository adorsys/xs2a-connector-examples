package de.adorsys.aspsp.xs2a.spi.converter;

import de.adorsys.ledgers.domain.sca.SCAMethodTO;
import de.adorsys.ledgers.domain.sca.SCAMethodTypeTO;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import org.junit.Before;
import org.junit.Test;
import org.mapstruct.factory.Mappers;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;

public class ScaMethodConverterTest {

    private SCAMethodTO method;
    private SCAMethodTypeTO methodType;
    private ScaMethodConverter mapper;

    @Before
    public void setUp() {
        method = new SCAMethodTO();
        methodType = SCAMethodTypeTO.EMAIL;
        method.setValue("some@email.com");
        method.setType(methodType);

        mapper = Mappers.getMapper(ScaMethodConverter.class);
    }

    @Test
    public void toSpiAuthenticationObject() {

        SpiAuthenticationObject authenticationObject = mapper.toSpiAuthenticationObject(method);

        assertThat(authenticationObject.getAuthenticationType(), is(methodType.name()));
        assertThat(authenticationObject.getName(), is(method.getValue()));

        assertThat(authenticationObject.getAuthenticationMethodId(), is(nullValue()));
        assertThat(authenticationObject.getAuthenticationVersion(), is(nullValue()));
        assertThat(authenticationObject.getExplanation(), is(nullValue()));
    }

    @Test
    public void toSpiAuthenticationObjectList() {

        List<SpiAuthenticationObject> objects = mapper.toSpiAuthenticationObjectList(Collections.singletonList(method));

        assertThat(objects.size(), is(1));
        SpiAuthenticationObject authenticationObject = objects.get(0);

        assertThat(authenticationObject.getAuthenticationType(), is(methodType.name()));
        assertThat(authenticationObject.getName(), is(method.getValue()));

        assertThat(authenticationObject.getAuthenticationMethodId(), is(nullValue()));
        assertThat(authenticationObject.getAuthenticationVersion(), is(nullValue()));
        assertThat(authenticationObject.getExplanation(), is(nullValue()));
    }
}