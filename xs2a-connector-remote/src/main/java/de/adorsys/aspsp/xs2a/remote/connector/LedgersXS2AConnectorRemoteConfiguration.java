package de.adorsys.aspsp.xs2a.remote.connector;

import de.adorsys.aspsp.xs2a.connector.EnableLedgersXS2AConnector;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackageClasses = {LedgersXS2AConnectorRemoteBasePackage.class})
@EnableLedgersXS2AConnector
public class LedgersXS2AConnectorRemoteConfiguration {
}
