/*
 * Copyright 2018-2023 adorsys GmbH & Co KG
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
package de.adorsys.aspsp.xs2a.connector.config.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.annotation.RequestScope;

@Configuration
public class AspspRestConfig {
    @Value("${http-client.read-timeout.ms:10000}")
    private int readTimeout;
    @Value("${http-client.connection-timeout.ms:10000}")
    private int connectionTimeout;

    public AspspRestConfig() {
	}

	public AspspRestConfig(int readTimeout, int connectionTimeout) {
		this.readTimeout = readTimeout;
		this.connectionTimeout = connectionTimeout;
	}

	@RequestScope
    @Bean(name = "aspspRestTemplate")
    public RestTemplate restTemplate(){
        RestTemplate rest = new RestTemplate(clientHttpRequestFactory());
        rest.getMessageConverters().removeIf(m -> m.getClass().isAssignableFrom(MappingJackson2XmlHttpMessageConverter.class));
        rest.setErrorHandler(new AspspRestErrorHandler());
        return rest;
    }

    private ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(readTimeout);
        factory.setConnectTimeout(connectionTimeout);
        return factory;
    }
}
