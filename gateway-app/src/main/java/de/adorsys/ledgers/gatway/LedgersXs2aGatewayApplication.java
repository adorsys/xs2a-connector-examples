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

package de.adorsys.ledgers.gatway;

import de.adorsys.aspsp.xs2a.remote.connector.EnableLedgersXS2AConnectorRemote;
import de.adorsys.ledgers.rest.client.CmsPsuPisClient;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.psd2.xs2a.config.EnableXs2aInterface;
import de.adorsys.psd2.xs2a.web.config.EnableXs2aSwagger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.hateoas.HypermediaAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(basePackageClasses = {PaymentRestClient.class, CmsPsuPisClient.class})
@SpringBootApplication(exclude = {HypermediaAutoConfiguration.class})
@EnableLedgersXS2AConnectorRemote
@EnableXs2aInterface
@EnableXs2aSwagger
public class LedgersXs2aGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgersXs2aGatewayApplication.class, args);
    }
}
