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

package de.adorsys.aspsp.xs2a.remote.connector.cms;

import de.adorsys.aspsp.xs2a.connector.cms.CmsPsuPisClient;
import de.adorsys.ledgers.rest.client.CmsPsuPisRestClient;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CmsPsuPisClientRemoteImpl implements CmsPsuPisClient {
    private final CmsPsuPisRestClient cmsPsuPisRestClient;

    @Override
    public void updatePaymentStatus(String paymentId, TransactionStatus transactionStatus, String instanceId) {
        cmsPsuPisRestClient.updatePaymentStatus(paymentId, transactionStatus.name(), instanceId);
    }
}
