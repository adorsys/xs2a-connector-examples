package de.adorsys.aspsp.xs2a.connector.config.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.aspsp.xs2a.util.TestConfiguration;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAConsentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {TestConfiguration.class, TokenStorageServiceImpl.class})
class TokenStorageServiceImplTest {

    @Autowired
    private TokenStorageServiceImpl tokenStorageService;
    @Autowired
    @Qualifier(value = "objectMapper")
    private ObjectMapper mapper;

    private JsonReader jsonReader = new JsonReader();

    @Test
    void fromBytes_SCAConsentResponseTO_success() throws IOException {
        byte[] tokenBytes = jsonReader.getStringFromFile("json/config/auth/sca-consent-response.json").getBytes();
        SCAResponseTO scaResponseTO = tokenStorageService.fromBytes(tokenBytes);

        assertNotNull(scaResponseTO);
        assertTrue(scaResponseTO instanceof SCAConsentResponseTO);
    }

    @Test
    void fromBytes_objectTypesNull() {
        assertThrows(IOException.class, () -> tokenStorageService.fromBytes("{}".getBytes()));
    }

    @Test
    void fromBytes_nullValue_shouldThrowException() {
        assertThrows(FeignException.class, () -> tokenStorageService.fromBytes(null));
    }

    @Test
    void fromBytes_emptyArray_shouldThrowException() {
        assertThrows(FeignException.class, () -> tokenStorageService.fromBytes(new byte[]{}));
    }

    @Test
    void objectType() throws IOException {
        assertEquals("test1", tokenStorageService.objectType(mapper.readTree("{\"objectType\": \"test1\"}")));
        assertNull(tokenStorageService.objectType(new TextNode("")));
        assertNull(tokenStorageService.objectType(new TextNode("{}")));
        assertNull(tokenStorageService.objectType(new TextNode(null)));
    }
}