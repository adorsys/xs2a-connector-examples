package de.adorsys.aspsp.xs2a.connector.spi.impl;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import de.adorsys.ledgers.middleware.api.service.TokenStorageService;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import feign.FeignException;
import feign.Response;

@Service
public class AspspConsentDataService {
	
	@Autowired
	private TokenStorageService tokenStorageService;

	public AspspConsentData store(SCAResponseTO response, AspspConsentData aspspConsentData){
		byte[] bytes;
		try {
			bytes = tokenStorageService.toBytes(response);
		} catch (IOException e) {
			throw FeignException.errorStatus(e.getMessage(), Response.builder().status(500).build());
		}
		return aspspConsentData.respondWith(bytes);
	}

	public <T extends SCAResponseTO> T response(AspspConsentData aspspConsentData, Class<T> klass) {
		byte[] aspspConsentDataBytes = aspspConsentData.getAspspConsentData();
		try {
			T sca = tokenStorageService.fromBytes(aspspConsentDataBytes, klass);
			if(sca==null || sca.getBearerToken()==null) {
				throw FeignException.errorStatus("Missing credentials", Response.builder().status(401).build());
			}
			return sca;
		} catch (IOException e) {
			throw FeignException.errorStatus(e.getMessage(), Response.builder().status(500).build());
		}
	}

	public SCAResponseTO response(AspspConsentData aspspConsentData) {
		byte[] aspspConsentDataBytes = aspspConsentData.getAspspConsentData();
		try {
			SCAResponseTO sca = tokenStorageService.fromBytes(aspspConsentDataBytes);
			if(sca==null || sca.getBearerToken()==null) {
				throw FeignException.errorStatus("Missing credentials", Response.builder().status(401).build());
			}
			return sca;
		} catch (IOException e) {
			throw FeignException.errorStatus(e.getMessage(), Response.builder().status(500).build());
		}
	}
}
