package de.adorsys.ledgers.xs2a.test.ctk.pis;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import de.adorsys.ledgers.xs2a.api.client.ConsentApiClient;
import de.adorsys.ledgers.xs2a.test.ctk.StarterApplication;
import de.adorsys.psd2.model.ConsentStatus;
import de.adorsys.psd2.model.ConsentsResponse201;
import de.adorsys.psd2.model.UpdatePsuAuthenticationResponse;
import feign.FeignException;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = StarterApplication.class)
public class ConsentEmbeddedUnknownUser {
	private String PSU_ID = "user.unknown";

	@Autowired
	private ConsentApiClient consentApi;

	private ConsentHelper consentHelper;
	@Before
	public void beforeClass() {
		consentHelper = new ConsentHelper(consentApi, PSU_ID);
	}

	@Test
	public void test_create_payment() {
		
		ResponseEntity<ConsentsResponse201> createConsentResp = consentHelper.createConsent();

		ConsentsResponse201 consents = createConsentResp.getBody();
		// Login User
		Assert.assertNotNull(consents);
		ConsentStatus consentStatus = consents.getConsentStatus();
		Assert.assertNotNull(consentStatus);
		Assert.assertEquals(ConsentStatus.RECEIVED, consentStatus);
		
		try {
			ResponseEntity<UpdatePsuAuthenticationResponse> loginResponseWrapper = consentHelper.login(createConsentResp);
		} catch(FeignException f) {
			// TODO: create Ticket why bad request. Middleware return not found. SPI design does not allow
			// pass thru of code.
//			Assert.assertEquals(HttpStatus.NOT_FOUND.value(), f.status());		
			Assert.assertEquals(HttpStatus.BAD_REQUEST.value(), f.status());		
		}

	}
}
