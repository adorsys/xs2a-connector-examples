package de.adorsys.ledgers.xs2a.test.ctk.pis;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import de.adorsys.ledgers.xs2a.ctk.xs2a.PaymentApiClient;
import de.adorsys.ledgers.xs2a.test.ctk.StarterApplication;
import de.adorsys.psd2.model.PaymentInitationRequestResponse201;
import de.adorsys.psd2.model.ScaStatus;
import de.adorsys.psd2.model.UpdatePsuAuthenticationResponse;

//@Ignore
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = StarterApplication.class)
public class SinglePaymentEmbeddedOneScaMethodIT {
	private final YAMLMapper ymlMapper = new YAMLMapper();
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final String paymentService = "payments";
	private final String paymentProduct = "sepa-credit-transfers";

	@Autowired
	private PaymentApiClient paymentApi;
	
	private PaymentExecutionHelper paymentInitService;
	
	@Before
	public void beforeClass() {
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		PaymentCase paymentCase = LoadPayment.loadPayment(SinglePaymentEmbeddedOneScaMethodIT.class, "SinglePaymentEmbeddedOneScaMethodIT.yml", ymlMapper);
		paymentInitService = new PaymentExecutionHelper(paymentApi, paymentCase, ymlMapper, objectMapper, paymentService, paymentProduct);
	}

	@Test
	public void test_create_payment() {
		PaymentInitationRequestResponse201 initiatedPayment = paymentInitService.initiatePayment();
		UpdatePsuAuthenticationResponse psuAuthenticationResponse = paymentInitService.login(initiatedPayment);
		ScaStatus scaStatus = psuAuthenticationResponse.getScaStatus();
		Assert.assertNotNull(scaStatus);
		Assert.assertEquals(ScaStatus.SCAMETHODSELECTED, scaStatus);
	}
}
