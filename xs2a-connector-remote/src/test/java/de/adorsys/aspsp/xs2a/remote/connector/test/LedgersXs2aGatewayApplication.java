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

import de.adorsys.aspsp.xs2a.remote.connector.EnableLedgersXS2AConnectorRemote;
import de.adorsys.ledgers.keycloak.client.impl.KeycloakTokenServiceImpl;
import de.adorsys.ledgers.keycloak.client.mapper.KeycloakAuthMapperImpl;
import de.adorsys.ledgers.keycloak.client.rest.KeycloakTokenRestClient;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.psd2.xs2a.config.EnableXs2aInterface;
import de.adorsys.psd2.xs2a.web.config.EnableXs2aSwagger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.hateoas.HypermediaAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;

@EnableFeignClients(basePackageClasses = {PaymentRestClient.class})
@SpringBootApplication(exclude = {HypermediaAutoConfiguration.class, DataSourceAutoConfiguration.class})
@EnableXs2aSwagger
@EnableXs2aInterface
@EnableLedgersXS2AConnectorRemote
@ActiveProfiles({"mock-qwac"})
public class LedgersXs2aGatewayApplication {
    public static void main(String[] args) {
        System.setProperty("spring.main.allow-bean-definition-overriding", "true");
        SpringApplication.run(LedgersXs2aGatewayApplication.class, "--spring.profiles.active=h2,mock-qwac", "--security.basic.enabled=false");
    }
}
