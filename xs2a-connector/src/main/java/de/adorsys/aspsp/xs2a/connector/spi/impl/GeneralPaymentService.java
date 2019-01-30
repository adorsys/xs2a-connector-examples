package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.ledgers.middleware.api.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.payment.TransactionStatusTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.ledgers.util.Ids;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiTransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import feign.FeignException;
import feign.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
public class GeneralPaymentService {
    private static final Logger logger = LoggerFactory.getLogger(PaymentAuthorisationSpiImpl.class);
    private final PaymentRestClient paymentRestClient;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;

    public GeneralPaymentService(PaymentRestClient ledgersRestClient,
                                 AuthRequestInterceptor authRequestInterceptor, AspspConsentDataService consentDataService) {
        super();
        this.paymentRestClient = ledgersRestClient;
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
    }

    public SpiResponse<SpiTransactionStatus> getPaymentStatusById(@NotNull PaymentTypeTO paymentType, @NotNull String paymentId, @NotNull AspspConsentData aspspConsentData) {
        try {
            SCAPaymentResponseTO sca = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            logger.info("Get payment status by id with type={}, and id={}", paymentType, paymentId);
            TransactionStatusTO response = paymentRestClient.getPaymentStatusById(sca.getPaymentId()).getBody();
            SpiTransactionStatus status = Optional.ofNullable(response)
                                                  .map(r -> SpiTransactionStatus.valueOf(r.name()))
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
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    public SpiResponse<SpiResponse.VoidResponse> verifyScaAuthorisationAndExecutePayment(@NotNull SpiScaConfirmation spiScaConfirmation, @NotNull AspspConsentData aspspConsentData) {
        try {
            SCAPaymentResponseTO sca = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class);
            authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

            ResponseEntity<SCAPaymentResponseTO> authorizePaymentResponse = paymentRestClient.authorizePayment(sca.getPaymentId(), sca.getAuthorisationId(), spiScaConfirmation.getTanNumber());
            SCAPaymentResponseTO consentResponse = authorizePaymentResponse.getBody();

            return SpiResponse.<SpiResponse.VoidResponse>builder().payload(SpiResponse.voidResponse())
                           .aspspConsentData(consentDataService.store(consentResponse, aspspConsentData))
                           .message(consentResponse.getScaStatus().name()).success();
        } catch (Exception e) {
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .fail(SpiResponseStatus.LOGICAL_FAILURE);
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    /**
     * Instantiating the very first response object.
     *
     * @param paymentType             the payment type
     * @param payment                 the payment object
     * @param initialAspspConsentData the credential data container
     * @param responsePayload         the instantiated payload object
     * @return
     */
    public <T extends SpiPaymentInitiationResponse> SpiResponse<T> firstCallInstantiatingPayment(
            @NotNull PaymentTypeTO paymentType, @NotNull SpiPayment payment,
            @NotNull AspspConsentData initialAspspConsentData, T responsePayload) {
        String paymentId = initialAspspConsentData.getConsentId() != null
                                   ? initialAspspConsentData.getConsentId()
                                   : Ids.id();
        SCAPaymentResponseTO response = new SCAPaymentResponseTO();
        response.setPaymentId(paymentId);
        response.setTransactionStatus(TransactionStatusTO.RCVD);
        response.setPaymentProduct(PaymentProductTO.getByValue(payment.getPaymentProduct()).orElse(null));
        response.setPaymentType(paymentType);
        responsePayload.setPaymentId(paymentId);
//		responsePayload.setAspspAccountId();// TODO ID of the deposit account
        responsePayload.setTransactionStatus(SpiTransactionStatus.valueOf(response.getTransactionStatus().name()));
        return SpiResponse.<T>builder()
                       .aspspConsentData(consentDataService.store(response, initialAspspConsentData, false))
                       .payload(responsePayload).success();
    }

    public <T> @NotNull SpiResponse<SpiResponse.VoidResponse> executePaymentWithoutSca(@NotNull SpiContextData contextData, @NotNull T payment, @NotNull AspspConsentData aspspConsentData) {
        try {
            // First check if there is any payment response ongoing.
            SCAPaymentResponseTO response = consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class);

            if (ScaStatusTO.EXEMPTED.equals(response.getScaStatus())
                        || ScaStatusTO.FINALISED.equals(response.getScaStatus())) {
                // Success
                List<String> messages = Arrays.asList(response.getScaStatus().name(),
                        String.format("Payment scheduled for execution. Transaction status is %s. Als see sca status",
                                response.getTransactionStatus()));
                return SpiResponse.<SpiResponse.VoidResponse>builder()
                               .aspspConsentData(aspspConsentData).message(messages)
                               .payload(SpiResponse.voidResponse()).success();
            }
            List<String> messages = Arrays.asList(response.getScaStatus().name(),
                    String.format("Payment not executed. Transaction status is %s. Als see sca status",
                            response.getTransactionStatus()));
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .aspspConsentData(consentDataService.store(response, aspspConsentData)).message(messages)
                           .fail(SpiResponseStatus.LOGICAL_FAILURE);
        } catch (FeignException e) {
            return SpiResponse.<SpiResponse.VoidResponse>builder()
                           .aspspConsentData(aspspConsentData.respondWith(aspspConsentData.getAspspConsentData()))
                           .fail(getSpiFailureResponse(e));
        }
    }


    private SpiResponseStatus getSpiFailureResponse(FeignException e) {
        logger.error(e.getMessage(), e);
        return e.status() == 500
                       ? SpiResponseStatus.TECHNICAL_FAILURE
                       : SpiResponseStatus.LOGICAL_FAILURE;
    }
}
