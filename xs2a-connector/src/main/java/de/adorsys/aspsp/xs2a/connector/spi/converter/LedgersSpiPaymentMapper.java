package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.ledgers.middleware.api.domain.general.AddressTO;
import de.adorsys.ledgers.middleware.api.domain.payment.BulkPaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PeriodicPaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.SinglePaymentTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.psd2.xs2a.core.pis.FrequencyCode;
import de.adorsys.psd2.xs2a.core.pis.PisDayOfExecution;
import de.adorsys.psd2.xs2a.core.pis.PisExecutionRule;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
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
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.time.*;
import java.util.List;
import java.util.Optional;

@Mapper(componentModel = "spring", uses = {LedgersSpiAccountMapper.class, ChallengeDataMapper.class, AddressMapper.class})
public abstract class LedgersSpiPaymentMapper {

    private LedgersSpiAccountMapper accountMapper = Mappers.getMapper(LedgersSpiAccountMapper.class);

    @Mappings({
            @Mapping(target = "requestedExecutionTime", expression = "java(toTime(payment.getRequestedExecutionTime()))"),
            @Mapping(target = "paymentProduct", expression = "java(toPaymentProduct(payment.getPaymentProduct()))")
    })
    public abstract SinglePaymentTO toSinglePaymentTO(SpiSinglePayment payment);

    @Mappings({
            @Mapping(target = "executionRule", expression = "java(de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapperHelper.mapPisExecutionRule(payment.getExecutionRule()))"),
            @Mapping(target = "dayOfExecution", expression = "java(de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapperHelper.mapPisDayOfExecution(payment.getDayOfExecution()))")
    })
    public abstract PeriodicPaymentTO toPeriodicPaymentTO(SpiPeriodicPayment payment);

    public abstract BulkPaymentTO toBulkPaymentTO(SpiBulkPayment payment);

    @Mapping(source = "paymentStatus", target = "transactionStatus")
    public abstract SpiSinglePaymentInitiationResponse toSpiSingleResponse(SinglePaymentTO payment);

    @Mappings({
            @Mapping(target = "scaMethods", expression = "java(de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodUtils.toScaMethods(response.getScaMethods()))"),
            @Mapping(target = "chosenScaMethod", expression = "java(de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodUtils.toScaMethod(response.getChosenScaMethod()))")
    })
    public abstract SpiSinglePaymentInitiationResponse toSpiSingleResponse(SCAPaymentResponseTO response);

    @Mapping(source = "paymentStatus", target = "transactionStatus")
    public abstract SpiPeriodicPaymentInitiationResponse toSpiPeriodicResponse(PeriodicPaymentTO payment);

    @Mappings({
            @Mapping(target = "scaMethods", expression = "java(de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodUtils.toScaMethods(response.getScaMethods()))"),
            @Mapping(target = "chosenScaMethod", expression = "java(de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodUtils.toScaMethod(response.getChosenScaMethod()))")
    })
    public abstract SpiPeriodicPaymentInitiationResponse toSpiPeriodicResponse(SCAPaymentResponseTO response);

    @Mapping(source = "paymentStatus", target = "transactionStatus")
    public abstract SpiBulkPaymentInitiationResponse toSpiBulkResponse(BulkPaymentTO payment);

    @Mappings({
            @Mapping(target = "scaMethods", expression = "java(de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodUtils.toScaMethods(response.getScaMethods()))"),
            @Mapping(target = "chosenScaMethod", expression = "java(de.adorsys.aspsp.xs2a.connector.spi.converter.ScaMethodUtils.toScaMethod(response.getChosenScaMethod()))")
    })
    public abstract SpiBulkPaymentInitiationResponse toSpiBulkResponse(SCAPaymentResponseTO response);

    public SpiSinglePayment toSpiSinglePayment(SinglePaymentTO payment) {
        SpiSinglePayment spiPayment = new SpiSinglePayment(payment.getPaymentProduct().getValue());
        spiPayment.setPaymentId(payment.getPaymentId());
        spiPayment.setEndToEndIdentification(payment.getEndToEndIdentification());
        spiPayment.setDebtorAccount(accountMapper.toSpiAccountReference(payment.getDebtorAccount()));
        spiPayment.setInstructedAmount(accountMapper.toSpiAmount(payment.getInstructedAmount()));
        spiPayment.setCreditorAccount(accountMapper.toSpiAccountReference(payment.getCreditorAccount()));
        spiPayment.setCreditorAgent(payment.getCreditorAgent());
        spiPayment.setCreditorName(payment.getCreditorName());
        spiPayment.setCreditorAddress(toSpiAddress(payment.getCreditorAddress()));
        spiPayment.setRemittanceInformationUnstructured(payment.getRemittanceInformationUnstructured());
        spiPayment.setPaymentStatus(TransactionStatus.valueOf(payment.getPaymentStatus().name()));
        spiPayment.setRequestedExecutionDate(payment.getRequestedExecutionDate());
        spiPayment.setRequestedExecutionTime(Optional.ofNullable(payment.getRequestedExecutionDate())
                                                     .map(d -> toDateTime(d, payment.getRequestedExecutionTime()))
                                                     .orElse(null));
        return spiPayment;
    } //Direct mapping no need for testing

