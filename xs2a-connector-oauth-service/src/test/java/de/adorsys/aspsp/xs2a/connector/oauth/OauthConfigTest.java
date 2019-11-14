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

package de.adorsys.aspsp.xs2a.connector.oauth;

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