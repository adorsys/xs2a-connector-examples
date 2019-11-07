package de.adorsys.aspsp.xs2a.remote.connector.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class OauthConfig {
    private final TokenAuthenticationFilter tokenAuthenticationFilter;

    @Bean
    public FilterRegistrationBean tokenAuthenticationFilterRegistration() {
        FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean<>(tokenAuthenticationFilter);
        filterRegistrationBean.setOrder(0);
        return filterRegistrationBean;
    }
}
