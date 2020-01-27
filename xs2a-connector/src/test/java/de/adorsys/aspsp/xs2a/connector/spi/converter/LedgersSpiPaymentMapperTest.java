package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.payment.BulkPaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PeriodicPaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.SinglePaymentTO;
import de.adorsys.psd2.xs2a.core.pis.FrequencyCode;
import de.adorsys.psd2.xs2a.core.pis.PisDayOfExecution;
import de.adorsys.psd2.xs2a.core.pis.PisExecutionRule;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiAmount;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiAddress;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiBulkPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiBulkPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPeriodicPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
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
    void toSinglePaymentTOWithRealData() {
        SpiSinglePayment inputData = getSpiSingle();
        SinglePaymentTO actualResult = ledgersSpiPaymentMapper.toSinglePaymentTO(inputData);
        SinglePaymentTO expectedResult = jsonReader.getObjectFromFile("json/mappers/single-payment-to.json", SinglePaymentTO.class);
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void toSinglePaymentTOWithNull() {
        SinglePaymentTO actualResult = ledgersSpiPaymentMapper.toSinglePaymentTO(null);
        assertNull(actualResult);
    }

    @Test
    void toPeriodicPaymentTOWithRealData() {
        SpiPeriodicPayment inputData = getPeriodic();
        PeriodicPaymentTO actualResult = ledgersSpiPaymentMapper.toPeriodicPaymentTO(inputData);
        PeriodicPaymentTO expectedResult = jsonReader.getObjectFromFile("json/mappers/periodic-payment-to.json", PeriodicPaymentTO.class);
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void toPeriodicPaymentTOWithNull() {
        PeriodicPaymentTO actualResult = ledgersSpiPaymentMapper.toPeriodicPaymentTO(null);
        assertNull(actualResult);
    }

    @Test
    void toBulkPaymentTOWithRealData() {
        SpiBulkPayment inputData = getBulk();
        BulkPaymentTO actualResult = ledgersSpiPaymentMapper.toBulkPaymentTO(inputData);
        BulkPaymentTO expectedResult = jsonReader.getObjectFromFile("json/mappers/bulk-payment-to.json", BulkPaymentTO.class);
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void toBulkPaymentTOWithNull() {
        BulkPaymentTO actualResult = ledgersSpiPaymentMapper.toBulkPaymentTO(null);
        assertNull(actualResult);
    }

    @Test
    void toSpiSingleResponseWithRealData() {
        SinglePaymentTO inputData = jsonReader.getObjectFromFile("json/mappers/single-payment-to.json", SinglePaymentTO.class);
        SpiSinglePaymentInitiationResponse actualResult = ledgersSpiPaymentMapper.toSpiSingleResponse(inputData);
        SpiSinglePaymentInitiationResponse expectedResult = jsonReader
                                                                    .getObjectFromFile("json/mappers/spi-payment-initiation-response.json", SpiSinglePaymentInitiationResponse.class);
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void toSpiSingleResponseWithNull() {
        SpiSinglePaymentInitiationResponse actualResult = ledgersSpiPaymentMapper.toSpiSingleResponse((SinglePaymentTO) null);
        assertNull(actualResult);
    }

    @Test
    void toSpiPeriodicResponseWithRealData() {
        PeriodicPaymentTO inputData = jsonReader.getObjectFromFile("json/mappers/periodic-payment-to.json", PeriodicPaymentTO.class);
        SpiPeriodicPaymentInitiationResponse actualResult = ledgersSpiPaymentMapper.toSpiPeriodicResponse(inputData);
        SpiPeriodicPaymentInitiationResponse expectedResult = jsonReader
                                                                      .getObjectFromFile("json/mappers/spi-payment-initiation-response.json", SpiPeriodicPaymentInitiationResponse.class);
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void toSpiPeriodicResponseWithNull() {
        SpiPeriodicPaymentInitiationResponse actualResult = ledgersSpiPaymentMapper.toSpiPeriodicResponse((PeriodicPaymentTO) null);
        assertNull(actualResult);
    }

    @Test
    void toSpiBulkResponseWithRealData() {
        BulkPaymentTO inputData = jsonReader.getObjectFromFile("json/mappers/bulk-payment-to.json", BulkPaymentTO.class);
        SpiBulkPaymentInitiationResponse actualResult = ledgersSpiPaymentMapper.toSpiBulkResponse(inputData);
        SpiBulkPaymentInitiationResponse expectedResult = getBulkResponse();
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void toSpiBulkResponseWithNull() {
        SpiBulkPaymentInitiationResponse actualResult = ledgersSpiPaymentMapper.toSpiBulkResponse((BulkPaymentTO) null);
        assertNull(actualResult);
    }

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

    private SpiSinglePayment getSpiSingle() {
        SpiSinglePayment spiPayment = new SpiSinglePayment(PAYMENT_PRODUCT_SEPA.getValue());
        spiPayment.setPaymentId("myPaymentId");
        spiPayment.setEndToEndIdentification("123456789");
        spiPayment.setDebtorAccount(getSpiAccountReference());
        spiPayment.setInstructedAmount(new SpiAmount(Currency.getInstance("EUR"), BigDecimal.valueOf(100)));
        spiPayment.setCreditorAccount(getSpiAccountReference());
        spiPayment.setCreditorAgent("agent");
        spiPayment.setCreditorName("Rozetka.ua");
        spiPayment.setCreditorAddress(new SpiAddress("SomeStreet", "666", "Kiev", "04210", "Ukraine"));
        spiPayment.setRemittanceInformationUnstructured("remittance");
        spiPayment.setPaymentStatus(TransactionStatus.RCVD);
        spiPayment.setRequestedExecutionDate(LocalDate.of(2018, 12, 12));
        spiPayment.setRequestedExecutionTime(OffsetDateTime.of(LocalDate.of(2018, 12, 12), LocalTime.of(12, 0), ZoneOffset.UTC));
        return spiPayment;
    }

    private SpiPeriodicPayment getPeriodic() {
        SpiPeriodicPayment spiPayment = new SpiPeriodicPayment(PAYMENT_PRODUCT_SEPA.getValue());
        spiPayment.setPaymentId("myPaymentId");
        spiPayment.setEndToEndIdentification("123456789");
        spiPayment.setDebtorAccount(getSpiAccountReference());
        spiPayment.setInstructedAmount(new SpiAmount(Currency.getInstance("EUR"), BigDecimal.valueOf(100)));
        spiPayment.setCreditorAccount(getSpiAccountReference());
        spiPayment.setCreditorAgent("agent");
        spiPayment.setCreditorName("Rozetka.ua");
        spiPayment.setCreditorAddress(new SpiAddress("SomeStreet", "666", "Kiev", "04210", "Ukraine"));
        spiPayment.setRemittanceInformationUnstructured("remittance");
        spiPayment.setPaymentStatus(TransactionStatus.RCVD);
        spiPayment.setRequestedExecutionDate(LocalDate.of(2018, 12, 12));
        spiPayment.setRequestedExecutionTime(OffsetDateTime.of(LocalDate.of(2018, 12, 12), LocalTime.of(12, 0), ZoneOffset.UTC));

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
        one.setCreditorAccount(getSpiAccountReference());
        one.setInstructedAmount(new SpiAmount(Currency.getInstance("EUR"), BigDecimal.valueOf(200)));
        one.setCreditorAccount(getSpiAccountReference());

        SpiSinglePayment two = getSpiSingle();
        two.setPaymentId("myPaymentId2");
        two.setCreditorAccount(getSpiAccountReference());
        two.setCreditorName("Sokol.ua");
        two.setPaymentProduct(PAYMENT_PRODUCT_CROSS_BORDER.getValue());

        payment.setPayments(Arrays.asList(one, two));
        payment.setPaymentProduct(PAYMENT_PRODUCT_SEPA.getValue());
        return payment;
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