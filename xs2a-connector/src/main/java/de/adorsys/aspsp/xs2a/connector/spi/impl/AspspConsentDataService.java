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
			throw FeignException.errorStatus(e.getMessage(), Response.builder().status(500).build());
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
			throw FeignException.errorStatus(e.getMessage(), Response.builder().status(500).build());
		}
	}

	private <T extends SCAResponseTO> void checkBearerTokenPresent(boolean checkCredentials, T sca) {
		if(checkCredentials && sca.getBearerToken()==null) {
			throw FeignException.errorStatus("Missing credentials. Expecting a bearer token in the consent data object.", Response.builder().status(401).build());
		}
	}

	private <T extends SCAResponseTO> void checkScaPresent(T sca) {
		if(sca==null) {
			throw FeignException.errorStatus("Missing consent data", Response.builder().status(401).build());
		}
	}

	
}
