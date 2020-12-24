/*
 * Copyright 2018-2019 adorsys GmbH & Co KG
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

import com.fasterxml.jackson.databind.ObjectMapper;
import de.adorsys.ledgers.middleware.api.domain.account.AccountReferenceTO;
import de.adorsys.ledgers.middleware.api.domain.general.AddressTO;
import de.adorsys.ledgers.middleware.api.domain.payment.*;
import de.adorsys.psd2.core.payment.model.*;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPaymentInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Currency;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class LedgersSpiPaymentToMapper {
    @Autowired
    protected ObjectMapper objectMapper;

    public PaymentTO toPaymentTO_Single(SpiPaymentInfo spiPaymentInfo) {
        return Optional.ofNullable(spiPaymentInfo.getPaymentData())
                       .filter(ArrayUtils::isNotEmpty)
                       .map(paymentData -> convert(paymentData, PaymentInitiationJson.class))
                       .map(payment -> {

                           PaymentTO paymentTO = new PaymentTO();
                           paymentTO.setPaymentId(spiPaymentInfo.getPaymentId());
                           paymentTO.setPaymentType(PaymentTypeTO.valueOf(spiPaymentInfo.getPaymentType().name()));
                           paymentTO.setPaymentProduct(spiPaymentInfo.getPaymentProduct());
                           paymentTO.setDebtorAccount(mapToAccountReferenceTO(payment.getDebtorAccount()));
                           paymentTO.setDebtorName(payment.getUltimateDebtor());
                           paymentTO.setTargets(Collections.singletonList(mapToPaymentTargetTO(payment, spiPaymentInfo)));
                           return paymentTO;

                       })
                       .orElse(null);
    }

    public PaymentTO toPaymentTO_Bulk(SpiPaymentInfo spiPaymentInfo) {
        return Optional.ofNullable(spiPaymentInfo.getPaymentData())
                       .filter(ArrayUtils::isNotEmpty)
                       .map(paymentData -> convert(paymentData, BulkPaymentInitiationJson.class))
                       .map(payment -> {

                           PaymentTO paymentTO = new PaymentTO();
                           paymentTO.setPaymentId(spiPaymentInfo.getPaymentId());
                           paymentTO.setPaymentType(PaymentTypeTO.valueOf(spiPaymentInfo.getPaymentType().name()));
                           paymentTO.setPaymentProduct(spiPaymentInfo.getPaymentProduct());
                           paymentTO.setDebtorAccount(mapToAccountReferenceTO(payment.getDebtorAccount()));
                           paymentTO.setBatchBookingPreferred(payment.getBatchBookingPreferred());
                           paymentTO.setRequestedExecutionDate(payment.getRequestedExecutionDate());
                           paymentTO.setRequestedExecutionTime(Optional.ofNullable(payment.getRequestedExecutionTime()).map(OffsetDateTime::toLocalTime).orElse(null));
                           paymentTO.setTargets(payment.getPayments().stream()
                                                        .map(bulk -> mapToPaymentTargetTO(bulk, spiPaymentInfo))
                                                        .collect(Collectors.toList()));
                           return paymentTO;
                       })
                       .orElse(null);

    }

    public PaymentTO toPaymentTO_Periodic(SpiPaymentInfo spiPaymentInfo) {
        return Optional.ofNullable(spiPaymentInfo.getPaymentData())
                       .filter(ArrayUtils::isNotEmpty)
                       .map(paymentData -> convert(paymentData, PeriodicPaymentInitiationJson.class))
                       .map(payment -> {

                           PaymentTO paymentTO = new PaymentTO();
                           paymentTO.setPaymentId(spiPaymentInfo.getPaymentId());
                           paymentTO.setPaymentType(PaymentTypeTO.valueOf(spiPaymentInfo.getPaymentType().name()));
                           paymentTO.setPaymentProduct(spiPaymentInfo.getPaymentProduct());
                           paymentTO.setDebtorAccount(mapToAccountReferenceTO(payment.getDebtorAccount()));

                           paymentTO.setStartDate(payment.getStartDate());
                           paymentTO.setEndDate(payment.getEndDate());
                           paymentTO.setExecutionRule(Optional.ofNullable(payment.getExecutionRule())
                                                              .map(ExecutionRule::toString)
                                                              .orElse(null));
                           paymentTO.setFrequency(mapToFrequencyCodeTO(payment.getFrequency()));
                           paymentTO.setDayOfExecution(Optional.ofNullable(payment.getDayOfExecution())
                                                               .map(d -> Integer.valueOf(d.toString()))
                                                               .orElse(null));
                           paymentTO.setDebtorName(payment.getUltimateDebtor());
                           paymentTO.setTargets(Collections.singletonList(mapToPaymentTargetTO(payment, spiPaymentInfo)));

                           return paymentTO;
                       })
                       .orElse(null);
    }

    private FrequencyCodeTO mapToFrequencyCodeTO(FrequencyCode frequencyCode) {
        return Optional.ofNullable(frequencyCode)
                       .map(FrequencyCode::name)
                       .map(FrequencyCodeTO::valueOf)
                       .orElse(null);
    }

    private <T> T convert(byte[] paymentData, Class<T> tClass) {
        try {
            return objectMapper.readValue(paymentData, tClass);
        } catch (IOException e) {
            log.warn("Can't convert byte[] to Object {}", e.getMessage());
            return null;
        }
    }

    private AccountReferenceTO mapToAccountReferenceTO(AccountReference accountReference) {
        if (accountReference == null) {
            return null;
        }
        AccountReferenceTO accountReferenceTO = new AccountReferenceTO();

        accountReferenceTO.setIban(accountReference.getIban());
        accountReferenceTO.setBban(accountReference.getBban());
        accountReferenceTO.setPan(accountReference.getPan());
        accountReferenceTO.setMaskedPan(accountReference.getMaskedPan());
        accountReferenceTO.setMsisdn(accountReference.getMsisdn());
        accountReferenceTO.setCurrency(mapToCurrency(accountReference.getCurrency()));

        return accountReferenceTO;
    }

    private PaymentTargetTO mapToPaymentTargetTO(PeriodicPaymentInitiationJson payment, SpiPaymentInfo spiPaymentInfo) {
        if (payment == null) {
            return null;
        }

        PaymentTargetTO paymentTargetTO = new PaymentTargetTO();

        paymentTargetTO.setPaymentId(spiPaymentInfo.getPaymentId());
        paymentTargetTO.setEndToEndIdentification(payment.getEndToEndIdentification());
        paymentTargetTO.setInstructedAmount(mapToAmountTO(payment.getInstructedAmount()));
        paymentTargetTO.setCreditorAccount(mapToAccountReferenceTO(payment.getCreditorAccount()));
        paymentTargetTO.setCreditorAgent(payment.getCreditorAgent());
        paymentTargetTO.setCreditorName(payment.getCreditorName());
        paymentTargetTO.setCreditorAddress(mapToAddressTO(payment.getCreditorAddress()));
        paymentTargetTO.setPurposeCode(mapToPurposeCodeTO(payment.getPurposeCode()));
        paymentTargetTO.setRemittanceInformationUnstructured(payment.getRemittanceInformationUnstructured());
        paymentTargetTO.setRemittanceInformationStructured(mapToRemittanceInformationStructuredTO(payment.getRemittanceInformationStructured()));

        return paymentTargetTO;
    }

    private PaymentTargetTO mapToPaymentTargetTO(PaymentInitiationJson payment, SpiPaymentInfo spiPaymentInfo) {
        if (payment == null) {
            return null;
        }

        PaymentTargetTO paymentTargetTO = new PaymentTargetTO();

        paymentTargetTO.setPaymentId(spiPaymentInfo.getPaymentId());
        paymentTargetTO.setEndToEndIdentification(payment.getEndToEndIdentification());
        paymentTargetTO.setInstructedAmount(mapToAmountTO(payment.getInstructedAmount()));
        paymentTargetTO.setCreditorAccount(mapToAccountReferenceTO(payment.getCreditorAccount()));
        paymentTargetTO.setCreditorAgent(payment.getCreditorAgent());
        paymentTargetTO.setCreditorName(payment.getCreditorName());
        paymentTargetTO.setCreditorAddress(mapToAddressTO(payment.getCreditorAddress()));
        paymentTargetTO.setPurposeCode(mapToPurposeCodeTO(payment.getPurposeCode()));
        paymentTargetTO.setRemittanceInformationUnstructured(payment.getRemittanceInformationUnstructured());
        paymentTargetTO.setRemittanceInformationStructured(mapToRemittanceInformationStructuredTO(payment.getRemittanceInformationStructured()));

        return paymentTargetTO;
    }

    protected RemittanceInformationStructuredTO mapToRemittanceInformationStructuredTO(RemittanceInformationStructured remittanceInformationStructured) {
        if (remittanceInformationStructured == null) {
            return null;
        }

        RemittanceInformationStructuredTO remittanceInformationStructuredTO = new RemittanceInformationStructuredTO();

        remittanceInformationStructuredTO.setReference(remittanceInformationStructured.getReference());
        remittanceInformationStructuredTO.setReferenceType(remittanceInformationStructured.getReferenceType());
        remittanceInformationStructuredTO.setReferenceIssuer(remittanceInformationStructured.getReferenceIssuer());

        return remittanceInformationStructuredTO;
    }

    private PurposeCodeTO mapToPurposeCodeTO(PurposeCode purposeCode) {
        return Optional.ofNullable(purposeCode)
                       .map(PurposeCode::name)
                       .map(PurposeCodeTO::valueOf)
                       .orElse(null);
    }

    private AmountTO mapToAmountTO(Amount amount) {
        if (amount == null) {
            return null;
        }

        AmountTO amountTO = new AmountTO();
        amountTO.setCurrency(mapToCurrency(amount.getCurrency()));
        amountTO.setAmount(BigDecimal.valueOf(Double.parseDouble(amount.getAmount())));

        return amountTO;
    }

    private AddressTO mapToAddressTO(Address address) {
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

    private Currency mapToCurrency(String currency) {
        return Optional.ofNullable(currency).map(Currency::getInstance).orElse(null);
    }
}
