/*
 * Copyright 2018-2023 adorsys GmbH & Co KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    public LedgersErrorCode getLedgersErrorCode(FeignException feignException) {
        return LedgersErrorCode.getFromString(getErrorCode(feignException))
                       .orElse(null);
    }

    private String getErrorCode(FeignException feignException) {
        return Optional.ofNullable(feignException.content())
                       .map(this::readTree)
                       .map(this::getCode)
                       .map(JsonNode::asText)
                       .orElse(null);
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
