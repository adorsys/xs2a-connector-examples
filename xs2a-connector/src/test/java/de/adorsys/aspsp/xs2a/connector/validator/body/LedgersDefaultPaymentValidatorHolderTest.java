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

package de.adorsys.aspsp.xs2a.connector.validator.body;

import de.adorsys.psd2.xs2a.service.validator.pis.payment.raw.DefaultPaymentBusinessValidatorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgersDefaultPaymentValidatorHolderTest {

    private LedgersDefaultPaymentValidatorHolder holder;

    @BeforeEach
    void setUp() {
        holder = new LedgersDefaultPaymentValidatorHolder(new LedgersPaymentBodyFieldsValidatorImpl(null, null),
                                                          new DefaultPaymentBusinessValidatorImpl(null, null, null));
    }

    @Test
    void getCountryIdentifier() {
        assertEquals("DE", holder.getCountryIdentifier());
    }

    @Test
    void getPaymentBodyFieldsValidator() {
        assertTrue(holder.getPaymentBodyFieldsValidator() instanceof LedgersPaymentBodyFieldsValidatorImpl);
    }

    @Test
    void getPaymentBusinessValidator() {
        assertTrue(holder.getPaymentBusinessValidator() instanceof DefaultPaymentBusinessValidatorImpl);
    }

    @Test
    void isCustom() {
        assertTrue(holder.isCustom());
    }
}