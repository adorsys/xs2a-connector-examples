/*
 * Copyright 2018-2020 adorsys GmbH & Co KG
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

package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.aspsp.xs2a.connector.account.IbanAccountReference;
import de.adorsys.aspsp.xs2a.connector.account.OwnerNameService;
import de.adorsys.aspsp.xs2a.connector.mock.IbanResolverMockService;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapper;
import de.adorsys.ledgers.middleware.api.domain.account.AccountDetailsTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import de.adorsys.ledgers.rest.client.AccountRestClient;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.psd2.xs2a.core.ais.BookingStatus;
import de.adorsys.psd2.xs2a.core.consent.AisConsentRequestType;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.*;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiAmount;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiAccountAccess;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.CardAccountSpi;
import feign.FeignException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Component
@PropertySource("classpath:mock-data.properties")
public class CardAccountSpiImpl implements CardAccountSpi {

    private static final String RESPONSE_STATUS_200_WITH_EMPTY_BODY = "Response status was 200, but the body was empty!";

    private static final Logger logger = LoggerFactory.getLogger(CardAccountSpiImpl.class);

    private static final String DEFAULT_ACCEPT_MEDIA_TYPE = MediaType.APPLICATION_JSON_VALUE;
    private static final String WILDCARD_ACCEPT_HEADER = "*/*";
    private static final String CARD_TRANSACTION_ACCEPTOR = "Müller";

    private final AccountRestClient accountRestClient;
    private final LedgersSpiAccountMapper accountMapper;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService tokenService;
    private final FeignExceptionReader feignExceptionReader;
    private final IbanResolverMockService ibanResolverMockService;
    private final OwnerNameService ownerNameService;

    public CardAccountSpiImpl(AccountRestClient restClient, LedgersSpiAccountMapper accountMapper,
                              AuthRequestInterceptor authRequestInterceptor, AspspConsentDataService tokenService,
                              FeignExceptionReader feignExceptionReader, IbanResolverMockService ibanResolverMockService,
                              OwnerNameService ownerNameService) {
        this.accountRestClient = restClient;
        this.accountMapper = accountMapper;
        this.authRequestInterceptor = authRequestInterceptor;
        this.tokenService = tokenService;
        this.feignExceptionReader = feignExceptionReader;
        this.ibanResolverMockService = ibanResolverMockService;
        this.ownerNameService = ownerNameService;
    }

    @Override
    public SpiResponse<List<SpiCardAccountDetails>> requestCardAccountList(@NotNull SpiContextData contextData,
                                                                           @NotNull SpiAccountConsent accountConsent,
                                                                           @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();

        try {
            SCAResponseTO response = applyAuthorisation(aspspConsentData);

            logger.info("Requested card account list for consent with ID: {}", accountConsent.getId());
            List<SpiCardAccountDetails> cardAccountDetailsList = getSpiCardAccountDetails(accountConsent, aspspConsentData);

            aspspConsentDataProvider.updateAspspConsentData(tokenService.store(response));

            List<SpiCardAccountDetails> cardAccountDetailsListWithMaskedPan = mapToCardAccountList(cardAccountDetailsList);
            List<SpiCardAccountDetails> cardAccountDetailsListWithOwnerName = cardAccountDetailsListWithMaskedPan.stream()
                                                                                      .map(accountDetails -> enrichWithOwnerName(accountDetails, accountConsent.getAccess()))
                                                                                      .collect(Collectors.toList());

            return SpiResponse.<List<SpiCardAccountDetails>>builder()
                           .payload(cardAccountDetailsListWithOwnerName)
                           .build();
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Request card account list failed: consent ID {}, devMessage {}", accountConsent.getId(), devMessage);

            return SpiResponse.<List<SpiCardAccountDetails>>builder()
                           .error(buildTppMessage(feignException))
                           .build();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    @Override
    public SpiResponse<SpiCardAccountDetails> requestCardAccountDetailsForAccount(@NotNull SpiContextData contextData,
                                                                                  @NotNull SpiAccountReference accountReference,
                                                                                  @NotNull SpiAccountConsent accountConsent,
                                                                                  @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();

        try {
            SCAResponseTO response = applyAuthorisation(aspspConsentData);

            logger.info("Requested details for account, ACCOUNT-ID: {}", accountReference.getResourceId());

            SpiCardAccountDetails cardAccountDetails = Optional
                                                               .ofNullable(accountRestClient.getAccountDetailsById(accountReference.getResourceId()).getBody())
                                                               .map(accountMapper::toSpiCardAccountDetails)
                                                               .orElseThrow(() -> FeignExceptionHandler.getException(HttpStatus.NOT_FOUND, RESPONSE_STATUS_200_WITH_EMPTY_BODY));

            cardAccountDetails.setMaskedPan(ibanResolverMockService.getMaskedPanByIban(cardAccountDetails.getAspspAccountId())); // TODO: Remove when ledgers starts supporting card accounts https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/1246

            aspspConsentDataProvider.updateAspspConsentData(tokenService.store(response));
            SpiCardAccountDetails accountDetailsWithOwnerName = enrichWithOwnerName(cardAccountDetails, accountConsent.getAccess());

            return SpiResponse.<SpiCardAccountDetails>builder()
                           .payload(accountDetailsWithOwnerName)
                           .build();

        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Request card account details for account failed: consent ID {}, resource ID {}, devMessage {}", accountConsent.getId(), accountReference.getResourceId(), devMessage);
            return SpiResponse.<SpiCardAccountDetails>builder()
                           .error(buildTppMessage(feignException))
                           .build();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    @Override
    public SpiResponse<SpiCardTransactionReport> requestCardTransactionsForAccount(@NotNull SpiContextData contextData,
                                                                                   @NotNull SpiTransactionReportParameters spiTransactionReportParameters,
                                                                                   @NotNull SpiAccountReference accountReference,
                                                                                   @NotNull SpiAccountConsent accountConsent,
                                                                                   @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        // TODO Remove it https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/1100
        if (BookingStatus.INFORMATION == spiTransactionReportParameters.getBookingStatus()) {
            logger.info("Retrieving mock standing order report for account: {}", accountReference.getResourceId());
            SpiCardTransactionReport transactionReport = new SpiCardTransactionReport("dGVzdA==", buildSpiTransactionList(), Collections.singletonList(buildSpiAccountBalance()), "application/json", null);
            return SpiResponse.<SpiCardTransactionReport>builder()
                           .payload(transactionReport)
                           .build();
        }


        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();
        // TODO https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/1106
        // For dates there are alternative test values in case of receiving NULLs, ledgers must receive dates for retrieving transactions request
        // Will be deleted when Ledgers provides supporting getting transactions list without dates.
        LocalDate dateFrom = Optional.ofNullable(spiTransactionReportParameters.getDateFrom())
                                     .orElse(LocalDate.now().minusMonths(6));
        LocalDate dateTo = Optional.ofNullable(spiTransactionReportParameters.getDateTo())
                                   .orElse(LocalDate.now());
        boolean withBalance = spiTransactionReportParameters.isWithBalance();
        String acceptMediaType = spiTransactionReportParameters.getAcceptMediaType();
        String entryReferenceFrom = spiTransactionReportParameters.getEntryReferenceFrom();
        Boolean deltaList = spiTransactionReportParameters.getDeltaList();

        try {
            SCAResponseTO response = applyAuthorisation(aspspConsentData);

            logger.info("Requested transactions for account: {}, dates from: {}, to: {}, withBalance: {}, entryReferenceFrom: {}, deltaList: {}",
                        accountReference.getResourceId(), dateFrom, dateTo, withBalance, entryReferenceFrom, deltaList);
            List<SpiCardTransaction> transactions = Optional.ofNullable(
                    accountRestClient.getTransactionByDates(accountReference.getResourceId(), dateFrom, dateTo).getBody())
                                                            .map(accountMapper::toSpiCardTransactions).orElseGet(ArrayList::new);
            List<SpiAccountBalance> balances = getSpiAccountBalances(contextData, withBalance, accountReference,
                                                                     accountConsent, aspspConsentDataProvider);

            SpiCardTransactionReport transactionReport =
                    new SpiCardTransactionReport("dGVzdA==", transactions, balances, processAcceptMediaType(acceptMediaType), null);

            logger.info("Finally found {} transactions.", transactionReport.getCardTransactions().size());

            aspspConsentDataProvider.updateAspspConsentData(tokenService.store(response));

            return SpiResponse.<SpiCardTransactionReport>builder()
                           .payload(transactionReport)
                           .build();
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Request transactions for account failed: consent ID {}, resource ID {}, devMessage {}", accountConsent.getId(), accountReference.getResourceId(), devMessage);
            return SpiResponse.<SpiCardTransactionReport>builder()
                           .error(buildTppMessage(feignException))
                           .build();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    private String processAcceptMediaType(String acceptMediaType) {
        return StringUtils.isBlank(acceptMediaType) || WILDCARD_ACCEPT_HEADER.equals(acceptMediaType) ?
                       DEFAULT_ACCEPT_MEDIA_TYPE : acceptMediaType;
    }

    @Override
    public SpiResponse<List<SpiAccountBalance>> requestCardBalancesForAccount(@NotNull SpiContextData contextData,
                                                                              @NotNull SpiAccountReference accountReference,
                                                                              @NotNull SpiAccountConsent accountConsent,
                                                                              @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();

        try {
            SCAResponseTO response = applyAuthorisation(aspspConsentData);

            logger.info("Requested Balances for ACCOUNT-ID: {}", accountReference.getResourceId());
            List<SpiAccountBalance> accountBalances = Optional
                                                              .ofNullable(accountRestClient.getBalances(accountReference.getResourceId()).getBody())
                                                              .map(accountMapper::toSpiAccountBalancesList)
                                                              .orElseThrow(() -> FeignExceptionHandler.getException(HttpStatus.NOT_FOUND, RESPONSE_STATUS_200_WITH_EMPTY_BODY));
            logger.info("Found Balances: {}", accountBalances.size());

            aspspConsentDataProvider.updateAspspConsentData(tokenService.store(response));

            return SpiResponse.<List<SpiAccountBalance>>builder()
                           .payload(accountBalances)
                           .build();
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Request balances for account failed: consent ID {}, resource ID {}, devMessage {}", accountConsent.getId(), accountReference.getResourceId(), devMessage);
            return SpiResponse.<List<SpiAccountBalance>>builder()
                           .error(buildTppMessage(feignException))
                           .build();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    private List<SpiCardAccountDetails> getSpiCardAccountDetails(@NotNull SpiAccountConsent accountConsent,
                                                                 byte[] aspspConsentData) {
        List<SpiCardAccountDetails> accountDetailsList;
        if (isGlobalConsent(accountConsent.getAccess()) || isAllAvailableAccountsConsent(accountConsent)) {
            logger.info("Consent with ID: {} is a global or available account Consent", accountConsent.getId());
            accountDetailsList = getAccountDetailsByConsentId(aspspConsentData);
        } else {
            logger.info("Consent with ID: {} is a regular consent", accountConsent.getId());
            accountDetailsList = getAccountDetailsFromReferences(accountConsent, aspspConsentData);
        }
        return accountDetailsList;
    }

    private List<SpiAccountBalance> getSpiAccountBalances(@NotNull SpiContextData contextData,
                                                          boolean withBalance,
                                                          @NotNull SpiAccountReference accountReference,
                                                          @NotNull SpiAccountConsent accountConsent,
                                                          @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        if (withBalance) {
            SpiResponse<List<SpiAccountBalance>> response = requestCardBalancesForAccount(contextData, accountReference,
                                                                                          accountConsent, aspspConsentDataProvider);
            if (response.isSuccessful()) {
                return response.getPayload();
            } else {
                throw FeignExceptionHandler.getException(HttpStatus.NOT_FOUND, "Requested transaction can`t be found");
            }
        } else {
            return null;
        }
    }

    private boolean isGlobalConsent(SpiAccountAccess accountAccess) {
        return accountAccess.getAllPsd2() != null;
    }

    private boolean isAllAvailableAccountsConsent(SpiAccountConsent accountConsent) {
        return accountConsent.getAisConsentRequestType() == AisConsentRequestType.ALL_AVAILABLE_ACCOUNTS;
    }

    private List<SpiCardAccountDetails> getAccountDetailsByConsentId(byte[] aspspConsentData) {
        try {
            applyAuthorisation(aspspConsentData);

            return Optional.ofNullable(accountRestClient.getListOfAccounts().getBody())
                           .map(l -> l.stream().map(accountMapper::toSpiCardAccountDetails).collect(Collectors.toList()))
                           .orElseGet(Collections::emptyList);
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    private List<SpiCardAccountDetails> getAccountDetailsFromReferences(SpiAccountConsent accountConsent,
                                                                        byte[] aspspConsentData) {

        SpiAccountAccess accountAccess = accountConsent.getAccess();
        List<SpiAccountReference> references = accountAccess.getAccounts();

        return getAccountDetailsFromReferences(references, aspspConsentData);
    }

    private List<SpiCardAccountDetails> getAccountDetailsFromReferences(List<SpiAccountReference> references,
                                                                        byte[] aspspConsentData) {
        applyAuthorisation(aspspConsentData);

        List<AccountDetailsTO> accountDetails = accountRestClient.getListOfAccounts().getBody();

        if (accountDetails == null) {
            return Collections.emptyList();
        }

        return accountDetails.stream()
                       .filter(account -> filterAccountDetailsByIbanAndCurrency(references, account))
                       .map(accountMapper::toSpiCardAccountDetails)
                       .collect(Collectors.toList());
    }

    private boolean filterAccountDetailsByIbanAndCurrency(List<SpiAccountReference> references, AccountDetailsTO account) {
        return references.stream()
                       .filter(reference -> Optional.ofNullable(reference.getIban())
                                                    .orElseGet(() -> ibanResolverMockService.handleIbanByAccountReference(reference)) // TODO: Remove when ledgers starts supporting card accounts https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/1246
                                                    .equals(account.getIban()))
                       .anyMatch(reference -> reference.getCurrency() == null || reference.getCurrency().equals(account.getCurrency()));
    }

    private SCAResponseTO applyAuthorisation(byte[] aspspConsentData) {
        SCAResponseTO sca = tokenService.response(aspspConsentData);
        authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());
        return sca;
    }

    private TppMessage buildTppMessage(FeignException exception) {
        return FeignExceptionHandler.getFailureMessage(exception, MessageErrorCode.CONSENT_UNKNOWN_400, feignExceptionReader.getErrorMessage(exception));
    }

    private List<SpiCardTransaction> buildSpiTransactionList() {
        List<SpiCardTransaction> transactions = new ArrayList<>();
        transactions.add(buildSpiCardTransactionById("0001"));
        transactions.add(buildSpiCardTransactionById("0002"));
        transactions.add(buildSpiCardTransactionById("0003"));
        return transactions;
    }

    private SpiCardTransaction buildSpiCardTransactionById(String cardTransactionId) {
        return new SpiCardTransaction(cardTransactionId,
                                      "999999999",
                                      LocalDate.of(2019, Month.JANUARY, 4),
                                      OffsetDateTime.of(2019, 1, 4, 10, 0, 0, 0, ZoneOffset.UTC),
                                      LocalDate.of(2019, Month.JANUARY, 4),
                                      new SpiAmount(Currency.getInstance("EUR"), new BigDecimal(200)),
                                      new ArrayList<>(),
                                      new SpiAmount(Currency.getInstance("EUR"), new BigDecimal(200)),
                                      new SpiAmount(Currency.getInstance("EUR"), new BigDecimal(200)),
                                      "2",
                                      CARD_TRANSACTION_ACCEPTOR,
                                      null,
                                      null,
                                      CARD_TRANSACTION_ACCEPTOR,
                                      CARD_TRANSACTION_ACCEPTOR,
                                      CARD_TRANSACTION_ACCEPTOR,
                                      true,
                                      "");
    }

    private SpiAccountBalance buildSpiAccountBalance() {
        SpiAccountBalance accountBalance = new SpiAccountBalance();
        accountBalance.setSpiBalanceAmount(new SpiAmount(Currency.getInstance("EUR"), new BigDecimal(1000)));
        accountBalance.setSpiBalanceType(SpiBalanceType.INTERIM_AVAILABLE);
        accountBalance.setLastCommittedTransaction("abcd");
        accountBalance.setReferenceDate(LocalDate.of(2020, Month.JANUARY, 1));
        accountBalance.setLastChangeDateTime(LocalDateTime.of(2019, Month.FEBRUARY, 15, 10, 0, 0, 0));
        return accountBalance;
    }

    private List<SpiCardAccountDetails> mapToCardAccountList(List<SpiCardAccountDetails> details) {
        details.forEach(det -> det.setMaskedPan(ibanResolverMockService.getMaskedPanByIban(det.getAspspAccountId()))); // TODO: Remove when ledgers starts supporting card accounts https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/1246
        return details;
    }

    private SpiCardAccountDetails enrichWithOwnerName(SpiCardAccountDetails spiCardAccountDetails, SpiAccountAccess accountAccess) {
        Optional<String> ibanOptional = ibanResolverMockService.getIbanByMaskedPan(spiCardAccountDetails.getMaskedPan());

        if (!ibanOptional.isPresent()) {
            return spiCardAccountDetails;
        }

        IbanAccountReference ibanAccountReference = new IbanAccountReference(ibanOptional.get(), spiCardAccountDetails.getCurrency());
        if (ownerNameService.shouldContainOwnerName(ibanAccountReference, accountAccess)) {
            return ownerNameService.enrichCardAccountDetailsWithOwnerName(spiCardAccountDetails);
        }

        return spiCardAccountDetails;
    }
}
