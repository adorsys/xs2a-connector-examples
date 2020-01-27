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

package de.adorsys.aspsp.xs2a.remote.connector.test;

import de.adorsys.aspsp.xs2a.connector.oauth.TokenAuthenticationFilter;
import de.adorsys.psd2.aspsp.profile.service.AspspProfileService;
import de.adorsys.psd2.xs2a.core.profile.ScaApproach;
import de.adorsys.psd2.xs2a.core.tpp.TppInfo;
import de.adorsys.psd2.xs2a.service.TppService;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.Filter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Supplier;

import static org.apache.commons.io.IOUtils.resourceToString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = LedgersXs2aGatewayApplication.class)
class ServiceUnavailableIT {
    private static final Charset UTF_8 = StandardCharsets.UTF_8;
    private static final String DEDICATED_CONSENT_REQUEST_JSON_PATH = "/json/account/req/DedicatedConsent.json";
    private static final String SERVICE_UNAVAILABLE_ERROR_MESSAGE_JSON_PATH = "/json/account/res/ServiceUnavailableErrorMessage.json";
    private static final TppInfo TPP_INFO = buildTppInfo();

    @RegisterExtension
    final BeforeEachCallback resourceAvailableCallback = this::onStartingTest;

    private HttpHeaders httpHeadersImplicit = new HttpHeaders();

    @Autowired
    private WebApplicationContext webApplicationContext;
    @MockBean
    private AspspProfileService aspspProfileService;
    @MockBean
    private TppService tppService;
    @Autowired
    private TokenAuthenticationFilter tokenAuthenticationFilter;
    private Supplier<ResourceAccessException> resourceAccessExceptionSupplier = () -> new ResourceAccessException("");

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD})
    public @interface ResourceAvailable {
        boolean profile() default true;
    }

    @BeforeEach
    void init() {
        HashMap<String, String> headerMap = new HashMap<>();
        headerMap.put("Content-Type", "application/json");
        headerMap.put("Accept", "application/json");
        headerMap.put("x-request-id", "2f77a125-aa7a-45c0-b414-cea25a116035");
        headerMap.put("PSU-ID", "PSU-123");
        headerMap.put("PSU-ID-Type", "Some type");
        headerMap.put("PSU-Corporate-ID", "Some corporate id");
        headerMap.put("PSU-Corporate-ID-Type", "Some corporate id type");
        headerMap.put("PSU-IP-Address", "1.1.1.1");
        headerMap.put("TPP-Redirect-URI", "ok.uri");
        headerMap.put("TPP-Implicit-Authorisation-Preferred", "false");
        headerMap.put("X-OAUTH-PREFERRED", "integrated");

        httpHeadersImplicit.setAll(headerMap);
    }

    @Test
    @ResourceAvailable(profile = false)
    void aspsp_profile_not_accessible_in_token_authentification_filter() throws Exception {
        MockMvc mockMvc = buildMockMvcWithFilters(tokenAuthenticationFilter);
        create_consent_service_unavailable_test(mockMvc);
    }

    private static TppInfo buildTppInfo() {
        TppInfo tppInfo = new TppInfo();
        tppInfo.setAuthorityId("authorisation_id");
        return tppInfo;
    }

    private MockMvc buildMockMvcWithFilters(Filter... filters) {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext)
                       .addFilters(filters)
                       .build();
    }

    private void create_consent_service_unavailable_test(MockMvc mockMvc) throws Exception {
        service_unavailable_test(mockMvc, DEDICATED_CONSENT_REQUEST_JSON_PATH, post("/v1/consents/"));
    }

    private void service_unavailable_test(MockMvc mockMvc, String contentFilePath, MockHttpServletRequestBuilder requestBuilder) throws Exception {
        //Given
        requestBuilder.headers(httpHeadersImplicit);
        requestBuilder.content(resourceToString(contentFilePath, UTF_8));
        //When
        ResultActions resultActions = mockMvc.perform(requestBuilder);
        //Then
        resultActions.andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(content().json(IOUtils.resourceToString(SERVICE_UNAVAILABLE_ERROR_MESSAGE_JSON_PATH, UTF_8)));
    }

    private void onStartingTest(ExtensionContext extensionContext) {
        Optional<ResourceAvailable> resourceAvailableOptional = extensionContext.getElement()
                                                                        .map(e -> e.getAnnotation(ResourceAvailable.class));
        makePreparationsCommon();

        if (resourceAvailableOptional.isPresent()) {
            ResourceAvailable resourceAvailable = resourceAvailableOptional.get();
            makePreparationsProfile(!resourceAvailable.profile());
            return;
        }

        makePreparationsProfile(false);
    }

    //Preparations
    private void makePreparationsProfile(boolean throwException) {
        givenReturnOrThrowException(aspspProfileService.getScaApproaches(), Collections.singletonList(ScaApproach.EMBEDDED), throwException);
    }

    private void makePreparationsCommon() {
        given(tppService.getTppInfo()).willReturn(TPP_INFO);
    }

    private <T> void givenReturnOrThrowException(T methodCall, T returnValue, boolean throwException) {
        if (throwException) {
            given(methodCall).willThrow(resourceAccessExceptionSupplier.get());
        } else {
            given(methodCall).willReturn(returnValue);
        }
    }
}
