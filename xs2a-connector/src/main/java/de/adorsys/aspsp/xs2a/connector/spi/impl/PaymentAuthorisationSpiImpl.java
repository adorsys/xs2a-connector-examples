/*
 * Copyright 2018-2018 adorsys GmbH & Co KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.adorsys.aspsp.xs2a.connector.spi.impl;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import de.adorsys.aspsp.xs2a.connector.spi.converter.ChallengeDataMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaLoginToPaymentResponseMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodConverter;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCALoginResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import de.adorsys.ledgers.middleware.api.service.TokenStorageService;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.core.sca.ChallengeData;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthenticationObject;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorisationStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiAuthorizationCodeResult;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.PaymentAuthorisationSpi;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;

@Component
public class PaymentAuthorisationSpiImpl implements PaymentAuthorisationSpi {
    private final GeneralAuthorisationService authorisationService;
	private final TokenStorageService tokenStorageService;
	private final ScaMethodConverter scaMethodConverter;
	private final ScaLoginToPaymentResponseMapper scaLoginToPaymentResponseMapper;
	private final ChallengeDataMapper challengeDataMapper;

	public PaymentAuthorisationSpiImpl(GeneralAuthorisationService authorisationService,
			TokenStorageService tokenStorageService, ScaMethodConverter scaMethodConverter,
			ScaLoginToPaymentResponseMapper scaLoginToPaymentResponseMapper, ChallengeDataMapper challengeDataMapper) {
		super();
		this.authorisationService = authorisationService;
		this.tokenStorageService = tokenStorageService;
		this.scaMethodConverter = scaMethodConverter;
		this.scaLoginToPaymentResponseMapper = scaLoginToPaymentResponseMapper;
		this.challengeDataMapper = challengeDataMapper;
	}

	@Override
    public SpiResponse<SpiAuthorisationStatus> authorisePsu(@NotNull SpiContextData contextData, @NotNull SpiPsuData psuLoginData, String pin, SpiPayment spiPayment, AspspConsentData aspspConsentData) {
		
		SCAPaymentResponseTO originalResponse = null;
		
		if(aspspConsentData!=null && aspspConsentData.getAspspConsentData()!=null) {
			try {
				originalResponse = tokenStorageService.fromBytes(aspspConsentData.getAspspConsentData(), SCAPaymentResponseTO.class);
			} catch (IOException e) {
		        return SpiResponse.<SpiAuthorisationStatus>builder()
		        		.message(e.getMessage())
		        		.aspspConsentData(aspspConsentData)
		                .fail(SpiResponseStatus.LOGICAL_FAILURE);
			}
			
		}

        SpiResponse<SpiAuthorisationStatus> authorisePsu = authorisationService.authorisePsu(psuLoginData, pin, aspspConsentData);
        
        if(!authorisePsu.isSuccessful()) {
        	return authorisePsu;
        }
        SCAPaymentResponseTO scaPaymentResponse;
		try {
			scaPaymentResponse = toPaymentConsent(spiPayment, authorisePsu, originalResponse);
		} catch (IOException e) {
	        return SpiResponse.<SpiAuthorisationStatus>builder()
	        		.message(e.getMessage())
	        		.aspspConsentData(aspspConsentData)
	                .fail(SpiResponseStatus.LOGICAL_FAILURE);
		}
		
		AspspConsentData paymentAspspConsentData;
		try {
	        byte[] responseAspspConsentData = tokenStorageService.toBytes(scaPaymentResponse);
	        paymentAspspConsentData = authorisePsu.getAspspConsentData().respondWith(responseAspspConsentData);
		} catch (IOException e) {
	        return SpiResponse.<SpiAuthorisationStatus>builder()
	        		.message(e.getMessage())
	        		.aspspConsentData(aspspConsentData)
	                .fail(SpiResponseStatus.LOGICAL_FAILURE);
		}
//		
//		if(ScaStatusTO.EXEMPTED.equals(scaPaymentResponse.getScaStatus())) {
//			return initiatePaymentOnExemptedSCA(contextData, spiPayment, authorisePsu, paymentAspspConsentData);
//		}

		return new SpiResponse<>(authorisePsu.getPayload(), paymentAspspConsentData);
    }

	@Override
    public SpiResponse<List<SpiAuthenticationObject>> requestAvailableScaMethods(@NotNull SpiContextData contextData, SpiPayment spiPayment, AspspConsentData aspspConsentData) {
    	SCAPaymentResponseTO sca;
		try {
			sca = tokenStorageService.fromBytes(aspspConsentData.getAspspConsentData(), SCAPaymentResponseTO.class);
		} catch (IOException e) {
			// bad credentials
            return SpiResponse.<List<SpiAuthenticationObject>>builder()
            		.message(String.format("Bad credentials %s" + e.getMessage()))
                    .fail(SpiResponseStatus.TECHNICAL_FAILURE);
		}
    	List<ScaUserDataTO> scaMethods = Optional.ofNullable(sca.getScaMethods()).orElse(Collections.emptyList());
		return SpiResponse.<List<SpiAuthenticationObject>>builder().
    			payload(scaMethodConverter.toSpiAuthenticationObjectList(scaMethods)).aspspConsentData(aspspConsentData).success();
    }

    @Override
    public @NotNull SpiResponse<SpiAuthorizationCodeResult> requestAuthorisationCode(@NotNull SpiContextData contextData, @NotNull String authenticationMethodId, @NotNull SpiPayment spiPayment, @NotNull AspspConsentData aspspConsentData) {
    	// Check sca status, if already selected, then return.
    	SCAPaymentResponseTO sca;
		try {
			sca = tokenStorageService.fromBytes(aspspConsentData.getAspspConsentData(), SCAPaymentResponseTO.class);
		} catch (IOException e) {
			// bad credentials
            return SpiResponse.<SpiAuthorizationCodeResult>builder()
            		.message(String.format("Bad credentials %s" + e.getMessage()))
                    .fail(SpiResponseStatus.TECHNICAL_FAILURE);
		}
		
		if(ScaStatusTO.SCAMETHODSELECTED.equals(sca.getScaStatus()) ){
			SpiAuthorizationCodeResult spiAuthorizationCodeResult = new SpiAuthorizationCodeResult();
			ChallengeData challengeData = Optional.ofNullable(challengeDataMapper.toChallengeData(sca.getChallengeData())).orElse(new ChallengeData());
			spiAuthorizationCodeResult.setChallengeData(challengeData);
			spiAuthorizationCodeResult.setSelectedScaMethod(scaMethodConverter.toSpiAuthenticationObject(sca.getChosenScaMethod()));
            return SpiResponse.<SpiAuthorizationCodeResult>builder()
                    .aspspConsentData(aspspConsentData)
                    .payload(spiAuthorizationCodeResult)
                    .success();
		}
    	
        return authorisationService.requestAuthorisationCode(contextData.getPsuData(), authenticationMethodId, spiPayment.getPaymentId(), spiPayment.toString(), aspspConsentData);
    }
	
	private SCAPaymentResponseTO toPaymentConsent(SpiPayment spiPayment, SpiResponse<SpiAuthorisationStatus> authorisePsu, SCAPaymentResponseTO originalResponse) throws IOException {
		String paymentTypeString = Optional.ofNullable(spiPayment.getPaymentType()).orElseThrow(() -> new IOException("Missing payment type")).name();
        SCALoginResponseTO scaResponseTO = tokenStorageService.fromBytes(authorisePsu.getAspspConsentData().getAspspConsentData(), SCALoginResponseTO.class);
        SCAPaymentResponseTO paymentResponse = scaLoginToPaymentResponseMapper.toPaymentResponse(scaResponseTO);
        paymentResponse.setObjectType(SCAPaymentResponseTO.class.getSimpleName());
        paymentResponse.setPaymentId(spiPayment.getPaymentId());
        paymentResponse.setPaymentType(PaymentTypeTO.valueOf(paymentTypeString));
        String paymentProduct2 = spiPayment.getPaymentProduct();
        if(paymentProduct2==null && originalResponse!=null && originalResponse.getPaymentProduct()!=null) {
        	paymentProduct2 = originalResponse.getPaymentProduct().getValue();
        } else {
        	throw new IOException("Missing payment product");
        }
        final String pp = paymentProduct2;
        paymentResponse.setPaymentProduct(PaymentProductTO.getByValue(paymentProduct2).orElseThrow(() -> new IOException(String.format("Unsupported payment product ", pp))));
        return paymentResponse;
	}
    
}
