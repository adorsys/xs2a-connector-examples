package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.ledgers.middleware.api.domain.account.AccountReferenceTO;
import de.adorsys.ledgers.middleware.api.domain.general.AddressTO;
import de.adorsys.ledgers.middleware.api.domain.payment.*;
import de.adorsys.psd2.xs2a.core.pis.*;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiAmount;
import de.adorsys.psd2.xs2a.spi.domain.payment.*;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.*;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {LedgersSpiAccountMapper.class, ChallengeDataMapper.class, AddressMapper.class},
        imports = {LedgersSpiPaymentMapperHelper.class, ScaMethodUtils.class})
public abstract class LedgersSpiPaymentMapper {

    private LedgersSpiAccountMapper accountMapper = Mappers.getMapper(LedgersSpiAccountMapper.class);

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

    public PaymentTO mapToPaymentTO(SpiSinglePayment spiSinglePayment) {
        return Optional.ofNullable(spiSinglePayment)
                       .map(payment -> {

                           PaymentTO paymentTO = new PaymentTO();
                           paymentTO.setPaymentId(payment.getPaymentId());
                           paymentTO.setPaymentType(PaymentTypeTO.valueOf(payment.getPaymentType().name()));
                           paymentTO.setPaymentProduct(payment.getPaymentProduct());
                           paymentTO.setDebtorAccount(mapToAccountReferenceTO(payment.getDebtorAccount()));
                           paymentTO.setDebtorName(payment.getUltimateDebtor());
                           paymentTO.setTargets(Collections.singletonList(mapToPaymentTargetTO(payment)));
                           return paymentTO;
                       })
                       .orElse(null);
    }

    public PaymentTO mapToPaymentTO(SpiPeriodicPayment spiPeriodicPayment) {
        return Optional.ofNullable(spiPeriodicPayment)
                       .map(payment -> {

                           PaymentTO paymentTO = new PaymentTO();
                           paymentTO.setPaymentId(payment.getPaymentId());
                           paymentTO.setPaymentType(PaymentTypeTO.valueOf(payment.getPaymentType().name()));
                           paymentTO.setPaymentProduct(payment.getPaymentProduct());
                           paymentTO.setDebtorAccount(mapToAccountReferenceTO(payment.getDebtorAccount()));

                           paymentTO.setStartDate(payment.getStartDate());
                           paymentTO.setEndDate(payment.getEndDate());
                           paymentTO.setExecutionRule(Optional.ofNullable(payment.getExecutionRule())
                                                              .map(PisExecutionRule::toString)
                                                              .orElse(null));
                           paymentTO.setFrequency(mapToFrequencyCodeTO(payment.getFrequency()));
                           paymentTO.setDayOfExecution(Integer.valueOf(payment.getDayOfExecution().toString()));
                           paymentTO.setDebtorName(payment.getUltimateDebtor());
                           paymentTO.setTargets(Collections.singletonList(mapToPaymentTargetTO(payment)));

                           return paymentTO;
                       })
                       .orElse(null);
    }

    public PaymentTO mapToPaymentTO(SpiBulkPayment spiBulkPayment) {
        return Optional.ofNullable(spiBulkPayment)
                       .map(payment -> {

                           PaymentTO paymentTO = new PaymentTO();
                           paymentTO.setPaymentId(payment.getPaymentId());
                           paymentTO.setPaymentType(PaymentTypeTO.valueOf(payment.getPaymentType().name()));
                           paymentTO.setPaymentProduct(payment.getPaymentProduct());
                           paymentTO.setDebtorAccount(mapToAccountReferenceTO(payment.getDebtorAccount()));
                           paymentTO.setBatchBookingPreferred(payment.getBatchBookingPreferred());
                           paymentTO.setRequestedExecutionDate(payment.getRequestedExecutionDate());
                           paymentTO.setRequestedExecutionTime(Optional.ofNullable(payment.getRequestedExecutionTime()).map(OffsetDateTime::toLocalTime).orElse(null));
                           paymentTO.setTargets(payment.getPayments().stream()
                                                        .map(this::mapToPaymentTargetTO)
                                                        .collect(Collectors.toList()));
                           return paymentTO;

                       })
                       .orElse(null);
    }

