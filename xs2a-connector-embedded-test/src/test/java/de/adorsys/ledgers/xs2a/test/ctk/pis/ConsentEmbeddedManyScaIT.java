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
import de.adorsys.psd2.model.ConsentStatusResponse200;
import de.adorsys.psd2.model.ConsentsResponse201;
import de.adorsys.psd2.model.ScaStatus;
import de.adorsys.psd2.model.UpdatePsuAuthenticationResponse;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = StarterApplication.class)
public class ConsentEmbeddedManyScaIT {
	private String PSU_ID = "max.musterman";

	@Autowired
	private ConsentApiClient consentApi;

	private ConsentHelper consentHelper;
	@Before
	public void beforeClass() {
		consentHelper = new ConsentHelper(consentApi, PSU_ID);
	}

	@Test
	public void test_initiate_consent() {
		
		// ============= INITIATE CONSENT =======================//
		ResponseEntity<ConsentsResponse201> createConsentResp = consentHelper.createConsent();
		ConsentsResponse201 consents = createConsentResp.getBody();
		Assert.assertNotNull(consents);
		Assert.assertEquals(ConsentStatus.RECEIVED, consents.getConsentStatus());
		
		// ============= IDENTIFY PSU =======================//
		ResponseEntity<UpdatePsuAuthenticationResponse> loginResponseWrapper = consentHelper.login(createConsentResp);
		Assert.assertNotNull(loginResponseWrapper);
		Assert.assertEquals(HttpStatus.OK, loginResponseWrapper.getStatusCode());
		UpdatePsuAuthenticationResponse loginResponse = loginResponseWrapper.getBody();
		Assert.assertEquals(ScaStatus.PSUAUTHENTICATED, loginResponse.getScaStatus());
		
		String selectAuthenticationMethodUrl = (String) loginResponse.getLinks().get("selectAuthenticationMethod");
		ResponseEntity<ConsentStatusResponse200> loadConsentStatusResponse = consentHelper.loadConsentStatus(selectAuthenticationMethodUrl);
		ConsentStatusResponse200 consentStatusResponse200 = loadConsentStatusResponse.getBody();
		Assert.assertEquals(ConsentStatus.RECEIVED, consentStatusResponse200.getConsentStatus());
		
		// ============= SELECT SCA =======================//
		ResponseEntity<UpdatePsuAuthenticationResponse> choseScaMethodResponseWrapper = consentHelper.choseScaMethod(loginResponse);
		Assert.assertNotNull(choseScaMethodResponseWrapper);
		Assert.assertEquals(HttpStatus.OK, choseScaMethodResponseWrapper.getStatusCode());
		UpdatePsuAuthenticationResponse choseScaMethodResponse = choseScaMethodResponseWrapper.getBody();
		Assert.assertEquals(ScaStatus.SCAMETHODSELECTED, choseScaMethodResponse.getScaStatus());
		
		String authoriseTransactionUrl = (String) choseScaMethodResponse.getLinks().get("authoriseTransaction");
		loadConsentStatusResponse = consentHelper.loadConsentStatus(authoriseTransactionUrl);
		consentStatusResponse200 = loadConsentStatusResponse.getBody();
		Assert.assertEquals(ConsentStatus.RECEIVED, consentStatusResponse200.getConsentStatus());
		
		// ============= AUTHORIZE CONSENT =======================//
		ResponseEntity<UpdatePsuAuthenticationResponse> authCodeResponseWrapper = consentHelper.authCode(choseScaMethodResponse);
		Assert.assertNotNull(authCodeResponseWrapper);
		Assert.assertEquals(HttpStatus.OK, authCodeResponseWrapper.getStatusCode());
		UpdatePsuAuthenticationResponse authCodeResponse = authCodeResponseWrapper.getBody();
		Assert.assertEquals(ScaStatus.FINALISED, authCodeResponse.getScaStatus());
		
		String scaStatusUrl = (String) authCodeResponse.getLinks().get("scaStatus");
		loadConsentStatusResponse = consentHelper.loadConsentStatus(scaStatusUrl);
		Assert.assertEquals(HttpStatus.OK, loadConsentStatusResponse.getStatusCode());
		consentStatusResponse200 = loadConsentStatusResponse.getBody();
		Assert.assertEquals(ConsentStatus.VALID, consentStatusResponse200.getConsentStatus());
		
	}
}
