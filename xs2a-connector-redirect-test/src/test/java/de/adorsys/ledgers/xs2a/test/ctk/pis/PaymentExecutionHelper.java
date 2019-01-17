package de.adorsys.ledgers.xs2a.test.ctk.pis;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;

import de.adorsys.ledgers.xs2a.api.client.PaymentApiClient;
import de.adorsys.psd2.model.PaymentInitationRequestResponse201;
import de.adorsys.psd2.model.PaymentInitiationStatusResponse200Json;
import de.adorsys.psd2.model.PsuData;
import de.adorsys.psd2.model.SelectPsuAuthenticationMethod;
import de.adorsys.psd2.model.UpdatePsuAuthentication;
import de.adorsys.psd2.model.UpdatePsuAuthenticationResponse;

public class PaymentExecutionHelper {

	private final String digest = null;
	private final String signature = null;
	private final byte[] tpPSignatureCertificate = null;
	private final String psUIDType = null;
	private final String psUCorporateID = null;
	private final String psUCorporateIDType = null;
	private final String psUIPAddress = "127.0.0.1";
	private final String psUIPPort = null;
	private final String psUAccept = null;
	private final String psUAcceptCharset = null;
	private final String psUAcceptEncoding = null;
	private final String psUAcceptLanguage = null;
	private final String psUUserAgent = null;
	private final String psUHttpMethod = null;
	private final UUID psUDeviceID = UUID.randomUUID();
	private final String psUGeoLocation = null;

	private final PaymentApiClient paymentApi;
	private final PaymentCase paymentCase;
	private final String paymentService;
	private final String paymentProduct;

	private String paymentId;
	private String authorisationId;

	public PaymentExecutionHelper(PaymentApiClient paymentApi, PaymentCase paymentCase, String paymentService,
			String paymentProduct) {
		super();
		this.paymentApi = paymentApi;
		this.paymentCase = paymentCase;
		this.paymentService = paymentService;
		this.paymentProduct = paymentProduct;
	}

	public PaymentInitationRequestResponse201 initiatePayment() {
		Object payment = paymentCase.getPayment();
		UUID xRequestID = UUID.randomUUID();
		String PSU_ID = paymentCase.getPsuId();
		String consentID = null;
		String tpPRedirectPreferred = "false";
		String tpPRedirectURI = null;
		String tpPNokRedirectURI = null;
		Boolean tpPExplicitAuthorisationPreferred = true;
		PaymentInitationRequestResponse201 resp = paymentApi._initiatePayment(payment, xRequestID, psUIPAddress,
				paymentService, paymentProduct, digest, signature, tpPSignatureCertificate, PSU_ID, psUIDType,
				psUCorporateID, psUCorporateIDType, consentID, tpPRedirectPreferred, tpPRedirectURI, tpPNokRedirectURI,
				tpPExplicitAuthorisationPreferred, psUIPPort, psUAccept, psUAcceptCharset, psUAcceptEncoding,
				psUAcceptLanguage, psUUserAgent, psUHttpMethod, psUDeviceID, psUGeoLocation).getBody();

		Assert.assertNotNull(resp);
		Assert.assertNotNull(getStartAuthorisationWithPsuAuthentication(resp));

		Assert.assertNotNull(resp.getPaymentId());
		Assert.assertNotNull(resp.getTransactionStatus());
		Assert.assertEquals("RCVD", resp.getTransactionStatus().name());
		Assert.assertNotNull(resp.getPaymentId());

		return resp;
	}

	public UpdatePsuAuthenticationResponse login(PaymentInitationRequestResponse201 initiatedPayment) {
		String startAuthorisationWithPsuAuthentication = getStartAuthorisationWithPsuAuthentication(initiatedPayment);
		paymentId = initiatedPayment.getPaymentId();
		authorisationId = StringUtils
				.substringBefore(StringUtils.substringAfterLast(startAuthorisationWithPsuAuthentication, "/"), "?");
		UpdatePsuAuthentication updatePsuAuthentication = new UpdatePsuAuthentication();
		updatePsuAuthentication.setPsuData(new PsuData().password("12345"));
		UUID xRequestID = UUID.randomUUID();
		String PSU_ID = paymentCase.getPsuId();

		// For explicite
//		paymentApi._startPaymentAuthorisation(paymentService, paymentId, xRequestID, PSU_ID, psUIDType, psUCorporateID, psUCorporateIDType, digest, signature, tpPSignatureCertificate, psUIPAddress, psUIPPort, psUAccept, psUAcceptCharset, psUAcceptEncoding, psUAcceptLanguage, psUUserAgent, psUHttpMethod, psUDeviceID, psUGeoLocation);

		UpdatePsuAuthenticationResponse updatePaymentPsuData = paymentApi
				._updatePaymentPsuData(xRequestID, paymentService, paymentProduct, paymentId, authorisationId,
						updatePsuAuthentication, digest, signature, tpPSignatureCertificate, PSU_ID, psUIDType,
						psUCorporateID, psUCorporateIDType, psUIPAddress, psUIPPort, psUAccept, psUAcceptCharset,
						psUAcceptEncoding, psUAcceptLanguage, psUUserAgent, psUHttpMethod, psUDeviceID, psUGeoLocation)
				.getBody();

		Assert.assertNotNull(updatePaymentPsuData);

		return updatePaymentPsuData;
	}

