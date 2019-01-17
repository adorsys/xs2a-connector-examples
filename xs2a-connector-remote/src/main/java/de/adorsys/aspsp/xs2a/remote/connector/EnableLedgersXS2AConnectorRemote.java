package de.adorsys.aspsp.xs2a.remote.connector;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

@Retention(value = java.lang.annotation.RetentionPolicy.RUNTIME)
@Target(value = {java.lang.annotation.ElementType.TYPE})
@Documented
@Import({
	LedgersXS2AConnectorRemoteConfiguration.class
})
public @interface EnableLedgersXS2AConnectorRemote {
}
