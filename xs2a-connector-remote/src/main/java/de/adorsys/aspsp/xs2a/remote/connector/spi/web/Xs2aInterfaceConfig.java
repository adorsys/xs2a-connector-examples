package de.adorsys.aspsp.xs2a.remote.connector.spi.web;

import de.adorsys.psd2.xs2a.config.WebConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ComponentScan(basePackages = {"de.adorsys.psd2.xs2a.component",
        "de.adorsys.aspsp.xs2a.remote.connector.config.validation",
        "de.adorsys.psd2.xs2a.config.factory",
        "de.adorsys.psd2.xs2a.domain",
        "de.adorsys.psd2.xs2a.service",
        "de.adorsys.psd2.xs2a.web",
        "de.adorsys.psd2.xs2a.exception",
        "de.adorsys.psd2.aspsp.profile",
        "de.adorsys.psd2.consent",
        "de.adorsys.ledgers"})
@Import(WebConfig.class)
public class Xs2aInterfaceConfig {
}
