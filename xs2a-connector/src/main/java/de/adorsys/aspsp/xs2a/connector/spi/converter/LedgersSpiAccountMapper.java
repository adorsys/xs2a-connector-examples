package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.aspsp.xs2a.connector.mock.MockAccountData;
import de.adorsys.ledgers.middleware.api.domain.account.*;
import de.adorsys.ledgers.middleware.api.domain.payment.AmountTO;
import de.adorsys.ledgers.middleware.api.domain.payment.RemittanceInformationStructuredTO;
import de.adorsys.psd2.xs2a.core.pis.Remittance;
import de.adorsys.psd2.xs2a.spi.domain.account.*;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiAmount;
import de.adorsys.psd2.xs2a.spi.domain.fund.SpiFundsConfirmationRequest;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Optional;

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
                               MockAccountData.DISPLAY_NAME,
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
                               MockAccountData.DISPLAY_NAME,
                               d.getProduct(),
                               SpiAccountStatus.valueOf(d.getAccountStatus().name()),
                               SpiAccountType.valueOf(d.getAccountType().name()),
                               SpiUsageType.valueOf(d.getUsageType().name()),
                               d.getDetails(),
                               MockAccountData.CREDIT_LIMIT, // TODO: Remove when ledgers starts supporting card accounts https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/1246
                               toSpiAccountBalancesList(d.getBalances()),
                               null))
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
                               t.getCreditorName(),
                               toSpiAccountReference(t.getCreditorAccount()),
                               t.getCreditorAgent(),
                               t.getUltimateCreditor(),
                               t.getDebtorName(),
                               toSpiAccountReference(t.getDebtorAccount()),
                               t.getDebtorAgent(),
                               t.getUltimateDebtor(),
                               t.getRemittanceInformationUnstructured(),
                               MockAccountData.REMITTANCE_UNSTRUCTURED_ARRAY,
                               mapToRemittance(t.getRemittanceInformationStructured()),
                               MockAccountData.REMITTANCE_STRUCTURED_ARRAY,
                               t.getPurposeCode(),
                               t.getBankTransactionCode(),
                               t.getProprietaryBankTransactionCode(),
                               MockAccountData.ADDITIONAL_INFORMATION,
                               null, // TODO Map proper field https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/1100
                               accountBalanceTOToSpiAccountBalance(t.getBalanceAfterTransaction())))
                       .orElse(null);
    }  //Full manual mapping here, no extra tests necessary

    public SpiCardTransaction toSpiCardTransaction(TransactionTO transaction) {
        return Optional.ofNullable(transaction)
                       .map(t -> new SpiCardTransaction(
                               t.getTransactionId(),
                               MockAccountData.TERMINAL_ID,
                               t.getValueDate(),
                               MockAccountData.ACCEPTOR_TRANSACTION_DATE_TIME,
                               t.getBookingDate(),
                               toSpiAmount(t.getAmount()),
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
                               r.getCurrency()))
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

    public abstract Remittance mapToRemittance(RemittanceInformationStructuredTO remittanceInformationStructuredTO);
}
