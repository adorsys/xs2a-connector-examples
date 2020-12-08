/*
 * Copyright 2018-2020 adorsys GmbH & Co KG
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

package de.adorsys.ledgers.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spring.web.plugins.Docket;

import javax.annotation.PostConstruct;

@Component
@DependsOn("api")
@RequiredArgsConstructor
public class Xs2aSwaggerConfig {

    @Value("${xs2a.license.url:}")
    private String licenseUrl;

    private final BuildProperties buildProperties;
    private final ApplicationContext applicationContext;

    @PostConstruct
    public void init() {
        applicationContext.getBean("api", Docket.class)
                .apiInfo(apiInfo());
    }

    public ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                       .title("XS2A Core rest API")
                       .contact(new Contact("Adorsys GmbH", "http://www.adorsys.de", "fpo@adorsys.de"))
                       .version(buildProperties.getVersion() + " " + buildProperties.get("build.number"))
                       .license("Apache License 2.0")
                       .licenseUrl(licenseUrl)
                       .build();
    }
}
