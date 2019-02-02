package de.adorsys.aspsp.xs2a.connector.spi.impl;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPaymentInfo;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.CommonPaymentSpi;

@Service
public class CommonPaymentSpiImpl implements CommonPaymentSpi {

	@Override
	public @NotNull SpiResponse<SpiPaymentExecutionResponse> executePaymentWithoutSca(@NotNull SpiContextData contextData,
			@NotNull SpiPaymentInfo payment, @NotNull AspspConsentData aspspConsentData) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public @NotNull SpiResponse<SpiPaymentExecutionResponse> verifyScaAuthorisationAndExecutePayment(
			@NotNull SpiContextData contextData, @NotNull SpiScaConfirmation spiScaConfirmation,
			@NotNull SpiPaymentInfo payment, @NotNull AspspConsentData aspspConsentData) {
		// TODO Auto-generated method stub
		return null;
	}

}
