package de.adorsys.aspsp.xs2a.connector.spi.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class FeignExceptionReader {
    private final ObjectMapper objectMapper;

    public String getErrorMessage(FeignException feignException) {
        return Optional.ofNullable(feignException.content())
                       .map(this::readTree)
                       .map(this::getMessage)
                       .map(JsonNode::asText)
                       .orElse(null);
    }

    public String getErrorCode(FeignException feignException) {
        return Optional.ofNullable(feignException.content())
                       .map(this::readTree)
                       .map(this::getCode)
                       .map(JsonNode::asText)
                       .orElse("");
    }

    private JsonNode getCode(JsonNode jsonNode) {
        return jsonNode.get("errorCode");
    }

    private JsonNode getMessage(JsonNode jsonNode) {
        JsonNode devMessage = jsonNode.get("devMessage");
        if (devMessage == null) {
            return jsonNode.get("message");
        }
        return devMessage;
    }

    private JsonNode readTree(byte[] content) {
        try {
            return objectMapper.readTree(content);
        } catch (IOException ex) {
            log.error("Could not parse Error Message from Bank!");
            return null;
        }
    }
}
