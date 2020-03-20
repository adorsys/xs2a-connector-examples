package de.adorsys.aspsp.xs2a.connector.spi.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.payment.SinglePaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.TransactionStatusTO;
import de.adorsys.ledgers.middleware.api.domain.sca.AuthConfirmationTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.ledgers.rest.client.UserMgmtRestClient;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiCheckConfirmationCodeRequest;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiGetPaymentStatusResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentConfirmationCodeValidationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeneralPaymentServiceTest {
    private static final String ANY_MEDIA_TYPE = "*/*";
    private static final String JSON_MEDIA_TYPE = "application/json";
    private static final String XML_MEDIA_TYPE = "application/xml";
    private static final String PSU_MESSAGE = "Mocked PSU message from SPI for this payment";
    private static final String MOCK_XML_BODY = "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.002.001.03\"><CstmrPmtStsRpt><GrpHdr><MsgId>4572457256725689726906</MsgId><CreDtTm>2017-02-14T20:24:56.021Z</CreDtTm><DbtrAgt><FinInstnId><BIC>ABCDDEFF</BIC></FinInstnId></DbtrAgt><CdtrAgt><FinInstnId><BIC>DCBADEFF</BIC></FinInstnId></CdtrAgt></GrpHdr><OrgnlGrpInfAndSts><OrgnlMsgId>MIPI-123456789RI-123456789</OrgnlMsgId><OrgnlMsgNmId>pain.001.001.03</OrgnlMsgNmId><OrgnlCreDtTm>2017-02-14T20:23:34.000Z</OrgnlCreDtTm><OrgnlNbOfTxs>1</OrgnlNbOfTxs><OrgnlCtrlSum>123</OrgnlCtrlSum><GrpSts>ACCT</GrpSts></OrgnlGrpInfAndSts><OrgnlPmtInfAndSts><OrgnlPmtInfId>BIPI-123456789RI-123456789</OrgnlPmtInfId><OrgnlNbOfTxs>1</OrgnlNbOfTxs><OrgnlCtrlSum>123</OrgnlCtrlSum><PmtInfSts>ACCT</PmtInfSts></OrgnlPmtInfAndSts></CstmrPmtStsRpt></Document>";
    private static final byte[] ASPSP_CONSENT_DATA = "".getBytes();
    private static final String CONFIRMATION_CODE = "confirmation code";
    private static final String AUTHORISATION_ID = "authorisation id";

    private JsonReader jsonReader = new JsonReader();

    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;
    @Mock
    private AuthRequestInterceptor authRequestInterceptor;
    @Mock
    private AspspConsentDataService consentDataService;
    @Mock
    private FeignExceptionReader feignExceptionReader;
    @Mock
    private PaymentRestClient paymentRestClient;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private LedgersSpiPaymentMapper paymentMapper;
    @Mock
    private MultilevelScaService multilevelScaService;
    @Mock
    private UserMgmtRestClient userMgmtRestClient;

    private GeneralPaymentService generalPaymentService;

    @BeforeEach
    void setUp() {
        generalPaymentService = new GeneralPaymentService(paymentRestClient, authRequestInterceptor, consentDataService, feignExceptionReader, objectMapper, MOCK_XML_BODY, multilevelScaService, userMgmtRestClient);
    }

    @Test
    void getPaymentStatusById_withXmlMediaType_shouldReturnMockResponse() {
        // Given
        byte[] xmlBody = MOCK_XML_BODY.getBytes();
        byte[] aspspConsentData = "".getBytes();
        SpiGetPaymentStatusResponse expectedResponse = new SpiGetPaymentStatusResponse(TransactionStatus.ACSP, null, XML_MEDIA_TYPE, xmlBody, PSU_MESSAGE);

        // When
        SpiResponse<SpiGetPaymentStatusResponse> spiResponse = generalPaymentService.getPaymentStatusById(PaymentTypeTO.SINGLE, XML_MEDIA_TYPE, "payment id", TransactionStatus.ACSP, aspspConsentData);

        // Then
        assertFalse(spiResponse.hasError());

        SpiGetPaymentStatusResponse payload = spiResponse.getPayload();
        assertEquals(expectedResponse, payload);
    }

    @Test
    void getPaymentStatusById_withNotAcspStatus_shouldReturnSameStatus() {
        // Given
        byte[] aspspConsentData = "".getBytes();
        SpiGetPaymentStatusResponse expectedResponse = new SpiGetPaymentStatusResponse(TransactionStatus.ACSC, null, JSON_MEDIA_TYPE, null, PSU_MESSAGE);

        // When
        SpiResponse<SpiGetPaymentStatusResponse> spiResponse = generalPaymentService.getPaymentStatusById(PaymentTypeTO.SINGLE, ANY_MEDIA_TYPE, "payment id", TransactionStatus.ACSC, aspspConsentData);

        // Then
        assertFalse(spiResponse.hasError());

        SpiGetPaymentStatusResponse payload = spiResponse.getPayload();
        assertEquals(expectedResponse, payload);
    }

    @Test
    void getPaymentByIdTransactionStatusRCVD() {
        //Given
        SpiPayment initialPayment = getSpiSingle(TransactionStatus.RCVD, "initialPayment");
        //When
        SpiResponse<SpiPayment> paymentById = generalPaymentService.getPaymentById(initialPayment, null, null, null, null);
        //Then
        assertTrue(paymentById.isSuccessful());
        assertEquals(initialPayment, paymentById.getPayload());
    }

    @Test
    void getPaymentByIdTransactionStatusACSP() {
        //Given
        SpiPayment initialPayment = getSpiSingle(TransactionStatus.ACSP, "initialPayment");
        SpiPayment paymentAspsp = getSpiSingle(TransactionStatus.ACSP, "paymentAspsp");

        SinglePaymentTO singlePaymentTO = new SinglePaymentTO();
        SCAPaymentResponseTO sca = new SCAPaymentResponseTO();
        sca.setPaymentId(initialPayment.getPaymentId());
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("accessToken");
        sca.setBearerToken(bearerTokenTO);
        byte[] aspspConsentData = "".getBytes();

        doReturn(ResponseEntity.ok(paymentAspsp))
                .when(paymentRestClient).getPaymentById(paymentAspsp.getPaymentId());
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(aspspConsentData);
        doNothing()
                .when(authRequestInterceptor).setAccessToken(anyString());
        when(consentDataService.response(aspspConsentData, SCAPaymentResponseTO.class))
                .thenReturn(sca);
        doReturn(singlePaymentTO)
                .when(objectMapper).convertValue(paymentAspsp, SinglePaymentTO.class);
        doReturn(paymentAspsp)
                .when(paymentMapper).toSpiSinglePayment(singlePaymentTO);

        //When
        SpiResponse<SpiPayment> paymentById = generalPaymentService.getPaymentById(initialPayment, spiAspspConsentDataProvider, SinglePaymentTO.class, paymentMapper::toSpiSinglePayment, PaymentTypeTO.SINGLE);
        //Then
        assertTrue(paymentById.isSuccessful());
        assertEquals(paymentAspsp, paymentById.getPayload());
    }

    @Test
    void checkConfirmationCode() {
        //Given
        SpiCheckConfirmationCodeRequest spiCheckConfirmationCodeRequest = new SpiCheckConfirmationCodeRequest(CONFIRMATION_CODE, AUTHORISATION_ID);
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(ASPSP_CONSENT_DATA);
        SCAPaymentResponseTO scaPaymentResponse = buildScaPaymentResponseTO();
        when(consentDataService.response(ASPSP_CONSENT_DATA, SCAPaymentResponseTO.class))
                .thenReturn(scaPaymentResponse);
        AuthConfirmationTO authConfirmationTO = new AuthConfirmationTO()
                                                        .success(true)
                                                        .transactionStatus(TransactionStatusTO.ACTC);
        when(userMgmtRestClient.verifyAuthConfirmationCode(AUTHORISATION_ID, CONFIRMATION_CODE))
                .thenReturn(ResponseEntity.ok(authConfirmationTO));

        // When
        SpiResponse<SpiPaymentConfirmationCodeValidationResponse> confirmationCodeResponse = generalPaymentService.checkConfirmationCode(spiCheckConfirmationCodeRequest, spiAspspConsentDataProvider);

        // Then
        assertTrue(confirmationCodeResponse.isSuccessful());
        SpiPaymentConfirmationCodeValidationResponse confirmationCodePayload = confirmationCodeResponse.getPayload();
        assertEquals(ScaStatus.FINALISED, confirmationCodePayload.getScaStatus());
        assertEquals(TransactionStatus.ACTC, confirmationCodePayload.getTransactionStatus());
    }

    @Test
    void checkConfirmationCode_cancelledTransactionFromLedgers_shouldReturnFinalised() {
        //Given
        SpiCheckConfirmationCodeRequest spiCheckConfirmationCodeRequest = new SpiCheckConfirmationCodeRequest(CONFIRMATION_CODE, AUTHORISATION_ID);
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(ASPSP_CONSENT_DATA);
        SCAPaymentResponseTO scaPaymentResponse = buildScaPaymentResponseTO();
        when(consentDataService.response(ASPSP_CONSENT_DATA, SCAPaymentResponseTO.class))
                .thenReturn(scaPaymentResponse);
        AuthConfirmationTO authConfirmationTO = new AuthConfirmationTO()
                                                        .success(true)
                                                        .transactionStatus(TransactionStatusTO.CANC);
        when(userMgmtRestClient.verifyAuthConfirmationCode(AUTHORISATION_ID, CONFIRMATION_CODE))
                .thenReturn(ResponseEntity.ok(authConfirmationTO));

        // When
        SpiResponse<SpiPaymentConfirmationCodeValidationResponse> confirmationCodeResponse = generalPaymentService.checkConfirmationCode(spiCheckConfirmationCodeRequest, spiAspspConsentDataProvider);

        // Then
        assertTrue(confirmationCodeResponse.isSuccessful());
        SpiPaymentConfirmationCodeValidationResponse confirmationCodePayload = confirmationCodeResponse.getPayload();
        assertEquals(ScaStatus.FINALISED, confirmationCodePayload.getScaStatus());
        assertEquals(TransactionStatus.CANC, confirmationCodePayload.getTransactionStatus());
    }

    @Test
    void checkConfirmationCode_noResponseBodyFromLedgers_shouldReturnFailed() {
        //Given
        SpiCheckConfirmationCodeRequest spiCheckConfirmationCodeRequest = new SpiCheckConfirmationCodeRequest(CONFIRMATION_CODE, AUTHORISATION_ID);
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(ASPSP_CONSENT_DATA);
        SCAPaymentResponseTO scaPaymentResponse = buildScaPaymentResponseTO();
        when(consentDataService.response(ASPSP_CONSENT_DATA, SCAPaymentResponseTO.class))
                .thenReturn(scaPaymentResponse);
        when(userMgmtRestClient.verifyAuthConfirmationCode(AUTHORISATION_ID, CONFIRMATION_CODE))
                .thenReturn(ResponseEntity.ok().build());

        // When
        SpiResponse<SpiPaymentConfirmationCodeValidationResponse> confirmationCodeResponse = generalPaymentService.checkConfirmationCode(spiCheckConfirmationCodeRequest, spiAspspConsentDataProvider);

        // Then
        assertTrue(confirmationCodeResponse.isSuccessful());
        SpiPaymentConfirmationCodeValidationResponse confirmationCodePayload = confirmationCodeResponse.getPayload();
        assertEquals(ScaStatus.FAILED, confirmationCodePayload.getScaStatus());
        assertEquals(TransactionStatus.RJCT, confirmationCodePayload.getTransactionStatus());
    }

    @Test
    void checkConfirmationCode_partiallyAuthorised() {
        //Given
        SpiCheckConfirmationCodeRequest spiCheckConfirmationCodeRequest = new SpiCheckConfirmationCodeRequest(CONFIRMATION_CODE, AUTHORISATION_ID);
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(ASPSP_CONSENT_DATA);
        SCAPaymentResponseTO scaPaymentResponse = buildScaPaymentResponseTO();
        when(consentDataService.response(ASPSP_CONSENT_DATA, SCAPaymentResponseTO.class))
                .thenReturn(scaPaymentResponse);
        AuthConfirmationTO authConfirmationTO = new AuthConfirmationTO()
                                                        .success(true)
                                                        .partiallyAuthorised(true);
        when(userMgmtRestClient.verifyAuthConfirmationCode(AUTHORISATION_ID, CONFIRMATION_CODE))
                .thenReturn(ResponseEntity.ok(authConfirmationTO));

        // When
        SpiResponse<SpiPaymentConfirmationCodeValidationResponse> confirmationCodeResponse = generalPaymentService.checkConfirmationCode(spiCheckConfirmationCodeRequest, spiAspspConsentDataProvider);

        // Then
        assertTrue(confirmationCodeResponse.isSuccessful());
        SpiPaymentConfirmationCodeValidationResponse confirmationCodePayload = confirmationCodeResponse.getPayload();
        assertEquals(ScaStatus.FINALISED, confirmationCodePayload.getScaStatus());
        assertEquals(TransactionStatus.PATC, confirmationCodePayload.getTransactionStatus());
    }

    @Test
    void checkConfirmationCode_noTransactionStatusFromLedgers_shouldReturnFailed() {
        //Given
        SpiCheckConfirmationCodeRequest spiCheckConfirmationCodeRequest = new SpiCheckConfirmationCodeRequest(CONFIRMATION_CODE, AUTHORISATION_ID);
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(ASPSP_CONSENT_DATA);
        SCAPaymentResponseTO scaPaymentResponse = buildScaPaymentResponseTO();
        when(consentDataService.response(ASPSP_CONSENT_DATA, SCAPaymentResponseTO.class))
                .thenReturn(scaPaymentResponse);
        AuthConfirmationTO authConfirmationTO = new AuthConfirmationTO()
                                                        .success(true);
        when(userMgmtRestClient.verifyAuthConfirmationCode(AUTHORISATION_ID, CONFIRMATION_CODE))
                .thenReturn(ResponseEntity.ok(authConfirmationTO));

        // When
        SpiResponse<SpiPaymentConfirmationCodeValidationResponse> confirmationCodeResponse = generalPaymentService.checkConfirmationCode(spiCheckConfirmationCodeRequest, spiAspspConsentDataProvider);

        // Then
        assertTrue(confirmationCodeResponse.isSuccessful());
        SpiPaymentConfirmationCodeValidationResponse confirmationCodePayload = confirmationCodeResponse.getPayload();
        assertEquals(ScaStatus.FAILED, confirmationCodePayload.getScaStatus());
        assertEquals(TransactionStatus.RJCT, confirmationCodePayload.getTransactionStatus());
    }

    @Test
    void checkConfirmationCode_feignException_shouldReturnSpiError() {
        //Given
        SpiCheckConfirmationCodeRequest spiCheckConfirmationCodeRequest = new SpiCheckConfirmationCodeRequest(CONFIRMATION_CODE, AUTHORISATION_ID);
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(ASPSP_CONSENT_DATA);
        SCAPaymentResponseTO scaPaymentResponse = buildScaPaymentResponseTO();
        when(consentDataService.response(ASPSP_CONSENT_DATA, SCAPaymentResponseTO.class))
                .thenReturn(scaPaymentResponse);
        FeignException feignException = buildFeignException();
        when(userMgmtRestClient.verifyAuthConfirmationCode(AUTHORISATION_ID, CONFIRMATION_CODE))
                .thenThrow(feignException);
        String errorMessage = "some dev error message";
        when(feignExceptionReader.getErrorMessage(feignException)).thenReturn(errorMessage);

        // When
        SpiResponse<SpiPaymentConfirmationCodeValidationResponse> confirmationCodeResponse = generalPaymentService.checkConfirmationCode(spiCheckConfirmationCodeRequest, spiAspspConsentDataProvider);

        // Then
        assertTrue(confirmationCodeResponse.hasError());
        List<TppMessage> actualErrors = confirmationCodeResponse.getErrors();
        TppMessage expectedError = new TppMessage(MessageErrorCode.PSU_CREDENTIALS_INVALID, errorMessage);
        assertTrue(actualErrors.contains(expectedError));
    }

    @Test
    void firstCallInstantiatingPayment_LedgersError() {
        SpiPayment initialPayment = getSpiSingle(TransactionStatus.RCVD, "initialPayment");

        SpiAccountReference spiAccountReference = jsonReader.getObjectFromFile("json/spi/impl/account-reference.json", SpiAccountReference.class);

        SpiSinglePaymentInitiationResponse responsePayload = new SpiSinglePaymentInitiationResponse();

        SpiPsuData spiPsuData = SpiPsuData.builder().build();

        when(multilevelScaService.isMultilevelScaRequired(spiPsuData, Collections.singleton(spiAccountReference))).thenThrow(buildFeignException()); //spiPsuData, spiAccountReferences

        SpiResponse<SpiPaymentInitiationResponse> actualResponse = generalPaymentService.firstCallInstantiatingPayment(PaymentTypeTO.SINGLE, initialPayment, spiAspspConsentDataProvider, responsePayload, spiPsuData, Collections.singleton(spiAccountReference));

        assertFalse(actualResponse.getErrors().isEmpty());
        assertNull(actualResponse.getPayload());
        assertTrue(actualResponse.getErrors().contains(new TppMessage(MessageErrorCode.FORMAT_ERROR_UNKNOWN_ACCOUNT)));
    }

    @Test
    void firstCallInstantiatingPayment_Success() {
        SCAPaymentResponseTO response = new SCAPaymentResponseTO();
        response.setPaymentId("myPaymentId");
        response.setTransactionStatus(TransactionStatusTO.RCVD);
        response.setPaymentProduct("sepa-credit-transfers");
        response.setPaymentType(PaymentTypeTO.SINGLE);

        SpiPayment initialPayment = getSpiSingle(TransactionStatus.RCVD, "initialPayment");

        SpiAccountReference spiAccountReference = jsonReader.getObjectFromFile("json/spi/impl/account-reference.json", SpiAccountReference.class);

        SpiSinglePaymentInitiationResponse responsePayload = new SpiSinglePaymentInitiationResponse();

        SpiPsuData spiPsuData = SpiPsuData.builder().build();

        when(multilevelScaService.isMultilevelScaRequired(spiPsuData, Collections.singleton(spiAccountReference))).thenReturn(true); //spiPsuData, spiAccountReferences

        SpiResponse<SpiPaymentInitiationResponse> actualResponse = generalPaymentService.firstCallInstantiatingPayment(PaymentTypeTO.SINGLE, initialPayment, spiAspspConsentDataProvider, responsePayload, spiPsuData, Collections.singleton(spiAccountReference));

        assertTrue(actualResponse.getErrors().isEmpty());
        assertNotNull(actualResponse.getPayload());
        verify(consentDataService, times(1)).store(response, false);
        verify(spiAspspConsentDataProvider, times(1)).updateAspspConsentData(any());
    }

    private SpiSinglePayment getSpiSingle(TransactionStatus transactionStatus, String agent) {
        SpiSinglePayment spiPayment = new SpiSinglePayment(PaymentProductTO.SEPA.getValue());
        spiPayment.setPaymentId("myPaymentId");
        spiPayment.setCreditorAgent(agent);
        spiPayment.setPaymentStatus(transactionStatus);
        return spiPayment;
    }

    private FeignException buildFeignException() {
        return FeignException.errorStatus("User doesn't have access to the requested account",
                                          buildErrorResponseForbidden());
    }

    private Response buildErrorResponseForbidden() {
        return Response.builder()
                       .status(403)
                       .request(Request.create(Request.HttpMethod.GET, "", Collections.emptyMap(), null))
                       .headers(Collections.emptyMap())
                       .build();
    }

    @NotNull
    private SCAPaymentResponseTO buildScaPaymentResponseTO() {
        SCAPaymentResponseTO sca = new SCAPaymentResponseTO();
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("accessToken");
        sca.setBearerToken(bearerTokenTO);
        return sca;
    }
}
