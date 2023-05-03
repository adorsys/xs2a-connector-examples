/*
 * Copyright 2018-2023 adorsys GmbH & Co KG
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
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.RemittanceInformationStructuredTO;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiAmount;
import de.adorsys.psd2.xs2a.spi.domain.payment.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {LedgersSpiPaymentMapper.class, LedgersSpiAccountMapperImpl.class})
class LedgersSpiPaymentMapperTest {

    @Autowired
    private LedgersSpiPaymentMapper ledgersSpiPaymentMapper;
    private static final JsonReader jsonReader = new JsonReader();

    @Test
    void toSpiSinglePayment() {
        //Given
        PaymentTO paymentTO = jsonReader.getObjectFromFile("json/mappers/payment-to-spi-single-payment.json", PaymentTO.class);
        //When
        SpiSinglePayment actual = ledgersSpiPaymentMapper.toSpiSinglePayment(paymentTO);
        //Then
        SpiSinglePayment expected = jsonReader.getObjectFromFile("json/mappers/spi-single-payment.json", SpiSinglePaymentTestable.class);
        assertEquals(expected, actual);
    }

    @Test
    void mapToSpiPeriodicPayment() {
        //Given
        PaymentTO paymentTO = jsonReader.getObjectFromFile("json/mappers/payment-to-spi-periodic-payment.json", PaymentTO.class);
        //When
        SpiPeriodicPayment actual = ledgersSpiPaymentMapper.mapToSpiPeriodicPayment(paymentTO);
        //Then
        SpiPeriodicPayment expected = jsonReader.getObjectFromFile("json/mappers/spi-periodic-payment.json", SpiPeriodicPaymentTestable.class);
        assertEquals(expected, actual);
    }

    @Test
    void mapToSpiBulkPayment() {
        //Given
        PaymentTO paymentTO = jsonReader.getObjectFromFile("json/mappers/payment-to-spi-bulk-payment.json", PaymentTO.class);
        //When
        SpiBulkPayment actual = ledgersSpiPaymentMapper.mapToSpiBulkPayment(paymentTO);
        //Then
        SpiBulkPayment expected = buildSpiBulkPayment();
        assertEquals(expected, actual);
    }

    @Test
    void mapToRemittanceStructuredArray() {
        //Given
        RemittanceInformationStructuredTO remittanceInformationStructuredTO = jsonReader.getObjectFromFile("json/mappers/remittance.json", RemittanceInformationStructuredTO.class);
        //When
        List<SpiRemittance> actual = ledgersSpiPaymentMapper.mapToRemittanceStructuredArray(Collections.singletonList(remittanceInformationStructuredTO));
        //Then
        List<SpiRemittance> expected = getTestSpiRemittanceList();
        assertEquals(expected, actual);
    }

    private List<SpiRemittance> getTestSpiRemittanceList() {
        SpiRemittance remittance = new SpiRemittance();
        remittance.setReference("Ref Number Merchant");
        remittance.setReferenceType("reference type");
        remittance.setReferenceIssuer("reference issuer");
        return Collections.singletonList(remittance);
    }

    private SpiBulkPayment buildSpiBulkPayment() {
        SpiBulkPayment spiBulkPayment = new SpiBulkPayment();
        spiBulkPayment.setPayments(Arrays.asList(
                buildSpiSinglePayment("yc7AU-GdRIMjLAzKXjmDU4", BigDecimal.valueOf(1000.0)),
                buildSpiSinglePayment("yc7AU-GdRIMjLAzKXjmDU5", BigDecimal.valueOf(2000.0))
        ));

        spiBulkPayment.setPaymentId("yc7AU-GdRIMjLAzKXjmDU4");
        spiBulkPayment.setBatchBookingPreferred(true);
        SpiAccountReference debtorAccount = buildDebtorAccount();
        spiBulkPayment.setDebtorAccount(debtorAccount);
        spiBulkPayment.setRequestedExecutionDate(LocalDate.of(2020, 12, 12));
        spiBulkPayment.setRequestedExecutionTime(OffsetDateTime.of(2020, 12, 12, 12, 0, 0, 0, ZoneOffset.UTC));
        spiBulkPayment.setPaymentStatus(SpiTransactionStatus.ACSC);
        spiBulkPayment.setPaymentProduct("sepa-credit-transfers");

        return spiBulkPayment;
    }

    private SpiAccountReference buildDebtorAccount() {
        return jsonReader.getObjectFromFile("json/mappers/spi-account-reference.json", SpiAccountReference.class);
    }

    private SpiSinglePayment buildSpiSinglePayment(String paymentId, BigDecimal amount) {
        SpiSinglePayment spiSinglePayment = jsonReader.getObjectFromFile("json/mappers/spi-single-payment.json", SpiSinglePaymentTestable.class);
        spiSinglePayment.setPaymentId(paymentId);
        spiSinglePayment.setInstructedAmount(new SpiAmount(Currency.getInstance("EUR"), amount));
        return spiSinglePayment;
    }
}