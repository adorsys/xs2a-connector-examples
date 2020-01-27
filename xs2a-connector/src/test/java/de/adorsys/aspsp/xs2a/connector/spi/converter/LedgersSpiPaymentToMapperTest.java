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

import de.adorsys.aspsp.xs2a.connector.config.JacksonConfig;
import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTO;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPaymentInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {LedgersSpiPaymentToMapper.class, JacksonConfig.class})
class LedgersSpiPaymentToMapperTest {
    private JsonReader jsonReader = new JsonReader();

    @Autowired
    private LedgersSpiPaymentToMapper ledgersSpiPaymentToMapper;

    @Test
    void toCommonPaymentTO() {
        // Given
        PaymentTO expectedResult = jsonReader.getObjectFromFile("json/mappers/payment-to-from-common-payment-Initiation.json", PaymentTO.class);
        SpiPaymentInfo spiPaymentInfo = jsonReader.getObjectFromFile("json/mappers/common-payment-initiation.json", SpiPaymentInfo.class);

        // When
        PaymentTO actualResult = ledgersSpiPaymentToMapper.toCommonPaymentTO(spiPaymentInfo);

        // Then
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void toPaymentTO_Single() {
        // Given
        PaymentTO expectedResult = jsonReader.getObjectFromFile("json/mappers/payment-to-from-single-payment-Initiation.json", PaymentTO.class);
        SpiPaymentInfo spiPaymentInfo = jsonReader.getObjectFromFile("json/mappers/payment-initiation.json", SpiPaymentInfo.class);

        // When
        PaymentTO actualResult = ledgersSpiPaymentToMapper.toPaymentTO_Single(spiPaymentInfo);

        // Then
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void toPaymentTO_Bulk() {
        // Given
        PaymentTO expectedResult = jsonReader.getObjectFromFile("json/mappers/payment-to-from-bulk-payment-Initiation.json", PaymentTO.class);
        SpiPaymentInfo spiPaymentInfo = jsonReader.getObjectFromFile("json/mappers/bulk-payment-initiation.json", SpiPaymentInfo.class);

        // When
        PaymentTO actualResult = ledgersSpiPaymentToMapper.toPaymentTO_Bulk(spiPaymentInfo);

        // Then
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void toPaymentTO_Periodic_valid() {
        // Given
        PaymentTO expectedResult = jsonReader.getObjectFromFile("json/mappers/payment-to-from-periodic-payment-Initiation.json", PaymentTO.class);
        SpiPaymentInfo spiPaymentInfo = jsonReader.getObjectFromFile("json/mappers/periodic-payment-Initiation.json", SpiPaymentInfo.class);

        // When
        PaymentTO actualResult = ledgersSpiPaymentToMapper.toPaymentTO_Periodic(spiPaymentInfo);

        // Then
        assertEquals(expectedResult, actualResult);
    }

}
