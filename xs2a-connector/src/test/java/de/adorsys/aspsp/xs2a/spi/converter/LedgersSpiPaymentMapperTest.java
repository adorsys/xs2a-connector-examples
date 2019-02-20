package de.adorsys.aspsp.xs2a.spi.converter;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.ledgers.middleware.api.domain.payment.BulkPaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PeriodicPaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.SinglePaymentTO;
import de.adorsys.psd2.xs2a.core.pis.PisDayOfExecution;
import de.adorsys.psd2.xs2a.core.pis.PisExecutionRule;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.code.SpiFrequencyCode;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiAmount;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiAddress;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiBulkPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiBulkPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPeriodicPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import org.junit.Test;
import org.mapstruct.factory.Mappers;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

public class LedgersSpiPaymentMapperTest {
    private YamlMapper yamlMapper = new YamlMapper(ScaMethodConverterTest.class);

    public static final PaymentProductTO PAYMENT_PRODUCT_SEPA = PaymentProductTO.SEPA;

    public static final PaymentProductTO PAYMENT_PRODUCT_CROSS_BORDER = PaymentProductTO.CROSS_BORDER;

    private final LedgersSpiPaymentMapper mapper = Mappers.getMapper(LedgersSpiPaymentMapper.class);


    @Test
    public void toSinglePaymentTO() throws IOException {
        //Given
        SpiSinglePayment spiPayment = getSpiSingle();
        SinglePaymentTO expected = yamlMapper.readYml(SinglePaymentTO.class, "PaymentSingleTO.yml");

        //When
        SinglePaymentTO payment = mapper.toSinglePaymentTO(spiPayment);
        assertThat(payment).isNotNull();
        assertThat(payment).isEqualToComparingFieldByFieldRecursively(expected);
    }

    @Test
    public void toPeriodicPaymentTO() throws IOException {
        //Given
        SpiPeriodicPayment spiPayment = getPeriodic();
        PeriodicPaymentTO expected = yamlMapper.readYml(PeriodicPaymentTO.class, "PaymentPeriodicTO.yml");

        //When
        PeriodicPaymentTO payment = mapper.toPeriodicPaymentTO(spiPayment);
        assertThat(payment).isNotNull();
        assertThat(payment).isEqualToComparingFieldByFieldRecursively(expected);
    }

    @Test
    public void toBulkPaymentTO() throws IOException {
        //Given
        SpiBulkPayment spiPayment = getBulk();
        BulkPaymentTO expected = yamlMapper.readYml(BulkPaymentTO.class, "PaymentBulkTO.yml");

        //When
        BulkPaymentTO payment = mapper.toBulkPaymentTO(spiPayment);
        assertThat(payment).isNotNull();
        assertThat(payment).isEqualToComparingFieldByFieldRecursively(expected);
    }

    @Test
    public void toSpiSingleResponse() throws IOException {
        //Given
        SinglePaymentTO initial = yamlMapper.readYml(SinglePaymentTO.class, "PaymentSingleTO.yml");
        SpiSinglePaymentInitiationResponse expected = yamlMapper.readYml(SpiSinglePaymentInitiationResponse.class, "SingleSpiResponse.yml");

        //When
        SpiSinglePaymentInitiationResponse response = mapper.toSpiSingleResponse(initial);
        assertThat(response).isNotNull();
        assertThat(response).isEqualToComparingFieldByFieldRecursively(expected);
    }

    @Test
    public void toSpiPeriodicResponse() throws IOException {
        //Given
        PeriodicPaymentTO initial = yamlMapper.readYml(PeriodicPaymentTO.class, "PaymentPeriodicTO.yml");
        SpiPeriodicPaymentInitiationResponse expected = yamlMapper.readYml(SpiPeriodicPaymentInitiationResponse.class, "SingleSpiResponse.yml");

        //When
        SpiPeriodicPaymentInitiationResponse response = mapper.toSpiPeriodicResponse(initial);
        assertThat(response).isNotNull();
        assertThat(response).isEqualToComparingFieldByFieldRecursively(expected);
    }

