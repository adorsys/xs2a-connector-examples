package de.adorsys.aspsp.xs2a.connector.config.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;

@Configuration
public class AuthConfig {
	@Bean
	public AuthRequestInterceptor getClientAuth() {
		return new AuthRequestInterceptor();
	}
}
