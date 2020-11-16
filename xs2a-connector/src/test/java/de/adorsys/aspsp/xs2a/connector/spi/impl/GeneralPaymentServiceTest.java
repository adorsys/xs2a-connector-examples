package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.aspsp.xs2a.connector.cms.CmsPsuPisClient;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.ScaResponseMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.payment.TransactionStatusTO;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.OpTypeTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.rest.client.PaymentRestClient;
import de.adorsys.ledgers.rest.client.RedirectScaRestClient;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.service.RequestProviderService;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiGetPaymentStatusResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeneralPaymentServiceTest {
    private final static String PAYMENT_PRODUCT = "sepa-credit-transfers";
    private static final String ANY_MEDIA_TYPE = MediaType.ALL_VALUE;
    private static final String JSON_MEDIA_TYPE = MediaType.APPLICATION_JSON_VALUE;
    private static final String XML_MEDIA_TYPE = MediaType.APPLICATION_XML_VALUE;
    private static final String PSU_MESSAGE = "Mocked PSU message from SPI for this payment";

    private final JsonReader jsonReader = new JsonReader();

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
    private LedgersSpiPaymentMapper paymentMapper;
    @Mock
    private MultilevelScaService multilevelScaService;
    @Mock
    private RedirectScaRestClient redirectScaRestClient;
    @Mock
    private CmsPsuPisClient cmsPsuPisClient;
    @Mock
    private RequestProviderService requestProviderService;

    @Spy
    private ScaResponseMapper scaResponseMapper = Mappers.getMapper(ScaResponseMapper.class);

    private GeneralPaymentService generalPaymentService;
    private String paymentBodyXml;

    @BeforeEach
    void setUp() {
        paymentBodyXml = jsonReader.getStringFromFile("xml/payment-body.xml");
        generalPaymentService = new GeneralPaymentService(paymentRestClient, authRequestInterceptor, consentDataService,
                                                          feignExceptionReader, paymentBodyXml, multilevelScaService,
                                                          redirectScaRestClient, scaResponseMapper,
                                                          cmsPsuPisClient, requestProviderService);
    }

    @Test
    void getPaymentStatusById_withXmlMediaType_shouldReturnMockResponse() {
        // Given
        byte[] xmlBody = paymentBodyXml.getBytes();
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
        SpiResponse<SpiPayment> paymentById = generalPaymentService.getPaymentById(initialPayment, null, null);
        //Then
        assertTrue(paymentById.isSuccessful());
        assertEquals(initialPayment, paymentById.getPayload());
    }

    @Test
    void getPaymentByIdTransactionStatusACSP() {
        //Given
        SpiPayment initialPayment = getSpiSingle(TransactionStatus.ACSP, "initialPayment");
        SpiPayment paymentAspsp = getSpiSingle(TransactionStatus.ACSP, "paymentAspsp");

        PaymentTO paymentTO = new PaymentTO();
        paymentTO.setPaymentId("myPaymentId");
        paymentTO.setTransactionStatus(TransactionStatusTO.ACSP);

        GlobalScaResponseTO sca = new GlobalScaResponseTO();
        sca.setOperationObjectId(initialPayment.getPaymentId());
        BearerTokenTO bearerTokenTO = new BearerTokenTO();
        bearerTokenTO.setAccess_token("accessToken");
        sca.setBearerToken(bearerTokenTO);
        byte[] aspspConsentData = "".getBytes();

        doReturn(ResponseEntity.ok(paymentTO))
                .when(paymentRestClient).getPaymentById(paymentTO.getPaymentId());
        when(spiAspspConsentDataProvider.loadAspspConsentData())
                .thenReturn(aspspConsentData);
        doNothing()
                .when(authRequestInterceptor).setAccessToken(anyString());
        when(consentDataService.response(aspspConsentData))
                .thenReturn(sca);
        doReturn(paymentAspsp)
                .when(paymentMapper).toSpiSinglePayment(paymentTO);

        //When
        SpiResponse<SpiPayment> paymentById = generalPaymentService.getPaymentById(initialPayment, spiAspspConsentDataProvider, paymentMapper::toSpiSinglePayment);
        //Then
        assertTrue(paymentById.isSuccessful());
        assertEquals(paymentAspsp, paymentById.getPayload());
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
        GlobalScaResponseTO response = new GlobalScaResponseTO();
        response.setOperationObjectId("myPaymentId");
        response.setOpType(OpTypeTO.PAYMENT);
        response.setMultilevelScaRequired(true);

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
        SpiSinglePayment spiPayment = new SpiSinglePayment(PAYMENT_PRODUCT);
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
}
