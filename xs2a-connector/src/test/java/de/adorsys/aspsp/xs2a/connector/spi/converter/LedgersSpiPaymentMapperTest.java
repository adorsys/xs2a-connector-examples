package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.RemittanceInformationStructuredTO;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiAmount;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiBulkPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
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
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {LedgersSpiPaymentMapperImpl.class, LedgersSpiAccountMapperImpl.class})
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
    void mapToSpiRemittance() {
        //Given
        RemittanceInformationStructuredTO remittanceInformationStructuredTO = jsonReader.getObjectFromFile("json/mappers/remittance.json", RemittanceInformationStructuredTO.class);
        //When
        String actual = ledgersSpiPaymentMapper.mapToRemittanceString(remittanceInformationStructuredTO);
        //Then
        String expected = "Ref Number Merchant";
        assertEquals(expected, actual);
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
        spiBulkPayment.setPaymentStatus(TransactionStatus.ACSC);
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