    @Test
    public void toSpiBulkResponse() throws IOException {
        //Given
        BulkPaymentTO initial = yamlMapper.readYml(BulkPaymentTO.class, "PaymentBulkTO.yml");
        SpiBulkPaymentInitiationResponse expected = getBulkResponse();

        //When
        SpiBulkPaymentInitiationResponse response = mapper.toSpiBulkResponse(initial);
        assertThat(response).isNotNull();
        assertThat(response).isEqualToComparingFieldByFieldRecursively(expected);
    }

    @Test
    public void mapToSpiBulkPayment() throws IOException {
        //Given
        BulkPaymentTO initial = yamlMapper.readYml(BulkPaymentTO.class, "PaymentBulkTO.yml");
        SpiBulkPayment expected = getBulk();

        //When
        SpiBulkPayment response = mapper.mapToSpiBulkPayment(initial);
        assertThat(response).isNotNull();
        assertThat(response).isEqualToComparingFieldByFieldRecursively(expected);
    }

    private SpiSinglePayment getSpiSingle() {
        SpiSinglePayment spiPayment = new SpiSinglePayment(PAYMENT_PRODUCT_SEPA.getValue());
        spiPayment.setPaymentId("myPaymentId");
        spiPayment.setEndToEndIdentification("123456789");
        spiPayment.setDebtorAccount(getDebtorAcc());
        spiPayment.setInstructedAmount(new SpiAmount(Currency.getInstance("EUR"), BigDecimal.valueOf(100)));
        spiPayment.setCreditorAccount(getDebtorAcc());
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
        spiPayment.setDebtorAccount(getDebtorAcc());
        spiPayment.setInstructedAmount(new SpiAmount(Currency.getInstance("EUR"), BigDecimal.valueOf(100)));
        spiPayment.setCreditorAccount(getDebtorAcc());
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
        spiPayment.setFrequency(SpiFrequencyCode.DAILY);
        spiPayment.setDayOfExecution(PisDayOfExecution.getByValue("1").get());
        return spiPayment;
    }

    private SpiBulkPayment getBulk() {
        SpiBulkPayment payment = new SpiBulkPayment();
        payment.setPaymentId("myPaymentId");
        payment.setBatchBookingPreferred(false);
        payment.setDebtorAccount(getDebtorAcc());
        payment.setRequestedExecutionDate(LocalDate.of(2018, 12, 12));
        payment.setPaymentStatus(TransactionStatus.RCVD);
        SpiSinglePayment one = getSpiSingle();
        one.setPaymentId("myPaymentId1");
        one.setEndToEndIdentification("123456788");
        one.setCreditorAccount(getCreditorAcc());
        one.setInstructedAmount(new SpiAmount(Currency.getInstance("EUR"), BigDecimal.valueOf(200)));
        one.setCreditorAccount(getCreditorAcc());

        SpiSinglePayment two = getSpiSingle();
        two.setPaymentId("myPaymentId2");
        two.setCreditorAccount(getCreditorAcc2());
        two.setCreditorName("Sokol.ua");
        two.setPaymentProduct(PAYMENT_PRODUCT_CROSS_BORDER.getValue());

        payment.setPayments(Arrays.asList(one, two));
        payment.setPaymentProduct(PAYMENT_PRODUCT_SEPA.getValue());
        return payment;
    }

    private SpiAccountReference getDebtorAcc() {
        return new SpiAccountReference("DE91100000000123456789", "DE91100000000123456789", "DE91100000000123456789", "bban", "pan", "maskedPan", "msisdn", Currency.getInstance("EUR"));
    }

    private SpiAccountReference getCreditorAcc() {
        return new SpiAccountReference("DE91100000000123456787", "DE91100000000123456787", "DE91100000000123456787", "bban", "pan", "maskedPan", "msisdn", Currency.getInstance("EUR"));
    }

    private SpiAccountReference getCreditorAcc2() {
        return new SpiAccountReference("DE91100000000123456788", "DE91100000000123456788", "DE91100000000123456788", "bban", "pan", "maskedPan", "msisdn", Currency.getInstance("EUR"));
    }

    private SpiBulkPaymentInitiationResponse getBulkResponse() {
        SpiBulkPaymentInitiationResponse resp = new SpiBulkPaymentInitiationResponse();
        resp.setPaymentId("myPaymentId");
        resp.setTransactionStatus(TransactionStatus.RCVD);
        resp.setPayments(getBulk().getPayments());
        return resp;
    }
}