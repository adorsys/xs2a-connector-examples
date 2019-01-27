package de.adorsys.ledgers.xs2a.test.ctk.pis;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import de.adorsys.psd2.model.ConsentStatus;
import de.adorsys.psd2.model.ConsentStatusResponse200;
import de.adorsys.psd2.model.ConsentsResponse201;
import de.adorsys.psd2.model.ScaStatus;
import de.adorsys.psd2.model.UpdatePsuAuthenticationResponse;

public class ConsentEmbeddedOneScaIT extends AbstractConsentEmbedded {
	@Override
	protected String getPsuId() {
		return "anton.brueckner";
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
		Assert.assertEquals(ScaStatus.SCAMETHODSELECTED, loginResponse.getScaStatus());
		
		String authoriseTransaction = (String) loginResponse.getLinks().get("authoriseTransaction");
		ResponseEntity<ConsentStatusResponse200> loadonsentStatusResponse = consentHelper.loadConsentStatus(authoriseTransaction);
		ConsentStatusResponse200 consentStatusResponse200 = loadonsentStatusResponse.getBody();
		Assert.assertEquals(ConsentStatus.RECEIVED, consentStatusResponse200.getConsentStatus());
		
		// ============= AUTHORIZE CONSENT =======================//
		ResponseEntity<UpdatePsuAuthenticationResponse> authCodeResponseWrapper = consentHelper.authCode(loginResponse);
		Assert.assertNotNull(authCodeResponseWrapper);
		Assert.assertEquals(HttpStatus.OK, authCodeResponseWrapper.getStatusCode());
		UpdatePsuAuthenticationResponse authCodeResponse = authCodeResponseWrapper.getBody();
		// TODO: Missing ScaStatus from response
		Assert.assertEquals(ScaStatus.FINALISED, authCodeResponse.getScaStatus());
		
		String scaStatusUrl = (String) authCodeResponse.getLinks().get("scaStatus");
		loadonsentStatusResponse = consentHelper.loadConsentStatus(scaStatusUrl);
		Assert.assertEquals(HttpStatus.OK, loadonsentStatusResponse.getStatusCode());
		consentStatusResponse200 = loadonsentStatusResponse.getBody();
		Assert.assertEquals(ConsentStatus.VALID, consentStatusResponse200.getConsentStatus());
		
	}
}
