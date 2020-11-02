/*
 * Copyright 2018-2020 adorsys GmbH & Co KG
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class LoginAttemptAspspConsentDataService {

    @Value("${xs2a.sca.login.failed.max:3}")
    private int loginFailedMax;

    private final ObjectMapper objectMapper;

    public byte[] store(LoginAttemptResponse response) {
        try {
            return objectMapper.writeValueAsBytes(response);
        } catch (IOException e) {
            throw FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    public LoginAttemptResponse response(byte[] aspspConsentData) {
        try {
            return fromBytes(aspspConsentData);
        } catch (IOException e) {
            throw FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    private LoginAttemptResponse fromBytes(byte[] bytes) throws IOException {
        String type = readType(bytes);
        if (LoginAttemptResponse.class.getSimpleName().equals(type)) {
            return objectMapper.readValue(bytes, LoginAttemptResponse.class);
        }
        return null;
    }

    private String readType(byte[] tokenBytes) throws IOException {
        JsonNode jsonNode = objectMapper.readTree(tokenBytes);
        JsonNode objectType = jsonNode.get("objectType");
        if (objectType == null) {
            return null;
        }
        return objectType.textValue();
    }

    public int getRemainingLoginAttempts(int loginFailedCount) {
        return Math.max(loginFailedMax - loginFailedCount, 0);
    }
}