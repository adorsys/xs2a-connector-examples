package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.payment.BulkPaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTO;
import de.adorsys.psd2.xs2a.core.pis.*;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiAmount;
import de.adorsys.psd2.xs2a.spi.domain.payment.*;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiBulkPaymentInitiationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {LedgersSpiPaymentMapperImpl.class, LedgersSpiAccountMapperImpl.class,
        ChallengeDataMapperImpl.class, AddressMapperImpl.class})
class LedgersSpiPaymentMapperTest {

    @Autowired
    private LedgersSpiPaymentMapper ledgersSpiPaymentMapper;
    private JsonReader jsonReader = new JsonReader();

    private static final PaymentProductTO PAYMENT_PRODUCT_SEPA = PaymentProductTO.SEPA;
    private static final PaymentProductTO PAYMENT_PRODUCT_CROSS_BORDER = PaymentProductTO.CROSS_BORDER;

    @Test
    void mapToSpiBulkPaymentWithRealData() {
        BulkPaymentTO inputData = jsonReader.getObjectFromFile("json/mappers/bulk-payment-to.json", BulkPaymentTO.class);
        SpiBulkPayment actualResult = ledgersSpiPaymentMapper.mapToSpiBulkPayment(inputData);
        SpiBulkPayment expectedResult = getBulk();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void mapToSpiBulkPaymentWithNull() {
        SpiBulkPayment actualResult = ledgersSpiPaymentMapper.mapToSpiBulkPayment(null);
        assertNull(actualResult);
    }

    @Test
    void toPaymentTO_Single() {
        // Given
        PaymentTO expectedResult = jsonReader.getObjectFromFile("json/mappers/single-payment-Initiation.json", PaymentTO.class);
        SpiSinglePayment spiSinglePayment = updateSpiPayment(getSpiSingle());

        // When
        PaymentTO actualResult = ledgersSpiPaymentMapper.mapToPaymentTO(spiSinglePayment);

        // Then
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void toPaymentTO_Bulk() {
        // Given
        PaymentTO expectedResult = jsonReader.getObjectFromFile("json/mappers/bulk-payment-Initiation.json", PaymentTO.class);
        SpiBulkPayment spiBulkPayment = getBulk();
        spiBulkPayment.getPayments().forEach(this::updateSpiPayment);

        // When
        PaymentTO actualResult = ledgersSpiPaymentMapper.mapToPaymentTO(spiBulkPayment);

        // Then
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void toPaymentTO_Periodic() {
        // Given
        PaymentTO expectedResult = jsonReader.getObjectFromFile("json/mappers/periodic-payment-Initiation.json", PaymentTO.class);
        SpiPeriodicPayment spiPeriodicPayment = updateSpiPayment(getPeriodic());

        // When
        PaymentTO actualResult = ledgersSpiPaymentMapper.mapToPaymentTO(spiPeriodicPayment);

        // Then
        assertEquals(expectedResult, actualResult);
    }

    private SpiSinglePayment getSpiSingle() {
        SpiSinglePayment spiPayment = new SpiSinglePayment(PAYMENT_PRODUCT_SEPA.getValue());
        return fillSpiPaymentWithInitialData(spiPayment);
    }

    private SpiPeriodicPayment getPeriodic() {
        SpiPeriodicPayment spiPayment = fillSpiPaymentWithInitialData(new SpiPeriodicPayment(PAYMENT_PRODUCT_SEPA.getValue()));
        spiPayment.setStartDate(LocalDate.of(2018, 12, 12));
        spiPayment.setEndDate(LocalDate.of(2018, 12, 28));
        spiPayment.setExecutionRule(PisExecutionRule.FOLLOWING);
        spiPayment.setFrequency(FrequencyCode.DAILY);
        spiPayment.setDayOfExecution(PisDayOfExecution.getByValue("1").get());
        return spiPayment;
    }

    private SpiBulkPayment getBulk() {
        SpiBulkPayment payment = new SpiBulkPayment();
        payment.setPaymentId("myPaymentId");
        payment.setBatchBookingPreferred(false);
        payment.setDebtorAccount(getSpiAccountReference());
        payment.setRequestedExecutionDate(LocalDate.of(2018, 12, 12));
        payment.setPaymentStatus(TransactionStatus.RCVD);
        SpiSinglePayment one = getSpiSingle();
        one.setPaymentId("myPaymentId1");
        one.setEndToEndIdentification("123456788");
        one.setInstructedAmount(new SpiAmount(Currency.getInstance("EUR"), BigDecimal.valueOf(200)));

        SpiSinglePayment two = getSpiSingle();
        two.setPaymentId("myPaymentId2");
        two.setCreditorName("Sokol.ua");
        two.setPaymentProduct(PAYMENT_PRODUCT_CROSS_BORDER.getValue());

        payment.setPayments(Arrays.asList(one, two));
        payment.setPaymentProduct(PAYMENT_PRODUCT_SEPA.getValue());
        return payment;
    }

    private <T extends SpiSinglePayment> T updateSpiPayment(T payment) {
        SpiRemittance spiRemittance = buildSpiRemittance();
        payment.setRemittanceInformationStructured(spiRemittance);
        payment.setPurposeCode(PurposeCode.BKDF);
        return payment;
    }

    private <T extends SpiSinglePayment> T fillSpiPaymentWithInitialData(T payment) {
        payment.setPaymentId("myPaymentId");
        payment.setEndToEndIdentification("123456789");
        payment.setDebtorAccount(getSpiAccountReference());
        payment.setInstructedAmount(new SpiAmount(Currency.getInstance("EUR"), BigDecimal.valueOf(100)));
        payment.setCreditorAccount(getSpiAccountReference());
        payment.setCreditorAgent("agent");
        payment.setCreditorName("Rozetka.ua");
        payment.setCreditorAddress(new SpiAddress("SomeStreet", "666", "Kiev", "04210", "Ukraine"));
        payment.setRemittanceInformationUnstructured("remittance");
        payment.setPaymentStatus(TransactionStatus.RCVD);
        payment.setRequestedExecutionDate(LocalDate.of(2018, 12, 12));
        payment.setRequestedExecutionTime(OffsetDateTime.of(LocalDate.of(2018, 12, 12), LocalTime.of(12, 0), ZoneOffset.UTC));
        return payment;
    }

    private SpiRemittance buildSpiRemittance() {
        SpiRemittance spiRemittance = new SpiRemittance();
        spiRemittance.setReference("Ref Number Merchant");
        spiRemittance.setReferenceIssuer("reference issuer");
        spiRemittance.setReferenceType("reference type");
        return spiRemittance;
    }

    private SpiAccountReference getSpiAccountReference() {
        return jsonReader.getObjectFromFile("json/mappers/spi-account-reference.json", SpiAccountReference.class);
    }

    private SpiBulkPaymentInitiationResponse getBulkResponse() {
        SpiBulkPaymentInitiationResponse resp = new SpiBulkPaymentInitiationResponse();
        resp.setPaymentId("myPaymentId");
        resp.setTransactionStatus(TransactionStatus.RCVD);
        resp.setPayments(getBulk().getPayments());
        return resp;
    }
}