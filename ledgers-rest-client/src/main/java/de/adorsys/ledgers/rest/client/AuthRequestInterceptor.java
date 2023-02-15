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

package de.adorsys.ledgers.rest.client;

import de.adorsys.ledgers.middleware.rest.utils.Constants;
import feign.RequestInterceptor;
import feign.RequestTemplate;

public class AuthRequestInterceptor implements RequestInterceptor {

    private static final String BEARER_CONSTANT = "Bearer ";

    private ThreadLocal<String> accessToken = new ThreadLocal<>();

    @Override
    public void apply(RequestTemplate template) {
        if (accessToken.get() != null) {
            template.header(Constants.AUTH_HEADER_NAME, BEARER_CONSTANT + accessToken.get());
        }
    }

    public void setAccessToken(String accessToken) {
        this.accessToken.set(accessToken);
    }
}
