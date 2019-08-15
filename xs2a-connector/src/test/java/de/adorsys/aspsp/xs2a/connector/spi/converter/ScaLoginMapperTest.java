package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAConsentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCALoginResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import org.junit.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.Assert.*;

public class ScaLoginMapperTest {

    private ScaLoginMapper mapper = Mappers.getMapper(ScaLoginMapper.class);
    private JsonReader jsonReader = new JsonReader();

    @Test
    public void toConsentResponse_success() {
        SCALoginResponseTO scaLoginResponseTO = jsonReader.getObjectFromFile("json/spi/converter/sca-login-response.json", SCALoginResponseTO.class);
        SCAConsentResponseTO scaConsentResponseTO = mapper.toConsentResponse(scaLoginResponseTO);

        SCAConsentResponseTO expected = jsonReader.getObjectFromFile("json/spi/converter/sca-login-response.json", SCAConsentResponseTO.class);
        assertEquals(expected, scaConsentResponseTO);
    }

    @Test
    public void toConsentResponse_nullValue() {
        SCAConsentResponseTO scaConsentResponseTO = mapper.toConsentResponse(null);
        assertNull(scaConsentResponseTO);
    }

    @Test
    public void toPaymentResponse_success() {
        SCALoginResponseTO scaLoginResponseTO = jsonReader.getObjectFromFile("json/spi/converter/sca-login-response.json", SCALoginResponseTO.class);
        SCAPaymentResponseTO scaPaymentResponseTO = mapper.toPaymentResponse(scaLoginResponseTO);

        SCAPaymentResponseTO expected = jsonReader.getObjectFromFile("json/spi/converter/sca-login-response.json", SCAPaymentResponseTO.class);
        assertEquals(expected, scaPaymentResponseTO);
    }

    @Test
    public void toPaymentResponse_nullValue() {
        SCAPaymentResponseTO scaPaymentResponseTO = mapper.toPaymentResponse(null);
        assertNull(scaPaymentResponseTO);
    }
}