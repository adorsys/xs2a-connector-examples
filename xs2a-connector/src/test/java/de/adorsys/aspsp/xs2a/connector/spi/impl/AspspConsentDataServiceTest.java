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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.middleware.api.domain.um.AccessTokenTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AspspConsentDataServiceTest {

    private static final String STRING_TO_SERIALIZE = "string for checking";
    private static final byte[] BYTES = "data".getBytes();

    @InjectMocks
    private AspspConsentDataService aspspConsentDataService;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void loginAttemptResponse() {
        // Given
        ObjectMapper objectMapper = new ObjectMapper();
        aspspConsentDataService = new AspspConsentDataService(objectMapper, null,
                                                              new LoginAttemptAspspConsentDataService(objectMapper));
        // When
        LoginAttemptAspspConsentDataService loginAttemptAspspConsentDataService = aspspConsentDataService.getLoginAttemptAspspConsentDataService();

        // Then
        LoginAttemptResponse response = new LoginAttemptResponse();
        assertEquals(1, response.getLoginFailedCount());

        byte[] bytes = loginAttemptAspspConsentDataService.store(response);
        assertNotNull(bytes);
        LoginAttemptResponse fromBytes = loginAttemptAspspConsentDataService.response(bytes);
        assertEquals(response, fromBytes);

        response.incrementLoginFailedCount();
        assertEquals(2, response.getLoginFailedCount());
        bytes = loginAttemptAspspConsentDataService.store(response);
        assertNotNull(bytes);
        fromBytes = loginAttemptAspspConsentDataService.response(bytes);
        assertEquals(response, fromBytes);
    }

    @Test
    void store_withouf_flag_success() throws JsonProcessingException {
        // Given
        GlobalScaResponseTO scaResponseTO = getScaResponse();
        when(objectMapper.writeValueAsBytes(scaResponseTO))
                .thenReturn(STRING_TO_SERIALIZE.getBytes());

        // When
        byte[] actual = aspspConsentDataService.store(scaResponseTO);

        // Then
        verify(objectMapper, times(1)).writeValueAsBytes(scaResponseTO);
        assertEquals(STRING_TO_SERIALIZE, new String(actual));
    }

    @Test
    void store_withouf_flag_fail() {
        // Given
        GlobalScaResponseTO scaResponseTO = getScaResponse();
        scaResponseTO.setBearerToken(null);

        // Then
        assertThrows(IllegalStateException.class, () -> aspspConsentDataService.store(scaResponseTO));
    }

    @Test
    void store_withouf_flag_fail_jackson() throws JsonProcessingException {
        // Given
        GlobalScaResponseTO scaResponseTO = getScaResponse();

        when(objectMapper.writeValueAsBytes(scaResponseTO))
                .thenAnswer(invocation -> {
                    throw new IOException();
                });

        // Then
        assertThrows(FeignException.class, () -> aspspConsentDataService.store(scaResponseTO));
    }

    @Test
    void response_success() throws IOException {
        // Given
        when(objectMapper.readTree(BYTES))
                .thenReturn(new TextNode("data"));
        when(objectMapper.readValue(BYTES, GlobalScaResponseTO.class))
                .thenReturn(getScaResponse());
        // When
        GlobalScaResponseTO actual = aspspConsentDataService.response(BYTES);

        // Then
        assertEquals(getScaResponse(), actual);
    }

    @Test
    void response_fail_jackson() throws IOException {
        // Given
        when(objectMapper.readTree(BYTES))
                .thenReturn(new TextNode("data"));

        when(objectMapper.readValue(BYTES, GlobalScaResponseTO.class))
                .thenAnswer(invocation -> {
                    throw new IOException();
                });

        // Then
        assertThrows(FeignException.class, () -> aspspConsentDataService.response(BYTES));
    }

    @Test
    void response_fail_no_token() throws IOException {
        // Given
        when(objectMapper.readTree(BYTES))
                .thenReturn(new TextNode("data"));

        GlobalScaResponseTO globalScaResponseTO = getScaResponse();
        globalScaResponseTO.setBearerToken(null);
        when(objectMapper.readValue(BYTES, GlobalScaResponseTO.class))
                .thenReturn(globalScaResponseTO);

        // Then
        assertThrows(FeignException.class, () -> aspspConsentDataService.response(BYTES));
    }

    private GlobalScaResponseTO getScaResponse() {
        GlobalScaResponseTO sca = new GlobalScaResponseTO();
        BearerTokenTO token = new BearerTokenTO();
        token.setExpires_in(100);
        token.setAccessTokenObject(new AccessTokenTO());
        token.setRefresh_token("refresh_token");
        token.setAccess_token("access_token");
        sca.setBearerToken(token);

        return sca;
    }
}