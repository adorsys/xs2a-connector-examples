package de.adorsys.aspsp.xs2a.spi.converter;

import de.adorsys.ledgers.domain.general.AddressTO;
import de.adorsys.ledgers.domain.payment.BulkPaymentTO;
import de.adorsys.ledgers.domain.payment.PaymentCancellationResponseTO;
import de.adorsys.ledgers.domain.payment.PeriodicPaymentTO;
import de.adorsys.ledgers.domain.payment.SinglePaymentTO;
import de.adorsys.psd2.xs2a.core.profile.PaymentProduct;
import de.adorsys.psd2.xs2a.spi.domain.code.SpiFrequencyCode;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiTransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiAddress;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiBulkPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiBulkPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentCancellationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPeriodicPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.*;
import java.util.List;
import java.util.Optional;

@Mapper(componentModel = "spring", uses = LedgersSpiAccountMapper.class)
public abstract class LedgersSpiPaymentMapper {

    private LedgersSpiAccountMapper accountMapper = Mappers.getMapper(LedgersSpiAccountMapper.class);

    @Mapping(target = "requestedExecutionTime", expression = "java(toTime(payment.getRequestedExecutionTime()))")
    public abstract SinglePaymentTO toSinglePaymentTO(SpiSinglePayment payment);

    public abstract PeriodicPaymentTO toPeriodicPaymentTO(SpiPeriodicPayment payment);

    public abstract BulkPaymentTO toBulkPaymentTO(SpiBulkPayment payment);

    @Mapping(source = "paymentStatus", target = "transactionStatus")
    public abstract SpiSinglePaymentInitiationResponse toSpiSingleResponse(SinglePaymentTO payment);

    @Mapping(source = "paymentStatus", target = "transactionStatus")
    public abstract SpiPeriodicPaymentInitiationResponse toSpiPeriodicResponse(PeriodicPaymentTO payment);

    @Mapping(source = "paymentStatus", target = "transactionStatus")
    public abstract SpiBulkPaymentInitiationResponse toSpiBulkResponse(BulkPaymentTO payment);

    public SpiSinglePayment toSpiSinglePayment(SinglePaymentTO payment) {
        SpiSinglePayment spiPayment = new SpiSinglePayment(PaymentProduct.valueOf(payment.getPaymentProduct().name()));
        spiPayment.setPaymentId(payment.getPaymentId());
        spiPayment.setEndToEndIdentification(payment.getEndToEndIdentification());
        spiPayment.setDebtorAccount(accountMapper.toSpiAccountReference(payment.getDebtorAccount()));
        spiPayment.setInstructedAmount(accountMapper.toSpiAmount(payment.getInstructedAmount()));
        spiPayment.setCreditorAccount(accountMapper.toSpiAccountReference(payment.getCreditorAccount()));
        spiPayment.setCreditorAgent(payment.getCreditorAgent());
        spiPayment.setCreditorName(payment.getCreditorName());
        spiPayment.setCreditorAddress(toSpiAddress(payment.getCreditorAddress()));
        spiPayment.setRemittanceInformationUnstructured(payment.getRemittanceInformationUnstructured());
        spiPayment.setPaymentStatus(SpiTransactionStatus.valueOf(payment.getPaymentStatus().name()));
        spiPayment.setRequestedExecutionDate(payment.getRequestedExecutionDate());
        spiPayment.setRequestedExecutionTime(toDateTime(payment.getRequestedExecutionDate(), payment.getRequestedExecutionTime()));
        return spiPayment;
    } //Direct mapping no need for testing

