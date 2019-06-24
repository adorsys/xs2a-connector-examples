/*
 * Copyright 2018-2019 adorsys GmbH & Co KG
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

import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import de.adorsys.ledgers.middleware.api.service.TokenStorageService;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;

@Service
public class AspspConsentDataService {
	
	@Autowired
	private TokenStorageService tokenStorageService;

	/**
	 * Default storage, makes sure there is a bearer token in the response object.
	 */
	public byte[] store(SCAResponseTO response){
		return store(response, true);
	}

	public byte[] store(SCAResponseTO response, boolean checkCredentials){
		if(checkCredentials && response.getBearerToken()==null) {
			throw new IllegalStateException("Missing credentials. response must contain a bearer token by default.");
		}
		try {
			return tokenStorageService.toBytes(response);
		} catch (IOException e) {
			throw FeignException.errorStatus(e.getMessage(), error(500));
		}
	}
	
	public <T extends SCAResponseTO> T response(byte[] aspspConsentData, Class<T> klass) {
		return response(aspspConsentData, klass, true);
	}

	public SCAResponseTO response(byte[] aspspConsentData) {
		return response(aspspConsentData, true);
	}

	public SCAResponseTO response(byte[] aspspConsentData, boolean checkCredentials) {
		try {
			SCAResponseTO sca = tokenStorageService.fromBytes(aspspConsentData);
			checkScaPresent(sca);
			checkBearerTokenPresent(checkCredentials, sca);
			return sca;
		} catch (IOException e) {
			throw FeignException.errorStatus(e.getMessage(), error(500));
		}
	}
	
	public <T extends SCAResponseTO> T response(byte[] aspspConsentData, Class<T> klass, boolean checkCredentials) {
		try {
			T sca = tokenStorageService.fromBytes(aspspConsentData, klass);
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
		return Response.builder()
				       .status(code)
				       .request(Request.create(Request.HttpMethod.GET, "", Collections.emptyMap(), null))
				       .headers(Collections.emptyMap())
				       .build();
	}
}
