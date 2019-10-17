package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.type;

import de.adorsys.aspsp.xs2a.util.TestSpiDataProvider;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPaymentInfo;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class CommonPaymentSpiImplTest {
    private final static String PAYMENT_PRODUCT = "sepa-credit-transfers";
    private static final SpiContextData SPI_CONTEXT_DATA = TestSpiDataProvider.getSpiContextData();

    @InjectMocks
    private CommonPaymentSpiImpl commonPaymentSpi;
    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;

    @Test
    public void executePaymentWithoutSca() {
        SpiResponse<SpiPaymentExecutionResponse> response = commonPaymentSpi.executePaymentWithoutSca(SPI_CONTEXT_DATA, new SpiPaymentInfo(PAYMENT_PRODUCT), spiAspspConsentDataProvider);

        assertTrue(response.hasError());
        assertEquals(MessageErrorCode.SERVICE_NOT_SUPPORTED, response.getErrors().get(0).getErrorCode());
    }

    @Test
    public void verifyScaAuthorisationAndExecutePayment() {
        SpiResponse<SpiPaymentExecutionResponse> response = commonPaymentSpi.verifyScaAuthorisationAndExecutePayment(SPI_CONTEXT_DATA, new SpiScaConfirmation(), new SpiPaymentInfo(PAYMENT_PRODUCT), spiAspspConsentDataProvider);

        assertTrue(response.hasError());
        assertEquals(MessageErrorCode.SERVICE_NOT_SUPPORTED, response.getErrors().get(0).getErrorCode());
    }
}