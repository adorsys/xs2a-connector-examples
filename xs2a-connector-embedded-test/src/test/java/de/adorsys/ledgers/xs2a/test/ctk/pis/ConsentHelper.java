package de.adorsys.ledgers.xs2a.test.ctk.pis;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import de.adorsys.ledgers.xs2a.api.client.ConsentApiClient;
import de.adorsys.psd2.model.AccountAccess;
import de.adorsys.psd2.model.AccountAccess.AllPsd2Enum;
import de.adorsys.psd2.model.ConsentStatus;
import de.adorsys.psd2.model.ConsentStatusResponse200;
import de.adorsys.psd2.model.Consents;
import de.adorsys.psd2.model.ConsentsResponse201;
import de.adorsys.psd2.model.PsuData;
import de.adorsys.psd2.model.SelectPsuAuthenticationMethod;
import de.adorsys.psd2.model.UpdatePsuAuthentication;
import de.adorsys.psd2.model.UpdatePsuAuthenticationResponse;

public class ConsentHelper {

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

	private final String PSU_ID;
	private final ConsentApiClient consentApi;

	public ConsentHelper(ConsentApiClient consentApi, String PSU_ID) {
		this.consentApi = consentApi;
		this.PSU_ID = PSU_ID;
	}

	public ResponseEntity<ConsentsResponse201> createConsent() {
		UUID xRequestID = UUID.randomUUID();
		String tpPRedirectPreferred = "false";
		String tpPRedirectURI = null;
		String tpPNokRedirectURI = null;
		Boolean tpPExplicitAuthorisationPreferred = false;
		Consents consents = new Consents();
		AccountAccess access = new AccountAccess();
		access.setAllPsd2(AllPsd2Enum.ALLACCOUNTS);
		consents.setAccess(access);
		consents.setFrequencyPerDay(4);
		consents.setRecurringIndicator(true);
		consents.setValidUntil(LocalDate.of(2019, 11, 30));
		ResponseEntity<ConsentsResponse201> consentsResponse201 = consentApi._createConsent(xRequestID, consents,
				digest, signature, tpPSignatureCertificate, PSU_ID, psUIDType, psUCorporateID, psUCorporateIDType,
				tpPRedirectPreferred, tpPRedirectURI, tpPNokRedirectURI, tpPExplicitAuthorisationPreferred,
				psUIPAddress, psUIPPort, psUAccept, psUAcceptCharset, psUAcceptEncoding, psUAcceptLanguage,
				psUUserAgent, psUHttpMethod, psUDeviceID, psUGeoLocation);

		Assert.assertNotNull(consentsResponse201);
		Assert.assertEquals(HttpStatus.CREATED, consentsResponse201.getStatusCode());
		ConsentsResponse201 consent201 = consentsResponse201.getBody();
		Assert.assertNotNull(getStartAuthorisationWithPsuAuthentication(consent201));

		Assert.assertNotNull(consent201.getConsentId());
		Assert.assertNotNull(consent201.getConsentStatus());
		Assert.assertEquals(ConsentStatus.RECEIVED, consent201.getConsentStatus());

		return consentsResponse201;
	}

	public ResponseEntity<UpdatePsuAuthenticationResponse> login(
			ResponseEntity<ConsentsResponse201> createConsentResp) {
		ConsentsResponse201 consentsResponse201 = createConsentResp.getBody();
		String startAuthorisationWithPsuAuthentication = getStartAuthorisationWithPsuAuthentication(
				consentsResponse201);
		String consentId = consentsResponse201.getConsentId();
		String authorisationId = StringUtils
				.substringBefore(StringUtils.substringAfterLast(startAuthorisationWithPsuAuthentication, "/"), "?");
		UpdatePsuAuthentication updatePsuAuthentication = new UpdatePsuAuthentication();
		updatePsuAuthentication.setPsuData(new PsuData().password("12345"));
		UUID xRequestID = UUID.randomUUID();

		ResponseEntity<UpdatePsuAuthenticationResponse> updateConsentsPsuDataResponse = consentApi
				._updateConsentsPsuData(xRequestID, consentId, authorisationId, updatePsuAuthentication, digest,
						signature, tpPSignatureCertificate, PSU_ID, psUIDType, psUCorporateID, psUCorporateIDType,
						psUIPAddress, psUIPPort, psUAccept, psUAcceptCharset, psUAcceptEncoding, psUAcceptLanguage,
						psUUserAgent, psUHttpMethod, psUDeviceID, psUGeoLocation);

		return updateConsentsPsuDataResponse;
	}

