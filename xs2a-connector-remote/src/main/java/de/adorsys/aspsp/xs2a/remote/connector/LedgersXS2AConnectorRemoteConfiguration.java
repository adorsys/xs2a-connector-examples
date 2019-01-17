package de.adorsys.aspsp.xs2a.remote.connector;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import de.adorsys.aspsp.xs2a.connector.EnableLedgersXS2AConnector;
import de.adorsys.aspsp.xs2a.remote.connector.spi.web.Xs2aInterfaceConfig;

@Configuration
@ComponentScan(basePackageClasses= {LedgersXS2AConnectorRemoteBasePackage.class})
@EnableLedgersXS2AConnector
@Import(Xs2aInterfaceConfig.class)
public class LedgersXS2AConnectorRemoteConfiguration {
}
