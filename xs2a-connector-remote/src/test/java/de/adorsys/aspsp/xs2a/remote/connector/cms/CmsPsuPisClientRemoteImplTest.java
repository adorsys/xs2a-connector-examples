package de.adorsys.aspsp.xs2a.remote.connector.cms;

import de.adorsys.ledgers.rest.client.CmsPsuPisRestClient;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CmsPsuPisClientRemoteImplTest {
    private static final String PAYMENT_ID = "some payment id";
    private static final TransactionStatus TRANSACTION_STATUS = TransactionStatus.ACSP;
    private static final String INSTANCE_ID = "UNDEFINED";

    @Mock
    private CmsPsuPisRestClient cmsPsuPisRestClient;

    @InjectMocks
    private CmsPsuPisClientRemoteImpl cmsPsuPisClientRemote;

    @Test
    void updatePaymentStatus_shouldExecuteFeignClientMethod() {
        // When
        cmsPsuPisClientRemote.updatePaymentStatus(PAYMENT_ID, TRANSACTION_STATUS, INSTANCE_ID);

        // Then
        verify(cmsPsuPisRestClient).updatePaymentStatus(PAYMENT_ID, TRANSACTION_STATUS.name(), INSTANCE_ID);
    }
}