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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AspspConsentDataServiceTest {

    private AspspConsentDataService aspspConsentDataService;
    private LoginAttemptAspspConsentDataService loginAttemptAspspConsentDataService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        aspspConsentDataService = new AspspConsentDataService(objectMapper, null,
                                                              new LoginAttemptAspspConsentDataService(objectMapper));
        loginAttemptAspspConsentDataService = aspspConsentDataService.getLoginAttemptAspspConsentDataService();
    }

    @Test
    void loginAttemptResponse() {
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
}