	public PaymentInitiationStatusResponse200Json loadPaymentStatus(UpdatePsuAuthenticationResponse authResponse) {
		String self = (String) authResponse.getLinks().get("self");
		String paymentId = StringUtils.substringAfterLast(self, "/");
		UUID xRequestID = UUID.randomUUID();
		PaymentInitiationStatusResponse200Json paymentInitiationStatus = paymentApi
				._getPaymentInitiationStatus(paymentService, paymentProduct, paymentId, xRequestID, digest, signature,
						tpPSignatureCertificate, psUIPAddress, psUIPPort, psUAccept, psUAcceptCharset,
						psUAcceptEncoding, psUAcceptLanguage, psUUserAgent, psUHttpMethod, psUDeviceID, psUGeoLocation)
				.getBody();

		Assert.assertNotNull(paymentInitiationStatus);

		return paymentInitiationStatus;
	}

	private String getStartAuthorisationWithPsuAuthentication(PaymentInitationRequestResponse201 resp) {
		return (String) resp.getLinks().get("startAuthorisationWithPsuAuthentication");
	}

	public UpdatePsuAuthenticationResponse authCode(UpdatePsuAuthenticationResponse psuAuthenticationResponse) {
		UUID xRequestID = UUID.randomUUID();
		Map<String, String> scaAuthenticationData = new HashMap<>();
		scaAuthenticationData.put("scaAuthenticationData", "123456");
		String PSU_ID = paymentCase.getPsuId();
		UpdatePsuAuthenticationResponse updatePaymentPsuData = paymentApi
				._updatePaymentPsuData(xRequestID, paymentService, paymentProduct, paymentId, authorisationId,
						scaAuthenticationData, digest, signature, tpPSignatureCertificate, PSU_ID, psUIDType,
						psUCorporateID, psUCorporateIDType, psUIPAddress, psUIPPort, psUAccept, psUAcceptCharset,
						psUAcceptEncoding, psUAcceptLanguage, psUUserAgent, psUHttpMethod, psUDeviceID, psUGeoLocation)
				.getBody();

		Assert.assertNotNull(updatePaymentPsuData);

		return updatePaymentPsuData;
	}

	public UpdatePsuAuthenticationResponse choseScaMethod(UpdatePsuAuthenticationResponse psuAuthenticationResponse) {
		UUID xRequestID = UUID.randomUUID();
		SelectPsuAuthenticationMethod selectPsuAuthenticationMethod = new SelectPsuAuthenticationMethod();
		Assert.assertNotNull(psuAuthenticationResponse.getScaMethods());
		Assert.assertFalse(psuAuthenticationResponse.getScaMethods().isEmpty());
		selectPsuAuthenticationMethod.setAuthenticationMethodId(
				psuAuthenticationResponse.getScaMethods().iterator().next().getAuthenticationMethodId());
		String PSU_ID = paymentCase.getPsuId();
		UpdatePsuAuthenticationResponse updatePaymentPsuData = paymentApi
				._updatePaymentPsuData(xRequestID, paymentService, paymentProduct, paymentId, authorisationId,
						selectPsuAuthenticationMethod, digest, signature, tpPSignatureCertificate, PSU_ID, psUIDType,
						psUCorporateID, psUCorporateIDType, psUIPAddress, psUIPPort, psUAccept, psUAcceptCharset,
						psUAcceptEncoding, psUAcceptLanguage, psUUserAgent, psUHttpMethod, psUDeviceID, psUGeoLocation)
				.getBody();

		Assert.assertNotNull(updatePaymentPsuData);

		return updatePaymentPsuData;
	}
}