	public ResponseEntity<ConsentStatusResponse200> loadConsentStatus(String authorisationUrl) {
		AuthUrl authUrl = AuthUrl.parse(authorisationUrl);
		String encryptedConsentId = authUrl.getEncryptedConsentId();

		UUID xRequestID = UUID.randomUUID();
		ResponseEntity<ConsentStatusResponse200> consentStatus = consentApi._getConsentStatus(encryptedConsentId,
				xRequestID, digest, signature, tpPSignatureCertificate, psUIPAddress, psUIPPort, psUAccept,
				psUAcceptCharset, psUAcceptEncoding, psUAcceptLanguage, psUUserAgent, psUHttpMethod, psUDeviceID,
				psUGeoLocation);

		Assert.assertNotNull(consentStatus);
		Assert.assertEquals(HttpStatus.OK, consentStatus.getStatusCode());
		return consentStatus;
	}

	private String getStartAuthorisationWithPsuAuthentication(ConsentsResponse201 consent201) {
		return (String) consent201.getLinks().get("startAuthorisationWithPsuAuthentication");
	}

	public ResponseEntity<UpdatePsuAuthenticationResponse> authCode(UpdatePsuAuthenticationResponse authResponse) {
		AuthUrl authUrl = AuthUrl.parse((String) authResponse.getLinks().get("authoriseTransaction"));
		String authorisationId = authUrl.getAuthorizationId();
		String encryptedConsentId = authUrl.getEncryptedConsentId();
		
		UUID xRequestID = UUID.randomUUID();
		Map<String, String> scaAuthenticationData = new HashMap<>();
		scaAuthenticationData.put("scaAuthenticationData", "123456");
		
		ResponseEntity<UpdatePsuAuthenticationResponse> authCodeResponse = consentApi
				._updateConsentsPsuData(xRequestID, encryptedConsentId, authorisationId, scaAuthenticationData, digest,
						signature, tpPSignatureCertificate, PSU_ID, psUIDType, psUCorporateID, psUCorporateIDType,
						psUIPAddress, psUIPPort, psUAccept, psUAcceptCharset, psUAcceptEncoding, psUAcceptLanguage,
						psUUserAgent, psUHttpMethod, psUDeviceID, psUGeoLocation);

		Assert.assertNotNull(authCodeResponse);
		Assert.assertEquals(HttpStatus.OK, authCodeResponse.getStatusCode());
		return authCodeResponse;
	}

	public ResponseEntity<UpdatePsuAuthenticationResponse> choseScaMethod(UpdatePsuAuthenticationResponse authResponse) {
		AuthUrl authUrl = AuthUrl.parse((String) authResponse.getLinks().get("selectAuthenticationMethod"));
		String authorisationId = authUrl.getAuthorizationId();
		String encryptedConsentId = authUrl.getEncryptedConsentId();

		UUID xRequestID = UUID.randomUUID();
		SelectPsuAuthenticationMethod selectPsuAuthenticationMethod = new SelectPsuAuthenticationMethod();
		Assert.assertNotNull(authResponse.getScaMethods());
		Assert.assertFalse(authResponse.getScaMethods().isEmpty());
		selectPsuAuthenticationMethod.setAuthenticationMethodId(
				authResponse.getScaMethods().iterator().next().getAuthenticationMethodId());
		ResponseEntity<UpdatePsuAuthenticationResponse> authCodeResponse = consentApi
				._updateConsentsPsuData(xRequestID, encryptedConsentId, authorisationId, selectPsuAuthenticationMethod, digest,
						signature, tpPSignatureCertificate, PSU_ID, psUIDType, psUCorporateID, psUCorporateIDType,
						psUIPAddress, psUIPPort, psUAccept, psUAcceptCharset, psUAcceptEncoding, psUAcceptLanguage,
						psUUserAgent, psUHttpMethod, psUDeviceID, psUGeoLocation);
		
		Assert.assertNotNull(authCodeResponse);

		return authCodeResponse;
	}
}
