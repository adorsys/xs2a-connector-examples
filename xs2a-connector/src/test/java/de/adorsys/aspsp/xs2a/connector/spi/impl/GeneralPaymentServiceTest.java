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
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.ResponseEntity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class GeneralPaymentServiceTest {
    @InjectMocks
    private GeneralPaymentService generalPaymentService;
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