    public SpiPeriodicPayment mapToSpiPeriodicPayment(PeriodicPaymentTO payment) {
        SpiPeriodicPayment spiPayment = new SpiPeriodicPayment(payment.getPaymentProduct().getValue());
        spiPayment.setPaymentId(payment.getPaymentId());
        spiPayment.setEndToEndIdentification(payment.getEndToEndIdentification());
        spiPayment.setDebtorAccount(accountMapper.toSpiAccountReference(payment.getDebtorAccount()));
        spiPayment.setInstructedAmount(accountMapper.toSpiAmount(payment.getInstructedAmount()));
        spiPayment.setCreditorAccount(accountMapper.toSpiAccountReference(payment.getCreditorAccount()));
        spiPayment.setCreditorAgent(payment.getCreditorAgent());
        spiPayment.setCreditorName(payment.getCreditorName());
        spiPayment.setCreditorAddress(toSpiAddress(payment.getCreditorAddress()));
        spiPayment.setRemittanceInformationUnstructured(payment.getRemittanceInformationUnstructured());
        spiPayment.setPaymentStatus(TransactionStatus.valueOf(payment.getPaymentStatus().name()));
        spiPayment.setRequestedExecutionDate(payment.getRequestedExecutionDate());
        spiPayment.setRequestedExecutionTime(toDateTime(payment.getRequestedExecutionDate(), payment.getRequestedExecutionTime()));
        spiPayment.setStartDate(payment.getStartDate());
        spiPayment.setEndDate(payment.getEndDate());
        Optional<PisExecutionRule> pisExecutionRule = PisExecutionRule.getByValue(payment.getExecutionRule());
        pisExecutionRule.ifPresent(spiPayment::setExecutionRule);
        spiPayment.setFrequency(FrequencyCode.valueOf(payment.getFrequency().name()));
        spiPayment.setDayOfExecution(PisDayOfExecution.fromValue(String.valueOf(payment.getDayOfExecution())));
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
                           spiBulkPayment.setPaymentStatus(TransactionStatus.valueOf(p.getPaymentStatus().name()));
                           spiBulkPayment.setPayments(toSpiSinglePaymentsList(p.getPayments()));
                           spiBulkPayment.setPaymentProduct(p.getPaymentProduct().getValue());
                           return spiBulkPayment;
                       }).orElse(null);
    }

    public abstract List<SpiSinglePayment> toSpiSinglePaymentsList(List<SinglePaymentTO> payments);

    public LocalTime toTime(OffsetDateTime time) {
        return Optional.ofNullable(time)
                       .map(OffsetDateTime::toLocalTime)
                       .orElse(null);
    } //Direct mapping no need for testing

    public OffsetDateTime toDateTime(LocalDate date, LocalTime time) {
        return Optional.ofNullable(date)
                       .map(d -> LocalDateTime.of(d, Optional.ofNullable(time)
                                                             .orElse(LocalTime.ofSecondOfDay(0)))
                                         .atOffset(ZoneOffset.UTC))
                       .orElse(null);
    } //Direct mapping no need for testing

    private SpiAddress toSpiAddress(AddressTO address) {
        return Optional.ofNullable(address)
                       .map(a -> new SpiAddress(
                               a.getStreet(),
                               a.getBuildingNumber(),
                               a.getCity(),
                               a.getPostalCode(),
                               a.getCountry()))
                       .orElse(null);
    } //Direct mapping no need for testing

    public SpiPaymentCancellationResponse toSpiPaymentCancellationResponse(SCAPaymentResponseTO response) {
        return Optional.ofNullable(response)
                       .map(t -> {
                           SpiPaymentCancellationResponse cancellation = new SpiPaymentCancellationResponse();
                           cancellation.setCancellationAuthorisationMandated(needAuthorization(response));
                           cancellation.setTransactionStatus(TransactionStatus.valueOf(response.getTransactionStatus().name()));
                           return cancellation;
                       }).orElseGet(SpiPaymentCancellationResponse::new);
    }//Direct mapping no testing necessary

    PaymentProductTO toPaymentProduct(String paymentProduct) {
        if (paymentProduct == null) {
            return null;
        }
        return PaymentProductTO.getByValue(paymentProduct)
                       .orElse(null);
    }

    /*
     * How do we know if a payment or a cancellation needs authorization.
     *
     * At initiation the SCAStatus shall be set to {@link ScaStatusTO#EXEMPTED}
     */
    private boolean needAuthorization(SCAPaymentResponseTO response) {
        return !ScaStatusTO.EXEMPTED.equals(response.getScaStatus());
    }
}
