package de.adorsys.ledgers.gatway;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.google.common.base.Predicates;

import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger.web.InMemorySwaggerResourcesProvider;
import springfox.documentation.swagger.web.SwaggerResource;
import springfox.documentation.swagger.web.SwaggerResourcesProvider;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@EnableSwagger2
public class SwaggerConfig {
    private static final String DEFAULT_PSD2_API_LOCATION = "/psd2-api-1.2-Update-2018-08-17.yaml";

    @Value("${xs2a.swagger.psd2.api.location:''}")
    private String customPsd2ApiLocation;

    @Bean(name = "api")
    public Docket apiDocklet() {
        return new Docket(DocumentationType.SWAGGER_2)
                   .apiInfo(new ApiInfoBuilder().build())
                   .groupName("XS2A")
                   .select()
                   .apis(RequestHandlerSelectors.basePackage("de.adorsys.psd2.xs2a.web"))
                   .paths(Predicates.not(PathSelectors.regex("/error.*?")))
                   .paths(Predicates.not(PathSelectors.regex("/connect.*")))
                   .paths(Predicates.not(PathSelectors.regex("/management.*")))
                   .build();
    }

    @Bean
    @Primary
    public SwaggerResourcesProvider swaggerResourcesProvider(InMemorySwaggerResourcesProvider defaultResourcesProvider) {
        return () -> {
            SwaggerResource swaggerResource = new SwaggerResource();
            swaggerResource.setLocation(resolveYamlLocation());
            List<SwaggerResource> resources = new ArrayList<>();
            resources.add(swaggerResource);
            resources.addAll(defaultResourcesProvider.get());
            return resources;            
        };
    }

    private String resolveYamlLocation() {
        if (StringUtils.isBlank(customPsd2ApiLocation)) {
            return DEFAULT_PSD2_API_LOCATION;
        }
        return customPsd2ApiLocation;
    }

}
