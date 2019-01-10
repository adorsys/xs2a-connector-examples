package de.adorsys.ledgers.consent.standalone;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackageClasses= {ConsentMgntBasePackage.class})
public class ConsentMgntConfiguration {
}
