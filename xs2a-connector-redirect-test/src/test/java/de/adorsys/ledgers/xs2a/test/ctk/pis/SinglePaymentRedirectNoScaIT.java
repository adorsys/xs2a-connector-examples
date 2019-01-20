package de.adorsys.ledgers.xs2a.test.ctk.pis;

import java.net.MalformedURLException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.oba.rest.api.domain.PaymentAuthorizeResponse;
import de.adorsys.ledgers.oba.rest.client.ObaPisApiClient;
import de.adorsys.ledgers.xs2a.api.client.PaymentApiClient;
import de.adorsys.ledgers.xs2a.test.ctk.StarterApplication;
import de.adorsys.psd2.model.PaymentInitationRequestResponse201;
import de.adorsys.psd2.model.PaymentInitiationStatusResponse200Json;
import de.adorsys.psd2.model.TransactionStatus;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = StarterApplication.class)
public class SinglePaymentRedirectNoScaIT {
	private final YAMLMapper ymlMapper = new YAMLMapper();
	private final String paymentService = "payments";
	private final String paymentProduct = "sepa-credit-transfers";

	@Autowired
	private PaymentApiClient paymentApi;
	@Autowired
	private ObaPisApiClient obaPisApiClient;

	private PaymentExecutionHelper paymentInitService;

	@Before
	public void beforeClass() {
		PaymentCase paymentCase = LoadPayment.loadPayment(SinglePaymentRedirectNoScaIT.class,
				SinglePaymentRedirectNoScaIT.class.getSimpleName() + ".yml", ymlMapper);
		paymentInitService = new PaymentExecutionHelper(paymentApi, obaPisApiClient, paymentCase, paymentService, paymentProduct);
	}

	@Test
	public void test_create_payment() throws MalformedURLException {
		// Initiate Payment
		PaymentInitationRequestResponse201 initiatedPayment = paymentInitService.initiatePayment();

		// Login User
		ResponseEntity<PaymentAuthorizeResponse> login = paymentInitService.login(initiatedPayment);
		PaymentAuthorizeResponse loginResponse = login.getBody();
		Assert.assertNotNull(loginResponse);
		ScaStatusTO scaStatus = loginResponse.getScaStatus();;
		Assert.assertNotNull(scaStatus);
		Assert.assertEquals(ScaStatusTO.EXEMPTED, scaStatus);

		PaymentInitiationStatusResponse200Json paymentStatus = paymentInitService
				.loadPaymentStatus(initiatedPayment.getPaymentId());
		Assert.assertNotNull(paymentStatus);
		TransactionStatus transactionStatus = paymentStatus.getTransactionStatus();
		Assert.assertNotNull(transactionStatus);
		Assert.assertEquals(TransactionStatus.ACSP, transactionStatus);
	}
}