    private PaymentTargetTO mapToPaymentTargetTO(SpiSinglePayment spiSinglePayment) {
        if (spiSinglePayment == null) {
            return null;
        }

        PaymentTargetTO paymentTargetTO = new PaymentTargetTO();

        paymentTargetTO.setPaymentId(spiSinglePayment.getPaymentId());
        paymentTargetTO.setEndToEndIdentification(spiSinglePayment.getEndToEndIdentification());
        paymentTargetTO.setInstructedAmount(mapToAmountTO(spiSinglePayment.getInstructedAmount()));
        paymentTargetTO.setCreditorAccount(mapToAccountReferenceTO(spiSinglePayment.getCreditorAccount()));
        paymentTargetTO.setCreditorAgent(spiSinglePayment.getCreditorAgent());
        paymentTargetTO.setCreditorName(spiSinglePayment.getCreditorName());
        paymentTargetTO.setCreditorAddress(mapToAddressTO(spiSinglePayment.getCreditorAddress()));
        paymentTargetTO.setPurposeCode(mapToPurposeCodeTO(spiSinglePayment.getPurposeCode()));
        paymentTargetTO.setRemittanceInformationUnstructured(spiSinglePayment.getRemittanceInformationUnstructured());
        paymentTargetTO.setRemittanceInformationStructured(mapToRemittanceInformationStructuredTO(spiSinglePayment.getRemittanceInformationStructured()));

        return paymentTargetTO;
    }

    private RemittanceInformationStructuredTO mapToRemittanceInformationStructuredTO(SpiRemittance spiRemittance) {
        if (spiRemittance == null) {
            return null;
        }

        RemittanceInformationStructuredTO remittanceInformationStructuredTO = new RemittanceInformationStructuredTO();

        remittanceInformationStructuredTO.setReference(spiRemittance.getReference());
        remittanceInformationStructuredTO.setReferenceType(spiRemittance.getReferenceType());
        remittanceInformationStructuredTO.setReferenceIssuer(spiRemittance.getReferenceIssuer());

        return remittanceInformationStructuredTO;
    }

    private PurposeCodeTO mapToPurposeCodeTO(PurposeCode purposeCode) {
        return Optional.ofNullable(purposeCode)
                       .map(PurposeCode::name)
                       .map(PurposeCodeTO::valueOf)
                       .orElse(null);
    }

    private AddressTO mapToAddressTO(SpiAddress address) {
        if (address == null) {
            return null;
        }

        AddressTO addressTO = new AddressTO();

        addressTO.setStreet(address.getStreetName());
        addressTO.setBuildingNumber(address.getBuildingNumber());
        addressTO.setCity(address.getTownName());
        addressTO.setPostalCode(address.getPostCode());
        addressTO.setCountry(address.getCountry());

        return addressTO;
    }

    private AmountTO mapToAmountTO(SpiAmount amount) {
        if (amount == null) {
            return null;
        }

        AmountTO amountTO = new AmountTO();
        amountTO.setCurrency(amount.getCurrency());
        amountTO.setAmount(amount.getAmount());

        return amountTO;
    }

    private AccountReferenceTO mapToAccountReferenceTO(SpiAccountReference spiAccountReference) {
        if (spiAccountReference == null) {
            return null;
        }
        AccountReferenceTO accountReferenceTO = new AccountReferenceTO();

        accountReferenceTO.setIban(spiAccountReference.getIban());
        accountReferenceTO.setBban(spiAccountReference.getBban());
        accountReferenceTO.setPan(spiAccountReference.getPan());
        accountReferenceTO.setMaskedPan(spiAccountReference.getMaskedPan());
        accountReferenceTO.setMsisdn(spiAccountReference.getMsisdn());
        accountReferenceTO.setCurrency(spiAccountReference.getCurrency());

        return accountReferenceTO;
    }

    private FrequencyCodeTO mapToFrequencyCodeTO(FrequencyCode frequencyCode) {
        return Optional.ofNullable(frequencyCode)
                       .map(FrequencyCode::name)
                       .map(FrequencyCodeTO::valueOf)
                       .orElse(null);
    }

}
