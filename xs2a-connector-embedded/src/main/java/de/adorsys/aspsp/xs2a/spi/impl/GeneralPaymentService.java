package de.adorsys.aspsp.xs2a.spi.impl;

import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import de.adorsys.ledgers.middleware.api.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.payment.TransactionStatusTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiTransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import feign.FeignException;
import feign.Response;

@Component
public class GeneralPaymentService {
    private static final Logger logger = LoggerFactory.getLogger(PaymentAuthorisationSpiImpl.class);
    private final PaymentRestClient paymentRestClient;
	private final AuthRequestInterceptor authRequestInterceptor;
	private final AspspConsentDataService tokenService;

	public GeneralPaymentService(PaymentRestClient ledgersRestClient,
			AuthRequestInterceptor authRequestInterceptor, AspspConsentDataService tokenService) {
		super();
		this.paymentRestClient = ledgersRestClient;
		this.authRequestInterceptor = authRequestInterceptor;
		this.tokenService = tokenService;
	}

	public SpiResponse<SpiTransactionStatus> getPaymentStatusById(@NotNull PaymentTypeTO paymentType, @NotNull String paymentId, @NotNull AspspConsentData aspspConsentData) {
		try {
			SCAResponseTO sca = tokenService.response(aspspConsentData);
			authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

			logger.info("Get payment status by id with type={}, and id={}", paymentType, paymentId);
            TransactionStatusTO response = paymentRestClient.getPaymentStatusById(paymentId).getBody();
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
		} finally {
			authRequestInterceptor.setAccessToken(null);
		}
    }

    public SpiResponse<SpiResponse.VoidResponse> verifyScaAuthorisationAndExecutePayment(@NotNull String paymentId, @NotNull PaymentProductTO paymentProduct, @NotNull PaymentTypeTO paymentType, @NotNull String paymentAsString, @NotNull SpiScaConfirmation spiScaConfirmation, @NotNull AspspConsentData aspspConsentData) {
		try {
			SCAPaymentResponseTO sca = tokenService.response(aspspConsentData, SCAPaymentResponseTO.class);
			authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());

			ResponseEntity<SCAPaymentResponseTO> authorizePaymentResponse = paymentRestClient.authorizePayment(sca.getPaymentId(), sca.getAuthorisationId(), spiScaConfirmation.getTanNumber());
			SCAPaymentResponseTO consentResponse = authorizePaymentResponse.getBody();

			return SpiResponse.<SpiResponse.VoidResponse>builder().payload(SpiResponse.voidResponse())
					.aspspConsentData(tokenService.store(consentResponse, aspspConsentData))
					.message(consentResponse.getScaStatus().name()).success();
		} finally {
			authRequestInterceptor.setAccessToken(null);
		}
    }

    private SpiResponseStatus getSpiFailureResponse(FeignException e) {
        logger.error(e.getMessage(), e);
        return e.status() == 500
                       ? SpiResponseStatus.TECHNICAL_FAILURE
                       : SpiResponseStatus.LOGICAL_FAILURE;
    }
}
