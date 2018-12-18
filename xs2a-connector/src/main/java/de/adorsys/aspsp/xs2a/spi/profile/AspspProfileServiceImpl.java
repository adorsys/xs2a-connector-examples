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

package de.adorsys.aspsp.xs2a.spi.profile;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import de.adorsys.psd2.aspsp.profile.domain.AspspSettings;
import de.adorsys.psd2.aspsp.profile.service.AspspProfileService;
import de.adorsys.psd2.xs2a.core.profile.ScaApproach;

@Service
public class AspspProfileServiceImpl implements AspspProfileService {
    private ProfileConfiguration profileConfiguration;

    public AspspProfileServiceImpl() {
	}
    
    @PostConstruct
    public void postConstruct() {
		ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
		InputStream resourceAsStream = AspspProfileServiceImpl.class.getResourceAsStream("/bank_profile_ledgers.yml");
		AspspSettingsHolder settings;
		try {
			settings = objectMapper.readValue(resourceAsStream, AspspSettingsHolder.class);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		this.profileConfiguration = settings.getSetting();
		this.profileConfiguration.afterPropertiesSet();
    }

	@Override
    public AspspSettings getAspspSettings() {
        return new AspspSettings(
            profileConfiguration.getFrequencyPerDay(),
            profileConfiguration.isCombinedServiceIndicator(),
            profileConfiguration.getAvailablePaymentProducts(),
            profileConfiguration.getAvailablePaymentTypes(),
            profileConfiguration.isTppSignatureRequired(),
            profileConfiguration.getPisRedirectUrlToAspsp(),
            profileConfiguration.getAisRedirectUrlToAspsp(),
            profileConfiguration.getMulticurrencyAccountLevel(),
            profileConfiguration.isBankOfferedConsentSupport(),
            profileConfiguration.getAvailableBookingStatuses(),
            profileConfiguration.getSupportedAccountReferenceFields(),
            profileConfiguration.getConsentLifetime(),
            profileConfiguration.getTransactionLifetime(),
            profileConfiguration.isAllPsd2Support(),
            profileConfiguration.isTransactionsWithoutBalancesSupported(),
            profileConfiguration.isSigningBasketSupported(),
            profileConfiguration.isPaymentCancellationAuthorizationMandated(),
            profileConfiguration.isPiisConsentSupported(),
            profileConfiguration.isDeltaReportSupported(),
            profileConfiguration.getRedirectUrlExpirationTimeMs());
    }

    @Override
    public ScaApproach getScaApproach() {
        return profileConfiguration.getScaApproach();
    }
}
