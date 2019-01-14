package de.adorsys.ledgers.xs2a.test.ctk.pis;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import de.adorsys.ledgers.xs2a.api.client.PaymentApiClient;
import de.adorsys.ledgers.xs2a.test.ctk.StarterApplication;
import de.adorsys.psd2.model.PaymentInitationRequestResponse201;
import de.adorsys.psd2.model.PaymentInitiationStatusResponse200Json;
import de.adorsys.psd2.model.ScaStatus;
import de.adorsys.psd2.model.TransactionStatus;
import de.adorsys.psd2.model.UpdatePsuAuthenticationResponse;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = StarterApplication.class)
public class SinglePaymentEmbeddedOneScaMethodIT {
	private final YAMLMapper ymlMapper = new YAMLMapper();
	private final String paymentService = "payments";
	private final String paymentProduct = "sepa-credit-transfers";

	@Autowired
	private PaymentApiClient paymentApi;
	
	private PaymentExecutionHelper paymentInitService;
	
	@Before
	public void beforeClass() {
		PaymentCase paymentCase = LoadPayment.loadPayment(SinglePaymentEmbeddedOneScaMethodIT.class, "SinglePaymentEmbeddedOneScaMethodIT.yml", ymlMapper);
		paymentInitService = new PaymentExecutionHelper(paymentApi, paymentCase, paymentService, paymentProduct);
	}

	@Test
	public void test_create_payment() {
		// Initiate Payment
		PaymentInitationRequestResponse201 initiatedPayment = paymentInitService.initiatePayment();

		// Login User
		UpdatePsuAuthenticationResponse psuAuthenticationResponse = paymentInitService.login(initiatedPayment);
		checkScaStatus(ScaStatus.SCAMETHODSELECTED, psuAuthenticationResponse);
		checkTransactionStatusStatus(TransactionStatus.ACCP, psuAuthenticationResponse);
		
		psuAuthenticationResponse = paymentInitService.authCode(psuAuthenticationResponse);
		checkScaStatus(ScaStatus.FINALISED, psuAuthenticationResponse);
		checkTransactionStatusStatus(TransactionStatus.ACSP, psuAuthenticationResponse);
	}
	
	private void checkTransactionStatusStatus(TransactionStatus t, UpdatePsuAuthenticationResponse psuAuthenticationResponse) {
		PaymentInitiationStatusResponse200Json paymentStatus = paymentInitService.loadPaymentStatus(psuAuthenticationResponse);
		Assert.assertNotNull(paymentStatus);
		TransactionStatus transactionStatus = paymentStatus.getTransactionStatus();
		Assert.assertNotNull(transactionStatus);
		Assert.assertEquals(t, transactionStatus);
	}
	
	private void checkScaStatus(ScaStatus s, UpdatePsuAuthenticationResponse psuAuthenticationResponse) {
		Assert.assertNotNull(psuAuthenticationResponse);
		ScaStatus scaStatus = psuAuthenticationResponse.getScaStatus();
		Assert.assertNotNull(scaStatus);
		Assert.assertEquals(s, scaStatus);
	}
}
