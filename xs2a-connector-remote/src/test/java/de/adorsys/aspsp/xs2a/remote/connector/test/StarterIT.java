package de.adorsys.aspsp.xs2a.remote.connector.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = LedgersXs2aGatewayApplication.class)
class StarterIT {

	// Suppress "Tests should include assertions" Sonar rule as this test is not supposed to assert anything
	@SuppressWarnings("squid:S2699")
	@Test
	void test() {
		// Test whether application starts up
	}
}
