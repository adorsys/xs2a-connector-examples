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

package de.adorsys.aspsp.xs2a.connector.mock;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IbanResolverMockServiceTest {
    private static final String IBAN_NONE_SCA = "DE69760700240340283600";
    private static final String MASKED_PAN_NONE_SCA = "493702******0836";
    private static final String MASKED_PAN_UNKNOWN = "121200******9977";

    @Test
    void getIbanByMaskedPan_validMaskedPan_shouldReturnIban() {
        IbanResolverMockService ibanResolverMockService = new IbanResolverMockService();
        ibanResolverMockService.setup();

        Optional<String> actualResult = ibanResolverMockService.getIbanByMaskedPan(MASKED_PAN_NONE_SCA);

        assertTrue(actualResult.isPresent());
        assertEquals(IBAN_NONE_SCA, actualResult.get());
    }

    @Test
    void getIbanByMaskedPan_unknownMaskedPan_shouldReturnEmpty() {
        IbanResolverMockService ibanResolverMockService = new IbanResolverMockService();
        ibanResolverMockService.setup();

        Optional<String> actualResult = ibanResolverMockService.getIbanByMaskedPan(MASKED_PAN_UNKNOWN);

        assertFalse(actualResult.isPresent());
    }
}