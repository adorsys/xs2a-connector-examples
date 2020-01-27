package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.aspsp.xs2a.connector.cms.CmsPsuPisClient;
import de.adorsys.ledgers.middleware.api.domain.sca.SCALoginResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.middleware.api.service.TokenStorageService;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CmsPaymentStatusUpdateServiceTest {
    private static final String PAYMENT_ID = "some payment id";
    private static final String INSTANCE_ID = "UNDEFINED";
    private static final byte[] ASPSP_CONSENT_DATA = "some ASPSP consent Data".getBytes();

    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;
    @Mock
    private TokenStorageService tokenStorageService;
    @Mock
    private CmsPsuPisClient cmsPsuPisClient;

    @InjectMocks
    private CmsPaymentStatusUpdateService cmsPaymentStatusUpdateService;

    @Test
    void updatePaymentStatus_withIdentifiedAuthorisation_shouldUpdateToAccp() throws IOException {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(ASPSP_CONSENT_DATA);
        SCALoginResponseTO scaLoginResponse = new SCALoginResponseTO();
        scaLoginResponse.setScaStatus(ScaStatusTO.PSUIDENTIFIED);
        when(tokenStorageService.fromBytes(ASPSP_CONSENT_DATA)).thenReturn(scaLoginResponse);

        // When
        cmsPaymentStatusUpdateService.updatePaymentStatus(PAYMENT_ID, spiAspspConsentDataProvider);

        // Then
        verify(cmsPsuPisClient).updatePaymentStatus(PAYMENT_ID, TransactionStatus.ACCP, INSTANCE_ID);
    }

    @Test
    void updatePaymentStatus_withExemptedAuthorisation_shouldUpdateToAccp() throws IOException {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(ASPSP_CONSENT_DATA);
        SCALoginResponseTO scaLoginResponse = new SCALoginResponseTO();
        scaLoginResponse.setScaStatus(ScaStatusTO.EXEMPTED);
        when(tokenStorageService.fromBytes(ASPSP_CONSENT_DATA)).thenReturn(scaLoginResponse);

        // When
        cmsPaymentStatusUpdateService.updatePaymentStatus(PAYMENT_ID, spiAspspConsentDataProvider);

        // Then
        verify(cmsPsuPisClient).updatePaymentStatus(PAYMENT_ID, TransactionStatus.ACCP, INSTANCE_ID);
    }

    @Test
    void updatePaymentStatus_withAuthenticatedAuthorisation_shouldUpdateToActc() throws IOException {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(ASPSP_CONSENT_DATA);
        SCALoginResponseTO scaLoginResponse = new SCALoginResponseTO();
        scaLoginResponse.setScaStatus(ScaStatusTO.PSUAUTHENTICATED);
        when(tokenStorageService.fromBytes(ASPSP_CONSENT_DATA)).thenReturn(scaLoginResponse);

        // When
        cmsPaymentStatusUpdateService.updatePaymentStatus(PAYMENT_ID, spiAspspConsentDataProvider);

        // Then
        verify(cmsPsuPisClient).updatePaymentStatus(PAYMENT_ID, TransactionStatus.ACTC, INSTANCE_ID);
    }

    @Test
    void updatePaymentStatus_withOtherAuthorisationStatus_shouldUpdateToRcvd() throws IOException {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(ASPSP_CONSENT_DATA);
        SCALoginResponseTO scaLoginResponse = new SCALoginResponseTO();
        scaLoginResponse.setScaStatus(ScaStatusTO.STARTED);
        when(tokenStorageService.fromBytes(ASPSP_CONSENT_DATA)).thenReturn(scaLoginResponse);

        // When
        cmsPaymentStatusUpdateService.updatePaymentStatus(PAYMENT_ID, spiAspspConsentDataProvider);

        // Then
        verify(cmsPsuPisClient).updatePaymentStatus(PAYMENT_ID, TransactionStatus.RCVD, INSTANCE_ID);
    }

    @Test
    void updatePaymentStatus_withExceptionOnReadingToken_shouldSkipUpdate() throws IOException {
        // Given
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(ASPSP_CONSENT_DATA);
        when(tokenStorageService.fromBytes(ASPSP_CONSENT_DATA)).thenThrow(IOException.class);

        // When
        cmsPaymentStatusUpdateService.updatePaymentStatus(PAYMENT_ID, spiAspspConsentDataProvider);

        // Then
        verify(cmsPsuPisClient, never()).updatePaymentStatus(any(), any(), any());
    }
}