package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.middleware.api.service.TokenStorageService;
import de.adorsys.ledgers.rest.client.CmsPsuPisClient;
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
    private final CmsPsuPisClient consentRestClient;
    private final TokenStorageService tokenStorageService;

    public CmsPaymentStatusUpdateService(CmsPsuPisClient consentRestClient, TokenStorageService tokenStorageService) {
        this.consentRestClient = consentRestClient;
        this.tokenStorageService = tokenStorageService;
    }

    public void updatePaymentStatus(String paymentId, SpiAspspConsentDataProvider aspspConsentDataProvider) {
        try {
            SCAResponseTO sca = tokenStorageService.fromBytes(aspspConsentDataProvider.loadAspspConsentData());
            String transactionStatus = getTransactionStatus(sca.getScaStatus());
            consentRestClient.updatePaymentStatus(paymentId, transactionStatus, "UNDEFINED");
        } catch (IOException e) {
            logger.error("Could not extract data from token", e);
        }
    }

    private String getTransactionStatus(ScaStatusTO scaStatus) {
        if (EnumSet.of(PSUIDENTIFIED, EXEMPTED).contains(scaStatus)) {
            return "ACCP";
        } else if (scaStatus == PSUAUTHENTICATED) {
            return "ACTC";
        } else {
            return "RCVD";
        }
    }
}
