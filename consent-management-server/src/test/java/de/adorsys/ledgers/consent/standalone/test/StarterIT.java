package de.adorsys.ledgers.consent.standalone.test;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = ConsentManagementApplication.class)
@ActiveProfiles("h2")
public class StarterIT {

	@Test
	public void test() {
		// DO nothing.
	}
}
