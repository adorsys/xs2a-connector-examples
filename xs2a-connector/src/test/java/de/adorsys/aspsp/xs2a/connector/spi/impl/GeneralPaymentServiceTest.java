package de.adorsys.aspsp.xs2a.connector.spi.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.payment.SinglePaymentTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiGetPaymentStatusResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.ResponseEntity;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class GeneralPaymentServiceTest {
    private static final String ANY_MEDIA_TYPE = "*/*";
    private static final String JSON_MEDIA_TYPE = "application/json";
    private static final String XML_MEDIA_TYPE = "application/xml";
    private static final String MOCK_XML_BODY = "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.002.001.03\"><CstmrPmtStsRpt><GrpHdr><MsgId>4572457256725689726906</MsgId><CreDtTm>2017-02-14T20:24:56.021Z</CreDtTm><DbtrAgt><FinInstnId><BIC>ABCDDEFF</BIC></FinInstnId></DbtrAgt><CdtrAgt><FinInstnId><BIC>DCBADEFF</BIC></FinInstnId></CdtrAgt></GrpHdr><OrgnlGrpInfAndSts><OrgnlMsgId>MIPI-123456789RI-123456789</OrgnlMsgId><OrgnlMsgNmId>pain.001.001.03</OrgnlMsgNmId><OrgnlCreDtTm>2017-02-14T20:23:34.000Z</OrgnlCreDtTm><OrgnlNbOfTxs>1</OrgnlNbOfTxs><OrgnlCtrlSum>123</OrgnlCtrlSum><GrpSts>ACCT</GrpSts></OrgnlGrpInfAndSts><OrgnlPmtInfAndSts><OrgnlPmtInfId>BIPI-123456789RI-123456789</OrgnlPmtInfId><OrgnlNbOfTxs>1</OrgnlNbOfTxs><OrgnlCtrlSum>123</OrgnlCtrlSum><PmtInfSts>ACCT</PmtInfSts></OrgnlPmtInfAndSts></CstmrPmtStsRpt></Document>";

    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;
    @Mock
    private AuthRequestInterceptor authRequestInterceptor;
    @Mock
    private AspspConsentDataService consentDataService;
    @Mock
    private PaymentRestClient paymentRestClient;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private LedgersSpiPaymentMapper paymentMapper;
    @Mock
    private MultilevelScaService multilevelScaService;

    private GeneralPaymentService generalPaymentService;

    @Before
    public void setUp() {
        generalPaymentService = new GeneralPaymentService(paymentRestClient, authRequestInterceptor, consentDataService, null, objectMapper, MOCK_XML_BODY, multilevelScaService);
    }

    @Test
    public void getPaymentStatusById_withXmlMediaType_shouldReturnMockResponse() {
        // Given
        byte[] xmlBody = MOCK_XML_BODY.getBytes();
        byte[] aspspConsentData = "".getBytes();
        SpiGetPaymentStatusResponse expectedResponse = new SpiGetPaymentStatusResponse(TransactionStatus.ACSP, null, XML_MEDIA_TYPE, xmlBody);

        // When
        SpiResponse<SpiGetPaymentStatusResponse> spiResponse = generalPaymentService.getPaymentStatusById(PaymentTypeTO.SINGLE, XML_MEDIA_TYPE, "payment id", TransactionStatus.ACSP, aspspConsentData);

        // Then
        assertFalse(spiResponse.hasError());

        SpiGetPaymentStatusResponse payload = spiResponse.getPayload();
        assertEquals(expectedResponse, payload);
    }

    @Test
    public void getPaymentStatusById_withNotAcspStatus_shouldReturnSameStatus() {
        // Given
        byte[] aspspConsentData = "".getBytes();
        SpiGetPaymentStatusResponse expectedResponse = new SpiGetPaymentStatusResponse(TransactionStatus.ACSC, null, JSON_MEDIA_TYPE, null);

        // When
        SpiResponse<SpiGetPaymentStatusResponse> spiResponse = generalPaymentService.getPaymentStatusById(PaymentTypeTO.SINGLE, ANY_MEDIA_TYPE, "payment id", TransactionStatus.ACSC, aspspConsentData);

        // Then
        assertFalse(spiResponse.hasError());

        SpiGetPaymentStatusResponse payload = spiResponse.getPayload();
        assertEquals(expectedResponse, payload);
    }

    @Test
    public void getPaymentByIdTransactionStatusRCVD() {
        //Given
        SpiPayment initialPayment = getSpiSingle(TransactionStatus.RCVD, "initialPayment");
        //When
        SpiResponse<SpiPayment> paymentById = generalPaymentService.getPaymentById(initialPayment, null, null, null, null);
        //Then
        assertTrue(paymentById.isSuccessful());
        assertEquals(initialPayment, paymentById.getPayload());
    }

    @Test
    public void getPaymentByIdTransactionStatusACSP() {
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

    private SpiSinglePayment getSpiSingle(TransactionStatus transactionStatus, String agent) {
        SpiSinglePayment spiPayment = new SpiSinglePayment(PaymentProductTO.SEPA.getValue());
        spiPayment.setPaymentId("myPaymentId");
        spiPayment.setCreditorAgent(agent);
        spiPayment.setPaymentStatus(transactionStatus);
        return spiPayment;
    }
}
