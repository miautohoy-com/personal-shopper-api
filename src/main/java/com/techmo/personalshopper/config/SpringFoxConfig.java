package com.techmo.personalshopper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

import java.util.Collections;

@Configuration
public class SpringFoxConfig {
    @Bean
    public Docket swaggerConfiguration() {
        return new Docket(DocumentationType.SWAGGER_2)
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.mah.personalshopper"))
                .build()
                .apiInfo(getApiDetails());
    }

    private ApiInfo getApiDetails() {
        return new ApiInfo(
                "Personal Shopper REST API",
                null,
                "1.1",
                "Free to use",
                new springfox.documentation.service.Contact(null, null, null),
                null,
                null,
                Collections.emptyList()
        );
    }
}