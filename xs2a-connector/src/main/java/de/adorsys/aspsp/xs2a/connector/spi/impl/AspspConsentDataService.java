/*
 * Copyright 2018-2019 adorsys GmbH & Co KG
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
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaResponseMapper;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAConsentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCALoginResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class AspspConsentDataService {

    private final ObjectMapper objectMapper;
    private final ScaResponseMapper scaResponseMapper;
    private final LoginAttemptAspspConsentDataService loginAttemptAspspConsentDataService;

    /**
     * Default storage, makes sure there is a bearer token in the response object.
     */
    public byte[] store(GlobalScaResponseTO response) {
        return store(response, true);
    }

    public byte[] store(GlobalScaResponseTO response, boolean checkCredentials) {
        if (checkCredentials && response.getBearerToken() == null) {
            throw new IllegalStateException("Missing credentials, response must contain a bearer token by default.");
        }
        try {
            return objectMapper.writeValueAsBytes(response);
        } catch (IOException e) {
            throw FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    public GlobalScaResponseTO response(byte[] aspspConsentData) {
        return response(aspspConsentData, true);
    }

    public GlobalScaResponseTO response(byte[] aspspConsentData, boolean checkCredentials) {
        try {
            GlobalScaResponseTO sca = fromBytes(aspspConsentData);
            checkBearerTokenPresent(checkCredentials, sca);
            return sca;
        } catch (IOException e) {
            throw FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    public LoginAttemptAspspConsentDataService getLoginAttemptAspspConsentDataService() {
        return loginAttemptAspspConsentDataService;
    }

    private void checkBearerTokenPresent(boolean checkCredentials, GlobalScaResponseTO sca) {
        if (checkCredentials && sca.getBearerToken() == null) {
            throw FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, "Missing credentials. Expecting a bearer token in the consent data object.");
        }
    }

    private String readType(byte[] tokenBytes) throws IOException {
        JsonNode jsonNode = objectMapper.readTree(tokenBytes);
        JsonNode objectType = jsonNode.get("objectType");
        if (objectType == null) {
            return null;
        }
        return objectType.textValue();
    }

    private GlobalScaResponseTO fromBytes(byte[] tokenBytes) throws IOException {
        String type = readType(tokenBytes);
        if (type == null || GlobalScaResponseTO.class.getSimpleName().equals(type)) {
            return objectMapper.readValue(tokenBytes, GlobalScaResponseTO.class);
        } else if (SCAConsentResponseTO.class.getSimpleName().equals(type)) {
            SCAConsentResponseTO scaConsentResponseTO = objectMapper.readValue(tokenBytes, SCAConsentResponseTO.class);
            return scaResponseMapper.toGlobalScaResponse(scaConsentResponseTO);
        } else if (SCALoginResponseTO.class.getSimpleName().equals(type)) {
            SCALoginResponseTO scaLoginResponseTO = objectMapper.readValue(tokenBytes, SCALoginResponseTO.class);
            return scaResponseMapper.toGlobalScaResponse(scaLoginResponseTO);
        } else if (SCAPaymentResponseTO.class.getSimpleName().equals(type)) {
            SCAPaymentResponseTO scaPaymentResponseTO = objectMapper.readValue(tokenBytes, SCAPaymentResponseTO.class);
            return scaResponseMapper.toGlobalScaResponse(scaPaymentResponseTO);
        } else {
            return null;
        }
    }
}
