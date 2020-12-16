package de.adorsys.aspsp.xs2a.connector.spi.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.adorsys.aspsp.xs2a.util.TestConfiguration;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {FeignExceptionReader.class, TestConfiguration.class})
class FeignExceptionReaderTest {
    private static final String DEV_MESSAGE = "devMessage";

    @Autowired
    private FeignExceptionReader feignExceptionReader;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getErrorMessageSuccess() throws JsonProcessingException {
        //Given
        String feignBodyString = buildFeignBodyMessage(DEV_MESSAGE);
        FeignException feignException = FeignException.errorStatus("", buildErrorResponse(feignBodyString));
        //When
        String errorMessage = feignExceptionReader.getErrorMessage(feignException);
        //Then
        assertEquals(DEV_MESSAGE, errorMessage);
    }

    @Test
    void getErrorMessageNoDevMessage() throws JsonProcessingException {
        //Given
        String feignBodyString = buildFeignBodyMessage("message", DEV_MESSAGE);
        FeignException feignException = FeignException.errorStatus("", buildErrorResponse(feignBodyString));
        //When
        String errorMessage = feignExceptionReader.getErrorMessage(feignException);
        //Then
        assertNotNull(errorMessage);
    }

    @Test
    void getErrorMessageNoDevMessageAndNoMessage() throws JsonProcessingException {
        //Given
        String feignBodyString = buildFeignBodyMessage("other message", DEV_MESSAGE);
        FeignException feignException = FeignException.errorStatus("", buildErrorResponse(feignBodyString));
        //When
        String errorMessage = feignExceptionReader.getErrorMessage(feignException);
        //Then
        assertNull(errorMessage);
    }

    @Test
    void getErrorMessageNoContent() {
        //Given
        FeignException feignException = FeignException.errorStatus("", FeignExceptionHandler.error(HttpStatus.BAD_REQUEST));
        //When
        String errorMessage = feignExceptionReader.getErrorMessage(feignException);
        //Then
        assertNull(errorMessage);
    }

    private Response buildErrorResponse(String body) {
        return Response.builder()
                       .status(HttpStatus.BAD_REQUEST.value())
                       .request(Request.create(Request.HttpMethod.GET, "", Collections.emptyMap(), null, Charset.defaultCharset(), null))
                       .headers(Collections.emptyMap())
                       .body(body, StandardCharsets.UTF_8)
                       .build();
    }

    private String buildFeignBodyMessage(String devMessage) throws JsonProcessingException {
        return buildFeignBodyMessage("devMessage", devMessage);
    }

    private String buildFeignBodyMessage(String key, String value) throws JsonProcessingException {
        Map<String, String> feignBody = new HashMap<>();
        feignBody.put(key, value);
        return objectMapper.writeValueAsString(feignBody);
    }
}
