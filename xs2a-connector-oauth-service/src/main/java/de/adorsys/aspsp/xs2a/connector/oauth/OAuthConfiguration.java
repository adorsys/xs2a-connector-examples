/*
 * Copyright 2018-2020 adorsys GmbH & Co KG
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

package de.adorsys.aspsp.xs2a.connector.oauth;

import de.adorsys.psd2.xs2a.core.mapper.ServiceType;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class OAuthConfiguration {
    @Value("${oauth.integrated.ais.suffix:?consentId={encrypted-consent-id}&redirectId={redirect-id}}")
    private String aisIntegratedOauthSuffix;
    @Value("${oauth.integrated.pis.suffix:?paymentId={encrypted-payment-id}&redirectId={redirect-id}}")
    private String pisIntegratedOauthSuffix;
    @Value("${oauth.integrated.pis.suffix:?piisConsentId={encrypted-consent-id}&redirectId={redirect-id}}")
    private String piisIntegratedOauthSuffix;
    @Value("${oauth.pre-step.ais.suffix:&token=}")
    private String aisPreStepOauthSuffix;
    @Value("${oauth.pre-step.pis.suffix:&token=}")
    private String pisPreStepOauthSuffix;

    public String getIntegratedOauthSuffix(ServiceType serviceType) {
        if (serviceType == ServiceType.AIS) {
            return aisIntegratedOauthSuffix;
        } else if (serviceType == ServiceType.PIS) {
            return pisIntegratedOauthSuffix;
        } else if (serviceType == ServiceType.PIIS) {
            return piisIntegratedOauthSuffix;
        }
        return "";
    }
}
