package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.type;

import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionHandler;
import de.adorsys.aspsp.xs2a.connector.spi.impl.FeignExceptionReader;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiGetPaymentStatusResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractPaymentSpi<P extends SpiPayment, R extends SpiPaymentInitiationResponse> {

    private final GeneralPaymentService paymentService;
    private final AspspConsentDataService consentDataService;
    private final FeignExceptionReader feignExceptionReader;

    /*
     * Initiating a payment you need a valid bearer token if not we just return ok.
     */
    public @NotNull SpiResponse<R> initiatePayment(@NotNull SpiContextData contextData,
                                                   @NotNull P payment,
                                                   @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        byte[] initialAspspConsentData = aspspConsentDataProvider.loadAspspConsentData();
        if (ArrayUtils.isEmpty(initialAspspConsentData)) {
            return processEmptyAspspConsentData(payment, aspspConsentDataProvider);
        }
        try {
            SCAPaymentResponseTO response = initiatePaymentInternal(payment, initialAspspConsentData);
            R spiInitiationResponse = Optional.ofNullable(response)
                                              .map(this::getToSpiPaymentResponse)
                                              .orElseThrow(() -> FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "Request failed, Response was 201, but body was empty!"));
            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(response));


            String scaStatusName = response.getScaStatus().name();
            log.info("SCA status is: {}", scaStatusName);

            return SpiResponse.<R>builder()
                           .payload(spiInitiationResponse)
                           .build();
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            log.error("Initiate payment failed: payment ID {}, devMessage {}", payment.getPaymentId(), devMessage);
            return SpiResponse.<R>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.PAYMENT_FAILED, devMessage))
                           .build();
        } catch (IllegalStateException e) {
            return SpiResponse.<R>builder()
                           .error(new TppMessage(MessageErrorCode.PAYMENT_FAILED))
                           .build();
        }
    }

    public @NotNull SpiResponse<SpiGetPaymentStatusResponse> getPaymentStatusById(@NotNull SpiContextData contextData,
                                                                                  @NotNull P payment,
                                                                                  @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return paymentService.getPaymentStatusById(PaymentTypeTO.valueOf(payment.getPaymentType().name()),
                                                   payment.getPaymentId(), payment.getPaymentStatus(),
                                                   aspspConsentDataProvider.loadAspspConsentData());
    }

    /*
     * This attempt to execute payment without sca can only work if the core banking system decides that there is no
     * sca required. If this is the case, the payment would have been executed in the initiation phase after
     * the first login of the user.
     *
     * In sure, the core banking considers it like any other payment initiation. If the status is ScsStatus.EXEMPTED
     * then we are fine. If not the user will be required to proceed with sca.
     *
     */
    public @NotNull SpiResponse<SpiPaymentExecutionResponse> executePaymentWithoutSca(@NotNull SpiContextData contextData,
                                                                                      @NotNull P payment,
                                                                                      @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return paymentService.executePaymentWithoutSca(aspspConsentDataProvider);
    }

    public @NotNull SpiResponse<SpiPaymentExecutionResponse> verifyScaAuthorisationAndExecutePayment(@NotNull SpiContextData contextData,
                                                                                                     @NotNull SpiScaConfirmation spiScaConfirmation,
                                                                                                     @NotNull P payment,
                                                                                                     @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return paymentService.verifyScaAuthorisationAndExecutePayment(spiScaConfirmation, aspspConsentDataProvider);
    }


    protected abstract SCAPaymentResponseTO initiatePaymentInternal(P payment, byte[] initialAspspConsentData);

    protected abstract SpiResponse<R> processEmptyAspspConsentData(@NotNull P payment,
                                                                   @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider);

    @NotNull
    protected abstract R getToSpiPaymentResponse(SCAPaymentResponseTO response);

}
