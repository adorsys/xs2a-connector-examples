package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.type;

import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation.confirmation.PaymentAuthConfirmationCodeService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiCheckConfirmationCodeRequest;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiGetPaymentStatusResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentConfirmationCodeValidationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractPaymentSpi<P extends SpiPayment, R extends SpiPaymentInitiationResponse> {

    protected final GeneralPaymentService paymentService;
    protected final AspspConsentDataService consentDataService;
    protected final PaymentAuthConfirmationCodeService paymentAuthConfirmationCodeService;

    /*
     * Initiating a payment you need a valid bearer token if not we just return ok.
     */
    public @NotNull SpiResponse<R> initiatePayment(@NotNull SpiContextData contextData, @NotNull P payment, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return processEmptyAspspConsentData(payment, aspspConsentDataProvider, contextData.getPsuData());
    }

    public @NotNull SpiResponse<SpiGetPaymentStatusResponse> getPaymentStatusById(@NotNull SpiContextData contextData,
                                                                                  @NotNull String acceptMediaType,
                                                                                  @NotNull P payment,
                                                                                  @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return paymentService.getPaymentStatusById(PaymentTypeTO.valueOf(payment.getPaymentType().name()),
                                                   acceptMediaType,
                                                   payment.getPaymentId(),
                                                   payment.getPaymentStatus(),
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

    public @NotNull SpiResponse<SpiPaymentExecutionResponse> verifyScaAuthorisationAndExecutePaymentWithPaymentResponse(@NotNull SpiContextData contextData,
                                                                                                               @NotNull SpiScaConfirmation spiScaConfirmation,
                                                                                                               @NotNull P payment,
                                                                                                               @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return paymentService.verifyScaAuthorisationAndExecutePaymentWithPaymentResponse(spiScaConfirmation, aspspConsentDataProvider);
    }

    public @NotNull SpiResponse<SpiPaymentConfirmationCodeValidationResponse> checkConfirmationCode(@NotNull SpiContextData contextData,
                                                                                                    @NotNull SpiCheckConfirmationCodeRequest spiCheckConfirmationCodeRequest,
                                                                                                    @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return paymentAuthConfirmationCodeService.checkConfirmationCode(spiCheckConfirmationCodeRequest, aspspConsentDataProvider);
    }

    public @NotNull SpiResponse<SpiPaymentConfirmationCodeValidationResponse> notifyConfirmationCodeValidation(@NotNull SpiContextData spiContextData, boolean confirmationCodeValidationResult, @NotNull P payment, boolean isCancellation, @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return paymentAuthConfirmationCodeService.completeAuthConfirmation(confirmationCodeValidationResult, aspspConsentDataProvider);
    }

    public boolean checkConfirmationCodeInternally(String authorisationId, String confirmationCode, String scaAuthenticationData,
                                                   @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return paymentAuthConfirmationCodeService.checkConfirmationCodeInternally(authorisationId, confirmationCode, scaAuthenticationData, aspspConsentDataProvider);
    }

    protected abstract SpiResponse<R> processEmptyAspspConsentData(@NotNull P payment,
                                                                   @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider,
                                                                   @NotNull SpiPsuData spiPsuData);
}
