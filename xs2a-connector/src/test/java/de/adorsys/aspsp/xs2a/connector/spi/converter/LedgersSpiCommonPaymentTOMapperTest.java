/*
 * Copyright 2018-2020 adorsys GmbH & Co KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.aspsp.xs2a.util.TestConfiguration;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTO;
import de.adorsys.ledgers.middleware.client.mappers.PaymentMapperTO;
import de.adorsys.psd2.mapper.Xs2aObjectMapper;
import de.adorsys.psd2.xs2a.core.profile.PaymentType;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPaymentInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, SpringExtension.class})
@ContextConfiguration(classes = {TestConfiguration.class, LedgersSpiPaymentToMapper.class})
class LedgersSpiCommonPaymentTOMapperTest {
    private static final String RAW_PAYMENT_PRODUCT = "pain.001-sepa-credit-transfers";
    private static final String PAYMENT_PRODUCT = "sepa-credit-transfers";

    @InjectMocks
    private LedgersSpiCommonPaymentTOMapper mapper;

    @Spy
    @Autowired
    private PaymentMapperTO paymentMapperTO;

    @Spy
    @Autowired
    private Xs2aObjectMapper xs2aObjectMapper;

    @Spy
    @Autowired
    private LedgersSpiPaymentToMapper ledgersSpiPaymentToMapper;

    @Mock
    private StandardPaymentProductsResolverConnector standardPaymentProductsResolverConnector;
    private JsonReader jsonReader = new JsonReader();

    @Test
    void mapToPaymentTO_rawPaymentSingle() {
        SpiPaymentInfo spiPaymentInfo = jsonReader.getObjectFromFile("json/spi/converter/raw-spi-payment-single.json", SpiPaymentInfo.class);
        when(standardPaymentProductsResolverConnector.isRawPaymentProduct(RAW_PAYMENT_PRODUCT)).thenReturn(true);

        PaymentTO actual = mapper.mapToPaymentTO(PaymentType.SINGLE, spiPaymentInfo);

        PaymentTO expected = jsonReader.getObjectFromFile("json/spi/converter/raw-payment-single-response.json", PaymentTO.class);
        assertEquals(expected, actual);
    }

    @Test
    void mapToPaymentTO_rawPaymentPeriodic() {
        SpiPaymentInfo spiPaymentInfo = jsonReader.getObjectFromFile("json/spi/converter/raw-spi-payment-periodic.json", SpiPaymentInfo.class);
        when(standardPaymentProductsResolverConnector.isRawPaymentProduct(RAW_PAYMENT_PRODUCT)).thenReturn(true);

        PaymentTO actual = mapper.mapToPaymentTO(PaymentType.PERIODIC, spiPaymentInfo);

        PaymentTO expected = jsonReader.getObjectFromFile("json/spi/converter/raw-payment-periodic-response.json", PaymentTO.class);
        assertEquals(expected, actual);
    }

    @Test
    void mapToPaymentTO_rawPaymentPeriodic_wrongJson() {
        SpiPaymentInfo spiPaymentInfo = jsonReader.getObjectFromFile("json/spi/converter/raw-spi-payment-periodic-wrong.json", SpiPaymentInfo.class);
        when(standardPaymentProductsResolverConnector.isRawPaymentProduct(RAW_PAYMENT_PRODUCT)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> mapper.mapToPaymentTO(PaymentType.PERIODIC, spiPaymentInfo));
    }

    @Test
    void mapToPaymentTO_singlePayment() {
        SpiPaymentInfo spiPaymentInfo = jsonReader.getObjectFromFile("json/spi/converter/spi-single-payment.json", SpiPaymentInfo.class);
        when(standardPaymentProductsResolverConnector.isRawPaymentProduct(PAYMENT_PRODUCT)).thenReturn(false);

        PaymentTO actual = mapper.mapToPaymentTO(PaymentType.SINGLE, spiPaymentInfo);

        PaymentTO expected = jsonReader.getObjectFromFile("json/spi/converter/payment-single-response.json", PaymentTO.class);
        assertEquals(expected, actual);
    }

    @Test
    void mapToPaymentTO_periodicPayment() {
        SpiPaymentInfo spiPaymentInfo = jsonReader.getObjectFromFile("json/spi/converter/spi-periodic-payment.json", SpiPaymentInfo.class);
        when(standardPaymentProductsResolverConnector.isRawPaymentProduct(PAYMENT_PRODUCT)).thenReturn(false);

        PaymentTO actual = mapper.mapToPaymentTO(PaymentType.PERIODIC, spiPaymentInfo);

        PaymentTO expected = jsonReader.getObjectFromFile("json/spi/converter/payment-periodic-response.json", PaymentTO.class);
        assertEquals(expected, actual);
    }

    @Test
    void mapToPaymentTO_bulkPayment() {
        SpiPaymentInfo spiPaymentInfo = jsonReader.getObjectFromFile("json/spi/converter/spi-bulk-payment.json", SpiPaymentInfo.class);
        when(standardPaymentProductsResolverConnector.isRawPaymentProduct(PAYMENT_PRODUCT)).thenReturn(false);

        PaymentTO actual = mapper.mapToPaymentTO(PaymentType.BULK, spiPaymentInfo);

        PaymentTO expected = jsonReader.getObjectFromFile("json/spi/converter/payment-bulk-response.json", PaymentTO.class);
        assertEquals(expected, actual);
    }
}