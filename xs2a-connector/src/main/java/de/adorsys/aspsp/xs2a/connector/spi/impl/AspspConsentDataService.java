package de.adorsys.aspsp.xs2a.connector.spi.impl;

import java.io.IOException;
import java.util.Collections;

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

	/**
	 * Default storage, makes sure there is a bearer token in the response object.
	 * @param response
	 * @param aspspConsentData
	 * @return
	 */
	public AspspConsentData store(SCAResponseTO response, AspspConsentData aspspConsentData){
		return store(response, aspspConsentData, true);
	}

	public AspspConsentData store(SCAResponseTO response, AspspConsentData aspspConsentData, boolean checkCredentials){
		if(checkCredentials && response.getBearerToken()==null) {
			throw new IllegalStateException("Missing credentials. response must contain a bearer token by default.");
		}
		try {
			return aspspConsentData.respondWith(tokenStorageService.toBytes(response));
		} catch (IOException e) {
			throw FeignException.errorStatus(e.getMessage(), error(500));
		}
	}
	
	public <T extends SCAResponseTO> T response(AspspConsentData aspspConsentData, Class<T> klass) {
		return response(aspspConsentData, klass, true);
	}

	public SCAResponseTO response(AspspConsentData aspspConsentData) {
		return response(aspspConsentData, true);
	}

	public SCAResponseTO response(AspspConsentData aspspConsentData, boolean checkCredentials) {
		byte[] aspspConsentDataBytes = aspspConsentData.getAspspConsentData();
		try {
			SCAResponseTO sca = tokenStorageService.fromBytes(aspspConsentDataBytes);
			checkScaPresent(sca);
			checkBearerTokenPresent(checkCredentials, sca);
			return sca;
		} catch (IOException e) {
			throw FeignException.errorStatus(e.getMessage(), error(500));
		}
	}
	
	public <T extends SCAResponseTO> T response(AspspConsentData aspspConsentData, Class<T> klass, boolean checkCredentials) {
		byte[] aspspConsentDataBytes = aspspConsentData.getAspspConsentData();
		try {
			T sca = tokenStorageService.fromBytes(aspspConsentDataBytes, klass);
			checkScaPresent(sca);
			checkBearerTokenPresent(checkCredentials, sca);
			return sca;
		} catch (IOException e) {
			throw FeignException.errorStatus(e.getMessage(), error(500));
		}
	}

	private <T extends SCAResponseTO> void checkBearerTokenPresent(boolean checkCredentials, T sca) {
		if(checkCredentials && sca.getBearerToken()==null) {
			throw FeignException.errorStatus("Missing credentials. Expecting a bearer token in the consent data object.", 
					error(401));
		}
	}

	private <T extends SCAResponseTO> void checkScaPresent(T sca) {
		if(sca==null) {
			throw FeignException.errorStatus("Missing consent data", error(401));
		}
	}

	private Response error(int code) {
		return Response.builder().status(code).headers(Collections.emptyMap()).build();
	}
}