    public SpiPeriodicPayment mapToSpiPeriodicPayment(PeriodicPaymentTO payment) {
        SpiPeriodicPayment spiPayment = new SpiPeriodicPayment(PaymentProduct.valueOf(payment.getPaymentProduct().name()));
        spiPayment.setPaymentId(payment.getPaymentId());
        spiPayment.setEndToEndIdentification(payment.getEndToEndIdentification());
        spiPayment.setDebtorAccount(accountMapper.toSpiAccountReference(payment.getDebtorAccount()));
        spiPayment.setInstructedAmount(accountMapper.toSpiAmount(payment.getInstructedAmount()));
        spiPayment.setCreditorAccount(accountMapper.toSpiAccountReference(payment.getCreditorAccount()));
        spiPayment.setCreditorAgent(payment.getCreditorAgent());
        spiPayment.setCreditorName(payment.getCreditorName());
        spiPayment.setCreditorAddress(toSpiAddress(payment.getCreditorAddress()));
        spiPayment.setRemittanceInformationUnstructured(payment.getRemittanceInformationUnstructured());
        spiPayment.setPaymentStatus(SpiTransactionStatus.valueOf(payment.getPaymentStatus().name()));
        spiPayment.setRequestedExecutionDate(payment.getRequestedExecutionDate());
        spiPayment.setRequestedExecutionTime(toDateTime(payment.getRequestedExecutionDate(), payment.getRequestedExecutionTime()));
        spiPayment.setStartDate(payment.getStartDate());
        spiPayment.setEndDate(payment.getEndDate());
        spiPayment.setExecutionRule(payment.getExecutionRule());
        spiPayment.setFrequency(SpiFrequencyCode.valueOf(payment.getFrequency().name()));
        spiPayment.setDayOfExecution(payment.getDayOfExecution());
        return spiPayment;
    } //Direct mapping no need for testing

    public SpiBulkPayment mapToSpiBulkPayment(BulkPaymentTO payment) {
        return Optional.ofNullable(payment)
                       .map(p -> {
                           SpiBulkPayment spiBulkPayment = new SpiBulkPayment();
                           spiBulkPayment.setPaymentId(p.getPaymentId());
                           spiBulkPayment.setBatchBookingPreferred(p.getBatchBookingPreferred());
                           spiBulkPayment.setDebtorAccount(accountMapper.toSpiAccountReference(p.getDebtorAccount()));
                           spiBulkPayment.setRequestedExecutionDate(p.getRequestedExecutionDate());
                           spiBulkPayment.setPaymentStatus(SpiTransactionStatus.valueOf(p.getPaymentStatus().name()));
                           spiBulkPayment.setPayments(toSpiSinglePaymentsList(p.getPayments()));
                           spiBulkPayment.setPaymentProduct(PaymentProduct.valueOf(p.getPaymentProduct().name()));
                           return spiBulkPayment;
                       }).orElse(null);
    }

    public abstract List<SpiSinglePayment> toSpiSinglePaymentsList(List<SinglePaymentTO> payments);

    public LocalTime toTime(OffsetDateTime time) {
        return time.toLocalTime();
    } //Direct mapping no need for testing

    public OffsetDateTime toDateTime(LocalDate date, LocalTime time) {
        return LocalDateTime.of(date, time).atOffset(ZoneOffset.UTC);
    } //Direct mapping no need for testing

    public SpiAddress toSpiAddress(AddressTO address) {
        return new SpiAddress(
                address.getStreet(),
                address.getBuildingNumber(),
                address.getCity(),
                address.getPostalCode(),
                address.getCountry());
    } //Direct mapping no need for testing

    public SpiPaymentCancellationResponse toSpiPaymentCancellationResponse(PaymentCancellationResponseTO response) {
        return Optional.ofNullable(response)
                       .map(t -> {
                           SpiPaymentCancellationResponse cancellation = new SpiPaymentCancellationResponse();
                           cancellation.setCancellationAuthorisationMandated(response.isCancellationAuthorisationMandated());
                           cancellation.setTransactionStatus(SpiTransactionStatus.valueOf(response.getTransactionStatus().name()));
                           return cancellation;
                       }).orElseGet(SpiPaymentCancellationResponse::new);
    }//Direct mapping no testing necessary
}
