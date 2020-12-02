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

package de.adorsys.aspsp.xs2a.connector.config.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AspspRemoteUrlsTest {

    private static final String BASE_URL = "http://base.url";

    private AspspRemoteUrls aspspRemoteUrls;

    @BeforeEach
    void setUp() {
        aspspRemoteUrls = new AspspRemoteUrls();
        ReflectionTestUtils.setField(aspspRemoteUrls, "spiMockBaseUrl", BASE_URL);
    }

    @Test
    void getAccountDetailsById() {
        assertEquals("http://base.url/account/{account-id}",
                     aspspRemoteUrls.getAccountDetailsById());
    }

    @Test
    void getBalancesByAccountId() {
        assertEquals("http://base.url/account/{account-id}/balances",
                     aspspRemoteUrls.getBalancesByAccountId());
    }

    @Test
    void getAccountDetailsByPsuId() {
        assertEquals("http://base.url/account/psu/{psu-id}",
                     aspspRemoteUrls.getAccountDetailsByPsuId());
    }

    @Test
    void getAccountDetailsByIban() {
        assertEquals("http://base.url/account/iban/{iban}",
                     aspspRemoteUrls.getAccountDetailsByIban());
    }

    @Test
    void createPayment() {
        assertEquals("http://base.url/payments/",
                     aspspRemoteUrls.createPayment());
    }

    @Test
    void getPaymentStatus() {
        assertEquals("http://base.url/payments/{payment-id}/status",
                     aspspRemoteUrls.getPaymentStatus());
    }

    @Test
    void createBulkPayment() {
        assertEquals("http://base.url/payments/bulk-payments",
                     aspspRemoteUrls.createBulkPayment());
    }

    @Test
    void createPeriodicPayment() {
        assertEquals("http://base.url/payments/create-periodic-payment",
                     aspspRemoteUrls.createPeriodicPayment());
    }

    @Test
    void readTransactionById() {
        assertEquals("http://base.url/transaction/{transaction-id}/{account-id}",
                     aspspRemoteUrls.readTransactionById());
    }

    @Test
    void readTransactionsByPeriod() {
        assertEquals("http://base.url/transaction/{account-id}",
                     aspspRemoteUrls.readTransactionsByPeriod());
    }

    @Test
    void createTransaction() {
        assertEquals("http://base.url/transaction",
                     aspspRemoteUrls.createTransaction());
    }

    @Test
    void getAllowedPaymentProducts() {
        assertEquals("http://base.url/psu/allowed-payment-products/{iban}",
                     aspspRemoteUrls.getAllowedPaymentProducts());
    }

    @Test
    void getPaymentById() {
        assertEquals("http://base.url/payments/{payment-type}/{payment-product}/{paymentId}",
                     aspspRemoteUrls.getPaymentById());
    }

    @Test
    void getScaMethods() {
        assertEquals("http://base.url/psu/sca-methods/{psuId}",
                     aspspRemoteUrls.getScaMethods());
    }

    @Test
    void getGenerateTanConfirmation() {
        assertEquals("http://base.url/consent/confirmation/pis/{psuId}/{sca-method-selected}",
                     aspspRemoteUrls.getGenerateTanConfirmation());
    }

    @Test
    void applyStrongUserAuthorisation() {
        assertEquals("http://base.url/consent/confirmation/pis",
                     aspspRemoteUrls.applyStrongUserAuthorisation());
    }

    @Test
    void getGenerateTanConfirmationForAis() {
        assertEquals("http://base.url/consent/confirmation/ais/{psuId}",
                     aspspRemoteUrls.getGenerateTanConfirmationForAis());
    }

    @Test
    void applyStrongUserAuthorisationForAis() {
        assertEquals("http://base.url/consent/confirmation/ais",
                     aspspRemoteUrls.applyStrongUserAuthorisationForAis());
    }

    @Test
    void cancelPayment() {
        assertEquals("http://base.url/payments/{paymentId}",
                     aspspRemoteUrls.cancelPayment());
    }

    @Test
    void initiatePaymentCancellation() {
        assertEquals("http://base.url/payments/{paymentId}/cancel",
                     aspspRemoteUrls.initiatePaymentCancellation());
    }
}