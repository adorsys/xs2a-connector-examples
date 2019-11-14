package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.aspsp.xs2a.connector.cms.CmsPsuPisClient;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.middleware.api.service.TokenStorageService;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.EnumSet;

import static de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO.*;

@Service
public class CmsPaymentStatusUpdateService {
    private static final Logger logger = LoggerFactory.getLogger(CmsPaymentStatusUpdateService.class);
    private final CmsPsuPisClient cmsPsuPisClient;
    private final TokenStorageService tokenStorageService;

    public CmsPaymentStatusUpdateService(CmsPsuPisClient cmsPsuPisClient, TokenStorageService tokenStorageService) {
        this.cmsPsuPisClient = cmsPsuPisClient;
        this.tokenStorageService = tokenStorageService;
    }

    public void updatePaymentStatus(String paymentId, SpiAspspConsentDataProvider aspspConsentDataProvider) {
        try {
            SCAResponseTO sca = tokenStorageService.fromBytes(aspspConsentDataProvider.loadAspspConsentData());
            TransactionStatus transactionStatus = getTransactionStatus(sca.getScaStatus());
            cmsPsuPisClient.updatePaymentStatus(paymentId, transactionStatus, "UNDEFINED");
        } catch (IOException e) {
            logger.error("Could not extract data from token", e);
        }
    }

    private TransactionStatus getTransactionStatus(ScaStatusTO scaStatus) {
        if (EnumSet.of(PSUIDENTIFIED, EXEMPTED).contains(scaStatus)) {
            return TransactionStatus.ACCP;
        } else if (scaStatus == PSUAUTHENTICATED) {
            return TransactionStatus.ACTC;
        } else {
            return TransactionStatus.RCVD;
        }
    }
}
