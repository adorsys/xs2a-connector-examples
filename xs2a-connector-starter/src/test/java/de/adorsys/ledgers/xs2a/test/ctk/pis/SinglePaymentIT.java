package de.adorsys.ledgers.xs2a.test.ctk.pis;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import de.adorsys.ledgers.xs2a.ctk.profile.AspspProfileUpdateClient;
import de.adorsys.ledgers.xs2a.ctk.xs2a.PaymentApiClient;
import de.adorsys.ledgers.xs2a.test.ctk.StarterApplication;
import de.adorsys.psd2.model.PaymentInitationRequestResponse201;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes=StarterApplication.class)
public class SinglePaymentIT {
	ObjectMapper ymlMapper = new ObjectMapper(new YAMLFactory());

	@Autowired
	private AspspProfileUpdateClient profileClient;
	@Autowired
	private PaymentApiClient paymentApi;
	
	private static ObjectMapper objectMapper = new ObjectMapper();
	
	@BeforeClass
	public static void beforeClass() {
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	@Test
	public void test_create_payment() {
		
		// Approach
//		profileClient.updateScaApproach("REDIRECT");
		
		
		PaymentCase case1 = loadPayment("paymentCase-1.yml");
		String paymentService = "payments";
		String paymentProduct = "sepa-credit-transfers";
		Object payment = case1.getPayment();
		UUID xRequestID = UUID.randomUUID();
		String psUIPAddress = "127.0.0.1";
		String digest = null;
		String signature = null;
		byte[] tpPSignatureCertificate = null;
		String PSU_ID = case1.getPsuId();
		String psUIDType = null;
		String psUCorporateID = null;
		String psUCorporateIDType = null;
		String consentID = null;
		Boolean tpPRedirectPreferred = false;
		String tpPRedirectURI = null;
		String tpPNokRedirectURI = null;
		Boolean tpPExplicitAuthorisationPreferred = true;
		Object psUIPPort = null;
		String psUAccept = null;
		String psUAcceptCharset = null;
		String psUAcceptEncoding = null;
		String psUAcceptLanguage = null;
		String psUUserAgent = null;
		String psUHttpMethod = null;
		UUID psUDeviceID = UUID.randomUUID();
		String psUGeoLocation = null;
		ResponseEntity<Object> response = paymentApi._initiatePayment(payment, paymentService, paymentProduct, xRequestID, psUIPAddress, digest, signature,
				tpPSignatureCertificate, PSU_ID, psUIDType, psUCorporateID, psUCorporateIDType, consentID,
				tpPRedirectPreferred, tpPRedirectURI, tpPNokRedirectURI, tpPExplicitAuthorisationPreferred, psUIPPort,
				psUAccept, psUAcceptCharset, psUAcceptEncoding, psUAcceptLanguage, psUUserAgent, psUHttpMethod,
				psUDeviceID, psUGeoLocation);
				
		Assert.assertNotNull(response);
		Map<String, String> pmtResp = (Map<String, String>) response.getBody();		

		PaymentInitationRequestResponse201 resp = objectMapper.convertValue(
				pmtResp.get("PaymentInitationRequestResponse201"), PaymentInitationRequestResponse201.class);
		String startAuthorisationWithPsuAuthentication = (String) resp.getLinks().get("startAuthorisationWithPsuAuthentication");

		Assert.assertNotNull(resp.getPaymentId());
		Assert.assertNotNull(resp.getTransactionStatus());
		Assert.assertEquals("RCVD", resp.getTransactionStatus().name());
		Assert.assertNotNull(resp.getPaymentId());
		
		
//		String authorisationId = StringUtils.substringAfterLast(startAuthorisationWithPsuAuthentication, "/");
//		authorisationId = StringUtils.substringBefore(authorisationId, "?");
//		paymentApi._updatePaymentPsuData(paymentService, resp.getPaymentId(), authorisationId, xRequestID, body, digest, signature, tpPSignatureCertificate, PSU_ID, psUIDType, psUCorporateID, psUCorporateIDType, psUIPAddress, psUIPPort, psUAccept, psUAcceptCharset, psUAcceptEncoding, psUAcceptLanguage, psUUserAgent, psUHttpMethod, psUDeviceID, psUGeoLocation);
		
		
//		String redirectLink = (String) resp.getLinks().get("scaRedirect");
//		Assert.assertNotNull(redirectLink);
//		Assert.assertTrue(redirectLink.startsWith("http://localhost:8090/pis/auth?paymentId=" + resp.getPaymentId()));

	}

	public PaymentCase loadPayment(String file) {
		InputStream stream = SinglePaymentIT.class.getResourceAsStream(file);
		try {
			return ymlMapper.readValue(stream, PaymentCase.class);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

}
