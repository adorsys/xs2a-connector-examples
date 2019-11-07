package de.adorsys.aspsp.xs2a.remote.connector.oauth;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class OauthConfigTest {
    @Mock
    private TokenAuthenticationFilter tokenAuthenticationFilter;

    @InjectMocks
    private OauthConfig oauthConfig;

    @Test
    public void tokenAuthenticationFilterRegistration() {
        FilterRegistrationBean filterRegistrationBean = oauthConfig.tokenAuthenticationFilterRegistration();

        assertEquals(0, filterRegistrationBean.getOrder());
        assertEquals(tokenAuthenticationFilter, filterRegistrationBean.getFilter());
    }
}