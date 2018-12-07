package de.adorsys.aspsp.xs2a.spi.impl;

import de.adorsys.ledgers.LedgersRestClient;
import de.adorsys.ledgers.domain.TransactionStatus;
import de.adorsys.ledgers.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.domain.payment.PaymentTypeTO;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiTransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import feign.FeignException;
import feign.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class GeneralPaymentService {
    private static final Logger logger = LoggerFactory.getLogger(PaymentAuthorisationSpiImpl.class);
    private final LedgersRestClient ledgersRestClient;
    private final GeneralAuthorisationService authorisationService;

    public GeneralPaymentService(LedgersRestClient ledgersRestClient, GeneralAuthorisationService authorisationService) {
        this.ledgersRestClient = ledgersRestClient;
        this.authorisationService = authorisationService;
    }

    public SpiResponse<SpiTransactionStatus> getPaymentStatusById(@NotNull PaymentTypeTO paymentType, @NotNull String paymentId, @NotNull AspspConsentData aspspConsentData) {
        try {
            logger.info("Get payment status by id with type={}, and id={}", paymentType, paymentId);
            TransactionStatus response = ledgersRestClient.getPaymentStatusById(paymentId).getBody();
            SpiTransactionStatus status = Optional.ofNullable(response)
                                                  .map(r -> SpiTransactionStatus.valueOf(r.getName()))
                                                  .orElseThrow(() -> FeignException.errorStatus("Request failed, Response was 200, but body was empty!", Response.builder().status(400).build()));
            logger.info("The status was:{}", status);
            return SpiResponse.<SpiTransactionStatus>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .payload(status)
                           .success();
        } catch (FeignException e) {
            return SpiResponse.<SpiTransactionStatus>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .fail(getSpiFailureResponse(e));
        }
    }

    public SpiResponse<SpiResponse.VoidResponse> executePaymentWithoutSca(@NotNull String paymentId, @NotNull PaymentProductTO paymentProduct, @NotNull PaymentTypeTO paymentType, @NotNull AspspConsentData aspspConsentData) {
        logger.info("Executing payment without SCA for paymentId={}, productName={} and paymentType={}", paymentId, paymentProduct, paymentType);
        try {
            TransactionStatus status = ledgersRestClient.executePaymentNoSca(paymentId, paymentProduct, paymentType).getBody();
            Optional.ofNullable(status)
                    .orElseThrow(() -> FeignException.errorStatus("Request failed, Response was 200, but body was empty!", Response.builder().status(400).build()));
            logger.info("The response status was:{}", status);
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .success();
        } catch (FeignException e) {
            logger.error(e.getMessage());
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .fail(getSpiFailureResponse(e));
        }
    }

    public SpiResponse<SpiResponse.VoidResponse> verifyScaAuthorisationAndExecutePayment(@NotNull String paymentId, @NotNull PaymentProductTO paymentProduct, @NotNull PaymentTypeTO paymentType, @NotNull String paymentAsString, @NotNull SpiScaConfirmation spiScaConfirmation, @NotNull AspspConsentData aspspConsentData) {
        SpiResponse<SpiResponse.VoidResponse> authResponse = authorisationService.verifyScaAuthorisation(spiScaConfirmation, paymentAsString, aspspConsentData);
        return authResponse.isSuccessful()
                       ? executePaymentWithoutSca(paymentId, paymentProduct, paymentType, aspspConsentData)
                       : authResponse;
    }

    private SpiResponseStatus getSpiFailureResponse(FeignException e) {
        logger.error(e.getMessage(), e);
        return e.status() == 500
                       ? SpiResponseStatus.TECHNICAL_FAILURE
                       : SpiResponseStatus.LOGICAL_FAILURE;
    }
}
