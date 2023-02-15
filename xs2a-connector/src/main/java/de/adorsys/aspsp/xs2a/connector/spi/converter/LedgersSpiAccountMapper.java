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

import de.adorsys.aspsp.xs2a.connector.mock.MockAccountData;
import de.adorsys.ledgers.middleware.api.domain.account.*;
import de.adorsys.ledgers.middleware.api.domain.payment.AmountTO;
import de.adorsys.ledgers.middleware.api.domain.payment.RemittanceInformationStructuredTO;
import de.adorsys.psd2.xs2a.spi.domain.account.*;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiAmount;
import de.adorsys.psd2.xs2a.spi.domain.fund.SpiFundsConfirmationRequest;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiRemittance;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public abstract class LedgersSpiAccountMapper {
    public SpiAccountDetails toSpiAccountDetails(AccountDetailsTO accountDetails) {
        return Optional.ofNullable(accountDetails)
                       .map(d -> new SpiAccountDetails(
                               d.getIban(),
                               d.getId(),
                               d.getIban(),
                               d.getBban(),
                               d.getPan(),
                               d.getMaskedPan(),
                               d.getMsisdn(),
                               d.getCurrency(),
                               d.getName(),
                               d.getDisplayName(),
                               d.getProduct(),
                               SpiAccountType.valueOf(d.getAccountType().name()),
                               SpiAccountStatus.valueOf(d.getAccountStatus().name()),
                               d.getBic(),
                               d.getLinkedAccounts(),
                               SpiUsageType.valueOf(d.getUsageType().name()),
                               d.getDetails(),
                               toSpiAccountBalancesList(d.getBalances()),
                               null,
                               null))
                       .orElse(null);
    } //Full manual mapping here, no extra tests necessary

    public SpiCardAccountDetails toSpiCardAccountDetails(AccountDetailsTO accountDetails) {
        return Optional.ofNullable(accountDetails)
                       .map(d -> new SpiCardAccountDetails(
                               d.getIban(),
                               d.getId(),
                               d.getMaskedPan(),
                               d.getCurrency(),
                               d.getName(),
                               d.getDisplayName(),
                               d.getProduct(),
                               SpiAccountStatus.valueOf(d.getAccountStatus().name()),
                               SpiAccountType.valueOf(d.getAccountType().name()),
                               SpiUsageType.valueOf(d.getUsageType().name()),
                               d.getDetails(),
                               MockAccountData.CREDIT_LIMIT, // TODO: Remove when ledgers starts supporting card accounts https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/1246
                               toSpiAccountBalancesList(d.getBalances()),
                               null,
                               MockAccountData.DEBIT_ACCOUNTING))
                       .orElse(null);
    } //Full manual mapping here, no extra tests necessary

    public abstract List<SpiTransaction> toSpiTransactions(List<TransactionTO> transactions);

    public abstract List<SpiCardTransaction> toSpiCardTransactions(List<TransactionTO> transactions);

    public SpiTransaction toSpiTransaction(TransactionTO transaction) {

        return Optional.ofNullable(transaction)
                       .map(t -> new SpiTransaction(
                               t.getTransactionId(),
                               t.getEntryReference(),
                               t.getEndToEndId(),
                               t.getMandateId(),
                               t.getCheckId(),
                               t.getCreditorId(),
                               t.getBookingDate(),
                               t.getValueDate(),
                               toSpiAmount(t.getAmount()),
                               toSpiExchangeRateList(t.getExchangeRate()),
                               new SpiTransactionInfo(t.getCreditorName(),
                                                      toSpiAccountReference(t.getCreditorAccount()),
                                                      t.getCreditorAgent(),
                                                      t.getUltimateCreditor(),
                                                      t.getDebtorName(),
                                                      toSpiAccountReference(t.getDebtorAccount()),
                                                      t.getDebtorAgent(),
                                                      t.getUltimateDebtor(),
                                                      null,
                                                      t.getRemittanceInformationUnstructuredArray(),
                                                      null,
                                                      mapToRemittanceStructuredArray(t.getRemittanceInformationStructuredArray()),
                                                      t.getPurposeCode()),
                               t.getBankTransactionCode(),
                               t.getProprietaryBankTransactionCode(),
                               t.getAdditionalInformation(),
                               null, // TODO Map proper field https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/1100
                               accountBalanceTOToSpiAccountBalance(t.getBalanceAfterTransaction()),
                               MockAccountData.BATCH_INDICATOR, MockAccountData.BATCH_NUMBER_OF_TRANSACTIONS, mapToSpiEntryDetailsList(t)))
                       .orElse(null);
    }  //Full manual mapping here, no extra tests necessary

    public SpiCardTransaction toSpiCardTransaction(TransactionTO transaction) {
        //TODO: Add real values from ledgers when it starts supporting them
        return Optional.ofNullable(transaction)
                       .map(t -> new SpiCardTransaction(
                               t.getTransactionId(),
                               MockAccountData.TERMINAL_ID,
                               t.getValueDate(),
                               MockAccountData.ACCEPTOR_TRANSACTION_DATE_TIME,
                               t.getBookingDate(),
                               t.getValueDate(),
                               toSpiAmount(t.getAmount()),
                               MockAccountData.GRAND_TOTAL_AMOUNT,
                               toSpiExchangeRateList(t.getExchangeRate()),
                               toSpiAmount(t.getAmount()),
                               toSpiAmount(t.getAmount()),
                               MockAccountData.MARKUP_FEE_PERCENTAGE,
                               t.getCreditorId(),
                               MockAccountData.CARD_ACCEPTOR_ADDRESS,
                               MockAccountData.CARD_ACCEPTOR_PHONE,
                               MockAccountData.MERCHANT_CATEGORY_CODE,
                               MockAccountData.MASKED_PAN,
                               MockAccountData.TRANSACTION_DETAILS,
                               false,
                               t.getProprietaryBankTransactionCode()))
                       .orElse(null);
    }  //Full manual mapping here, no extra tests necessary

    public SpiAccountReference toSpiAccountReference(AccountReferenceTO reference) {
        return Optional.ofNullable(reference)
                       .map(r -> new SpiAccountReference(
                               r.getIban(),
                               r.getIban(),
                               r.getIban(),
                               r.getBban(),
                               r.getPan(),
                               r.getMaskedPan(),
                               r.getMsisdn(),
                               r.getCurrency(),
                               null))
                       .orElse(null);
    } //Full manual mapping here, no extra tests necessary

    public abstract List<SpiAccountBalance> toSpiAccountBalancesList(List<AccountBalanceTO> accountBalanceTOS);

    @Mapping(target = "spiBalanceType", expression = "java(mapToSpiBalanceType(accountBalanceTO.getBalanceType()))")
    @Mapping(source = "amount", target = "spiBalanceAmount")
    public abstract SpiAccountBalance accountBalanceTOToSpiAccountBalance(AccountBalanceTO accountBalanceTO);

    public abstract List<SpiExchangeRate> toSpiExchangeRateList(List<ExchangeRateTO> exchangeRates);

    protected SpiBalanceType mapToSpiBalanceType(BalanceTypeTO balanceTypeTO) {
        return SpiBalanceType.valueOf(balanceTypeTO.name());
    }

    public SpiExchangeRate toSpiExchangeRate(ExchangeRateTO exchangeRate) {
        return Optional.ofNullable(exchangeRate)
                       .map(e -> new SpiExchangeRate(
                               e.getCurrencyFrom().getCurrencyCode(),
                               e.getRateFrom(),
                               e.getCurrency().getCurrencyCode(),
                               e.getRateTo(),
                               e.getRateDate(),
                               e.getRateContract()))
                       .orElse(null);
    } //Full manual mapping here, no extra tests necessary

    public SpiAmount toSpiAmount(AmountTO amount) {
        return Optional.ofNullable(amount)
                       .map(a -> new SpiAmount(a.getCurrency(), a.getAmount()))
                       .orElse(null);
    }//Full manual mapping here, no extra tests necessary

    public abstract FundsConfirmationRequestTO toFundsConfirmationTO(SpiPsuData psuData, SpiFundsConfirmationRequest spiFundsConfirmationRequest);

    public abstract AccountReferenceTO mapToAccountReferenceTO(SpiAccountReference spiAccountReference);

    public List<SpiRemittance> mapToRemittanceStructuredArray(List<RemittanceInformationStructuredTO> remittanceInformationStructuredTO) {
        if (remittanceInformationStructuredTO == null) {
            return null;
        }
        return remittanceInformationStructuredTO.stream()
                       .map(this::mapToSpiRemittance)
                       .collect(Collectors.toList());
    }

    protected SpiRemittance mapToSpiRemittance(RemittanceInformationStructuredTO remittance) {
        return Optional.ofNullable(remittance)
                       .map(r -> {
                           SpiRemittance spiRemittance = new SpiRemittance();
                           spiRemittance.setReference(r.getReference());
                           spiRemittance.setReferenceType(r.getReferenceType());
                           spiRemittance.setReferenceIssuer(r.getReferenceIssuer());
                           return spiRemittance;
                       })
                       .orElse(null);
    }

    private List<SpiEntryDetails> mapToSpiEntryDetailsList(TransactionTO transaction) {
        return Collections.singletonList(new SpiEntryDetails(
                transaction.getEndToEndId(), transaction.getMandateId(), transaction.getCheckId(), transaction.getCreditorId(),
                toSpiAmount(transaction.getAmount()),
                buildSpiExchangeRate(transaction.getExchangeRate()),
                buildSpiTransactionInfo(transaction)

        ));
    }

    private List<SpiExchangeRate> buildSpiExchangeRate(List<ExchangeRateTO> exchangeRates) {
        return Optional.ofNullable(exchangeRates)
                       .map(rates -> rates.stream().map(r -> new SpiExchangeRate(r.getCurrencyFrom().getCurrencyCode(),
                                                                                 r.getRateFrom(),
                                                                                 r.getCurrency().getCurrencyCode(),
                                                                                 r.getRateTo(),
                                                                                 r.getRateDate(),
                                                                                 r.getRateContract()))
                                             .collect(Collectors.toList()))
                       .orElse(null);
    }

    private SpiTransactionInfo buildSpiTransactionInfo(TransactionTO transaction) {
        return new SpiTransactionInfo(transaction.getCreditorName(),
                                      toSpiAccountReference(transaction.getCreditorAccount()),
                                      transaction.getCreditorAgent(),
                                      transaction.getUltimateCreditor(),
                                      transaction.getDebtorName(),
                                      toSpiAccountReference(transaction.getDebtorAccount()),
                                      transaction.getDebtorAgent(),
                                      transaction.getUltimateDebtor(),
                                      null,
                                      transaction.getRemittanceInformationUnstructuredArray(),
                                      null,
                                      mapToRemittanceStructuredArray(transaction.getRemittanceInformationStructuredArray()),
                                      transaction.getPurposeCode());
    }
}
