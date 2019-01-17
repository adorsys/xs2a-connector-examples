package de.adorsys.aspsp.xs2a.connector;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackageClasses= {LedgersXS2AConnectorBasePackage.class})
public class LedgersXS2AConnectorConfiguration {
}
