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

package de.adorsys.aspsp.xs2a.remote.connector.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.test.context.ActiveProfiles;

import de.adorsys.aspsp.xs2a.remote.connector.EnableLedgersXS2AConnectorRemote;
import de.adorsys.ledgers.rest.client.PaymentRestClient;

@EnableFeignClients(basePackageClasses=PaymentRestClient.class)
@SpringBootApplication
//@EnableXs2aSwagger
@EnableLedgersXS2AConnectorRemote
@ActiveProfiles({"h2","mockspi"})
@EnableAutoConfiguration
public class LedgersXs2aGatewayApplication {
    public static void main(String[] args) {
    	System.setProperty("spring.main.allow-bean-definition-overriding", "true");
        SpringApplication.run(LedgersXs2aGatewayApplication.class, args);
    }
}
