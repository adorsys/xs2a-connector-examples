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
