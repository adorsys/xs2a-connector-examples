/*
 * Copyright 2018-2018 adorsys GmbH & Co KG
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

package de.adorsys.aspsp.xs2a.spi.impl;

import de.adorsys.aspsp.xs2a.spi.mappers.LedgersSpiAccountMapper;
import de.adorsys.ledgers.LedgersAccountRestClient;
import de.adorsys.ledgers.domain.account.AccountDetailsTO;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.exception.RestException;
import de.adorsys.psd2.xs2a.spi.domain.account.*;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiAccountAccess;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.AccountSpi;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class AccountSpiImpl implements AccountSpi {
    // Test data is used there for testing purposes to have the possibility to see if AccountSpiImpl is being invoked from xs2a.
    // TODO remove if some requirements will be received https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/394
    private static final String TEST_ASPSP_DATA = "Test aspsp data";

    private static final Logger logger = LoggerFactory.getLogger(SinglePaymentSpiImpl.class);

    private final LedgersAccountRestClient restClient;
    private final LedgersSpiAccountMapper accountMapper;

    public AccountSpiImpl(LedgersAccountRestClient restClient, LedgersSpiAccountMapper accountMapper) {
        this.restClient = restClient;
        this.accountMapper = accountMapper;
    }

    @Override
    public SpiResponse<List<SpiAccountDetails>> requestAccountList(boolean withBalance, @NotNull SpiAccountConsent accountConsent, @NotNull AspspConsentData aspspConsentData) {
        try {
            List<SpiAccountDetails> accountDetailsList;

            if (isBankOfferedConsent(accountConsent.getAccess())) {
                accountDetailsList = getAccountDetailsByConsentId(accountConsent);
            } else {
                accountDetailsList = getAccountDetailsFromReferences(withBalance, accountConsent);
            }

            return SpiResponse.<List<SpiAccountDetails>>builder()
                           .payload(filterAccountDetailsByWithBalance(withBalance, accountDetailsList))
                           .aspspConsentData(aspspConsentData.respondWith(TEST_ASPSP_DATA.getBytes()))
                           .success();
        } catch (RestException e) {
            logger.error(e.getMessage());
            if (e.getHttpStatus() == HttpStatus.INTERNAL_SERVER_ERROR) {
                return SpiResponse.<List<SpiAccountDetails>>builder()
                               .fail(SpiResponseStatus.TECHNICAL_FAILURE);
            }

            return SpiResponse.<List<SpiAccountDetails>>builder()
                           .fail(SpiResponseStatus.LOGICAL_FAILURE);
        }
    }

    @Override
    public SpiResponse<SpiAccountDetails> requestAccountDetailForAccount(boolean withBalance, @NotNull SpiAccountReference accountReference, @NotNull SpiAccountConsent accountConsent, @NotNull AspspConsentData aspspConsentData) {
        try {
            SpiAccountDetails accountDetails = Optional.ofNullable(restClient.getDetailsByAccountId(accountReference.getResourceId()).getBody())
                                                       .map(accountMapper::toSpiAccountDetails)
                                                       .orElseThrow(() -> new RestException(HttpStatus.NOT_FOUND, "Response status was 200, but body was empty!"));

            if (!withBalance) {
                accountDetails.emptyBalances();
            }

            return SpiResponse.<SpiAccountDetails>builder()
                           .payload(accountDetails)
                           .aspspConsentData(aspspConsentData.respondWith(TEST_ASPSP_DATA.getBytes()))
                           .success();
        } catch (RestException e) {
            logger.error(e.getMessage());
            if (e.getHttpStatus() == HttpStatus.INTERNAL_SERVER_ERROR) {
                return SpiResponse.<SpiAccountDetails>builder()
                               .fail(SpiResponseStatus.TECHNICAL_FAILURE);
            }

            return SpiResponse.<SpiAccountDetails>builder()
                           .fail(SpiResponseStatus.LOGICAL_FAILURE);
        }
    }

    @Override
    public SpiResponse<SpiTransactionReport> requestTransactionsForAccount(boolean withBalance, @NotNull LocalDate dateFrom, @NotNull LocalDate dateTo, @NotNull SpiAccountReference accountReference, @NotNull SpiAccountConsent accountConsent, @NotNull AspspConsentData aspspConsentData) {
        try {
            Optional<List<SpiTransaction>> transactionsOptional = Optional.ofNullable(
                    restClient.getTransactionByDates(accountReference.getResourceId(), dateFrom, dateTo).getBody())
                                                                          .map(accountMapper::toSpiTransactions);

            List<SpiTransaction> transactions = transactionsOptional.orElseGet(ArrayList::new);
            List<SpiAccountBalance> balances = null;

            if (withBalance) {
                balances = Optional.ofNullable(restClient.getDetailsByAccountId(accountReference.getResourceId()).getBody())
                                   .map(accountMapper::toSpiAccountDetails)
                                   .map(SpiAccountDetails::getBalances)
                                   .orElseGet(ArrayList::new);
            }

            SpiTransactionReport transactionReport = new SpiTransactionReport(transactions, balances);

            return SpiResponse.<SpiTransactionReport>builder()
                           .payload(transactionReport)
                           .aspspConsentData(aspspConsentData.respondWith(TEST_ASPSP_DATA.getBytes()))
                           .success();
        } catch (RestException e) {
            logger.error(e.getMessage());
            if (e.getHttpStatus() == HttpStatus.INTERNAL_SERVER_ERROR) {
                return SpiResponse.<SpiTransactionReport>builder()
                               .fail(SpiResponseStatus.TECHNICAL_FAILURE);
            }

            return SpiResponse.<SpiTransactionReport>builder()
                           .fail(SpiResponseStatus.LOGICAL_FAILURE);
        }
    }

    @Override
    public SpiResponse<SpiTransaction> requestTransactionForAccountByTransactionId(@NotNull String transactionId, @NotNull SpiAccountReference accountReference, @NotNull SpiAccountConsent accountConsent, @NotNull AspspConsentData aspspConsentData) {
        try {
            SpiTransaction transaction = Optional.ofNullable(restClient.getTransactionById(accountReference.getResourceId(), transactionId).getBody())
                                                 .map(accountMapper::toSpiTransaction)
                                                 .orElseThrow(() -> new RestException(HttpStatus.NOT_FOUND, "Response status was 200, but the body was empty!"));

            return SpiResponse.<SpiTransaction>builder()
                           .payload(transaction)
                           .aspspConsentData(aspspConsentData.respondWith(TEST_ASPSP_DATA.getBytes()))
                           .success();
        } catch (RestException e) {
            logger.error(e.getMessage());
            if (e.getHttpStatus() == HttpStatus.INTERNAL_SERVER_ERROR) {
                return SpiResponse.<SpiTransaction>builder()
                               .fail(SpiResponseStatus.TECHNICAL_FAILURE);
            }
            return SpiResponse.<SpiTransaction>builder()
                           .fail(SpiResponseStatus.LOGICAL_FAILURE);
        }
    }

    @Override
    public SpiResponse<List<SpiAccountBalance>> requestBalancesForAccount(@NotNull SpiAccountReference accountReference, @NotNull SpiAccountConsent accountConsent, @NotNull AspspConsentData aspspConsentData) {
        try {
            List<SpiAccountBalance> accountBalances = Optional.ofNullable(restClient.getBalancesByAccountId(accountReference.getResourceId()).getBody())
                                                              .map(accountMapper::toSpiAccountBalancesList)
                                                              .orElseThrow(() -> new RestException(HttpStatus.NOT_FOUND, "Response status was 200, but the body was empty!"));
            return SpiResponse.<List<SpiAccountBalance>>builder()
                           .payload(accountBalances)
                           .aspspConsentData(aspspConsentData.respondWith(TEST_ASPSP_DATA.getBytes()))
                           .success();
        } catch (RestException e) {
            logger.error(e.getMessage());
            if (e.getHttpStatus() == HttpStatus.INTERNAL_SERVER_ERROR) {
                return SpiResponse.<List<SpiAccountBalance>>builder()
                               .fail(SpiResponseStatus.TECHNICAL_FAILURE);
            }

            return SpiResponse.<List<SpiAccountBalance>>builder()
                           .fail(SpiResponseStatus.LOGICAL_FAILURE);
        }
    }

    private boolean isBankOfferedConsent(SpiAccountAccess accountAccess) {
        return CollectionUtils.isEmpty(accountAccess.getBalances())
                       && CollectionUtils.isEmpty(accountAccess.getTransactions())
                       && CollectionUtils.isEmpty(accountAccess.getAccounts());
    }

    private List<SpiAccountDetails> getAccountDetailsByConsentId(SpiAccountConsent accountConsent) {
        String psuId = Optional.ofNullable(accountConsent.getPsuData()).map(SpiPsuData::getPsuId).orElse(null);

        return Optional.ofNullable(restClient.getAccountDetailsByUserLogin(psuId).getBody())
                       .map(l -> l.stream()
                                         .map(accountMapper::toSpiAccountDetails)
                                         .collect(Collectors.toList()))
                       .orElseGet(Collections::emptyList);
    }

    private List<SpiAccountDetails> getAccountDetailsFromReferences(boolean withBalance, SpiAccountConsent accountConsent) { // TODO remove consentId param, when SpiAccountConsent contains it https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/430
        SpiAccountAccess accountAccess = accountConsent.getAccess();
        List<SpiAccountReference> references = withBalance
                                                       ? accountAccess.getBalances()
                                                       : accountAccess.getAccounts();

        return getAccountDetailsFromReferences(references);
    }

    private List<SpiAccountDetails> getAccountDetailsFromReferences(List<SpiAccountReference> references) {
        if (CollectionUtils.isEmpty(references)) {
            return Collections.emptyList();
        }
        return references.stream()
                       .map(this::getAccountDetailsByAccountReference)
                       .filter(Optional::isPresent)
                       .map(Optional::get)
                       .collect(Collectors.toList());
    }

    private Optional<SpiAccountDetails> getAccountDetailsByAccountReference(SpiAccountReference reference) {
        if (reference == null) {
            return Optional.empty();
        }

        // TODO don't use IBAN as an account identifier https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/440
        List<AccountDetailsTO> response = Optional.ofNullable(restClient.getAccountDetailsByIban(reference.getIban()).getBody())
                                                  .orElseGet(Collections::emptyList);

        // TODO don't use currency as an account identifier https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/440
        return response.stream()
                       .filter(acc -> acc.getCurrency() == reference.getCurrency())
                       .findFirst()
                       .map(accountMapper::toSpiAccountDetails);
    }

    private List<SpiAccountDetails> filterAccountDetailsByWithBalance(boolean withBalance, List<SpiAccountDetails> details) {
        if (!withBalance) {
            details.forEach(SpiAccountDetails::emptyBalances);
        }
        return details;
    }
}
