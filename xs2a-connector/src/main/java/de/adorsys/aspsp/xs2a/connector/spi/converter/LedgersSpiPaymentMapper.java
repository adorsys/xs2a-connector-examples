package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.ledgers.middleware.api.domain.general.AddressTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTargetTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PurposeCodeTO;
import de.adorsys.ledgers.middleware.api.domain.payment.RemittanceInformationStructuredTO;
import de.adorsys.ledgers.middleware.api.domain.payment.TransactionStatusTO;
import de.adorsys.psd2.core.payment.model.PurposeCode;
import de.adorsys.psd2.xs2a.core.pis.FrequencyCode;
import de.adorsys.psd2.xs2a.core.pis.PisDayOfExecution;
import de.adorsys.psd2.xs2a.core.pis.PisExecutionRule;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiAddress;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiBulkPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiRemittance;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LedgersSpiPaymentMapper {

    private final LedgersSpiAccountMapper accountMapper;

    public SpiSinglePayment toSpiSinglePayment(PaymentTO paymentTO) {
        if (paymentTO == null) {
            return null;
        }

        SpiSinglePayment spiSinglePayment = new SpiSinglePayment(paymentTO.getPaymentProduct());
        fillCommonPart(paymentTO, spiSinglePayment);

        return spiSinglePayment;
    }

    public SpiPeriodicPayment mapToSpiPeriodicPayment(PaymentTO paymentTO) {
        if (paymentTO == null) {
            return null;
        }

        SpiPeriodicPayment spiPeriodicPayment = new SpiPeriodicPayment(paymentTO.getPaymentProduct());
        fillCommonPart(paymentTO, spiPeriodicPayment);

        spiPeriodicPayment.setStartDate(paymentTO.getStartDate());
        spiPeriodicPayment.setEndDate(paymentTO.getEndDate());
        spiPeriodicPayment.setFrequency(FrequencyCode.valueOf(paymentTO.getFrequency().name()));
        spiPeriodicPayment.setDayOfExecution(PisDayOfExecution.fromValue(String.valueOf(paymentTO.getDayOfExecution())));
        spiPeriodicPayment.setExecutionRule(PisExecutionRule.getByValue(paymentTO.getExecutionRule()).orElse(null));

        return spiPeriodicPayment;
    }

    public SpiBulkPayment mapToSpiBulkPayment(PaymentTO paymentTO) {
        if (paymentTO == null) {
            return null;
        }

        SpiBulkPayment spiBulkPayment = new SpiBulkPayment();
        spiBulkPayment.setPaymentId(paymentTO.getPaymentId());
        spiBulkPayment.setBatchBookingPreferred(paymentTO.getBatchBookingPreferred());
        spiBulkPayment.setDebtorAccount(accountMapper.toSpiAccountReference(paymentTO.getDebtorAccount()));
        spiBulkPayment.setDebtorName(paymentTO.getDebtorName());
        spiBulkPayment.setRequestedExecutionDate(paymentTO.getRequestedExecutionDate());
        spiBulkPayment.setRequestedExecutionTime(toDateTime(paymentTO.getRequestedExecutionDate(), paymentTO.getRequestedExecutionTime()));
        spiBulkPayment.setPaymentStatus(TransactionStatus.valueOf(paymentTO.getTransactionStatus().name()));
        spiBulkPayment.setPayments(toSpiSinglePaymentsList(paymentTO));
        spiBulkPayment.setPaymentProduct(paymentTO.getPaymentProduct());

        return spiBulkPayment;
    }

    private List<SpiSinglePayment> toSpiSinglePaymentsList(PaymentTO paymentTO) {
        List<SpiSinglePayment> spiSinglePaymentList = new ArrayList<>();
        paymentTO.getTargets().forEach(paymentTargetTO -> {
            SpiSinglePayment spiSinglePayment = new SpiSinglePayment(paymentTO.getPaymentProduct());
            fillCommonPartFromPaymentTargetTO(paymentTargetTO, spiSinglePayment);
            fillCommonPartFromPaymentTO(paymentTO, spiSinglePayment);
            spiSinglePaymentList.add(spiSinglePayment);
        });
        return spiSinglePaymentList;
    }

    private void fillCommonPart(PaymentTO paymentTO, SpiSinglePayment spiPayment) {
        fillCommonPartFromPaymentTargetTO(paymentTO.getTargets().get(0), spiPayment);
        fillCommonPartFromPaymentTO(paymentTO, spiPayment);
    }

    private void fillCommonPartFromPaymentTO(PaymentTO paymentTO, SpiSinglePayment spiPayment) {
        spiPayment.setDebtorAccount(accountMapper.toSpiAccountReference(paymentTO.getDebtorAccount()));
        spiPayment.setPaymentStatus(Optional.ofNullable(paymentTO.getTransactionStatus()).map(TransactionStatusTO::name).map(TransactionStatus::valueOf).orElse(null));
        spiPayment.setRequestedExecutionDate(paymentTO.getRequestedExecutionDate());
        spiPayment.setRequestedExecutionTime(toDateTime(paymentTO.getRequestedExecutionDate(), paymentTO.getRequestedExecutionTime()));
        spiPayment.setDebtorName(paymentTO.getDebtorName());
    }

    private void fillCommonPartFromPaymentTargetTO(PaymentTargetTO paymentTargetTO, SpiSinglePayment spiPayment) {
        spiPayment.setPaymentId(paymentTargetTO.getPaymentId());
        spiPayment.setEndToEndIdentification(paymentTargetTO.getEndToEndIdentification());
        spiPayment.setCreditorAgent(paymentTargetTO.getCreditorAgent());
        spiPayment.setCreditorName(paymentTargetTO.getCreditorName());
        spiPayment.setCreditorAddress(toSpiAddress(paymentTargetTO.getCreditorAddress()));
        spiPayment.setRemittanceInformationUnstructuredArray(paymentTargetTO.getRemittanceInformationUnstructuredArray());
        spiPayment.setInstructedAmount(accountMapper.toSpiAmount(paymentTargetTO.getInstructedAmount()));
        spiPayment.setCreditorAccount(accountMapper.toSpiAccountReference(paymentTargetTO.getCreditorAccount()));
        spiPayment.setRemittanceInformationStructuredArray(mapToRemittanceStructuredArray(paymentTargetTO.getRemittanceInformationStructuredArray()));
        spiPayment.setPurposeCode(Optional.ofNullable(paymentTargetTO.getPurposeCode())
                                          .map(PurposeCodeTO::name)
                                          .map(PurposeCode::fromValue)
                                          .orElse(null));
    }

    public List<SpiRemittance> mapToRemittanceStructuredArray(List<RemittanceInformationStructuredTO> remittanceInformationStructuredTOArray) {
        if (remittanceInformationStructuredTOArray == null) {
            return null;
        }
        return remittanceInformationStructuredTOArray.stream()
                       .map(this::mapToSpiRemittance)
                       .collect(Collectors.toList());
    }

    private OffsetDateTime toDateTime(LocalDate date, LocalTime time) {
        return Optional.ofNullable(date)
                       .map(d -> LocalDateTime.of(d, Optional.ofNullable(time)
                                                             .orElse(LocalTime.ofSecondOfDay(0)))
                                         .atOffset(ZoneOffset.UTC))
                       .orElse(null);
    }

    private SpiAddress toSpiAddress(AddressTO address) {
        return Optional.ofNullable(address)
                       .map(a -> new SpiAddress(
                               a.getStreet(),
                               a.getBuildingNumber(),
                               a.getCity(),
                               a.getPostalCode(),
                               a.getCountry()))
                       .orElse(null);
    }

    private SpiRemittance mapToSpiRemittance(RemittanceInformationStructuredTO remittanceInformationStructuredTO) {
        SpiRemittance spiRemittance = new SpiRemittance();
        spiRemittance.setReference(remittanceInformationStructuredTO.getReference());
        spiRemittance.setReferenceType(remittanceInformationStructuredTO.getReferenceType());
        spiRemittance.setReferenceIssuer(remittanceInformationStructuredTO.getReferenceIssuer());
        return spiRemittance;
    }
}
