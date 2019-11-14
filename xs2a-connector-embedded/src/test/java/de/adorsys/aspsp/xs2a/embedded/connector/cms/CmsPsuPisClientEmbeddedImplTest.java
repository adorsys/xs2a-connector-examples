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

package de.adorsys.aspsp.xs2a.embedded.connector.cms;

import de.adorsys.psd2.consent.psu.api.CmsPsuPisService;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class CmsPsuPisClientEmbeddedImplTest {
    private static final String PAYMENT_ID = "some payment id";
    private static final TransactionStatus TRANSACTION_STATUS = TransactionStatus.ACSP;
    private static final String INSTANCE_ID = "UNDEFINED";

    @Mock
    private CmsPsuPisService cmsPsuPisService;

    @InjectMocks
    private CmsPsuPisClientEmbeddedImpl cmsPsuPisClientEmbedded;

    @Test
    public void updatePaymentStatus_shouldExecuteCmsMethod() {
        // When
        cmsPsuPisClientEmbedded.updatePaymentStatus(PAYMENT_ID, TRANSACTION_STATUS, INSTANCE_ID);

        // Then
        verify(cmsPsuPisService).updatePaymentStatus(PAYMENT_ID, TRANSACTION_STATUS, INSTANCE_ID);
    }
}