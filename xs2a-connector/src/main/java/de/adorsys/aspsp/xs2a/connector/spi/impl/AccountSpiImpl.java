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

package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.aspsp.xs2a.connector.account.IbanAccountReference;
import de.adorsys.aspsp.xs2a.connector.account.OwnerNameService;
import de.adorsys.aspsp.xs2a.connector.mock.IbanResolverMockService;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapper;
import de.adorsys.aspsp.xs2a.connector.spi.file.exception.FileManagementException;
import de.adorsys.aspsp.xs2a.connector.spi.file.util.FileManagementService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.service.TransactionLinksService;
import de.adorsys.ledgers.middleware.api.domain.account.AccountDetailsTO;
import de.adorsys.ledgers.middleware.api.domain.account.TransactionTO;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.rest.client.AccountRestClient;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.ledgers.util.domain.CustomPageImpl;
import de.adorsys.psd2.mapper.Xs2aObjectMapper;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.*;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiAmount;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiAccountAccess;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiAisConsentRequestType;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiBookingStatus;
import de.adorsys.psd2.xs2a.spi.domain.error.SpiMessageErrorCode;
import de.adorsys.psd2.xs2a.spi.domain.error.SpiTppMessage;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiAddress;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiFrequencyCode;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPisDayOfExecution;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPisExecutionRule;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.AccountSpi;
import feign.FeignException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.*;
import java.util.stream.Collectors;

@Component
@PropertySource("classpath:mock-data.properties")
public class AccountSpiImpl implements AccountSpi {
    private static final Logger logger = LoggerFactory.getLogger(AccountSpiImpl.class);
    private static final String RESPONSE_STATUS_200_WITH_EMPTY_BODY = "Response status was 200, but the body was empty!";
    private static final String DEFAULT_ACCEPT_MEDIA_TYPE = MediaType.APPLICATION_JSON_VALUE;
    private static final String WILDCARD_ACCEPT_HEADER = "*/*";
    private static final Integer DEFAULT_TOTAL_PAGES = 1;

    private final AccountRestClient accountRestClient;
    private final LedgersSpiAccountMapper accountMapper;
    private final AuthRequestInterceptor authRequestInterceptor;
    private final AspspConsentDataService consentDataService;
    private final FeignExceptionReader feignExceptionReader;
    private final IbanResolverMockService ibanResolverMockService;
    private final OwnerNameService ownerNameService;
    private final TransactionLinksService transactionLinksService;
    private final FileManagementService fileManagementService;
    private final Xs2aObjectMapper xs2aObjectMapper;


    @Value("${xs2a.transaction.list.defaultPage}")
    private int defaultPage;
    @Value("${xs2a.transaction.list.defaultSize}")
    private int defaultSize;
    @Value("${xs2a.transaction.list.filename:transactions.json}")
    private String transactionsFilename;

    public AccountSpiImpl(AccountRestClient restClient, LedgersSpiAccountMapper accountMapper,
                          AuthRequestInterceptor authRequestInterceptor, AspspConsentDataService consentDataService,
                          FeignExceptionReader feignExceptionReader, IbanResolverMockService ibanResolverMockService,
                          OwnerNameService ownerNameService, TransactionLinksService transactionLinksService,
                          FileManagementService fileManagementService, Xs2aObjectMapper xs2aObjectMapper) {
        this.accountRestClient = restClient;
        this.accountMapper = accountMapper;
        this.authRequestInterceptor = authRequestInterceptor;
        this.consentDataService = consentDataService;
        this.feignExceptionReader = feignExceptionReader;
        this.ibanResolverMockService = ibanResolverMockService;
        this.ownerNameService = ownerNameService;
        this.transactionLinksService = transactionLinksService;
        this.fileManagementService = fileManagementService;
        this.xs2aObjectMapper = xs2aObjectMapper;
    }

    @Override
    public SpiResponse<List<SpiAccountDetails>> requestAccountList(@NotNull SpiContextData contextData,
                                                                   boolean withBalance,
                                                                   @NotNull SpiAccountConsent accountConsent,
                                                                   @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();

        try {
            GlobalScaResponseTO response = applyAuthorisation(aspspConsentData);

            logger.info("Requested account list for consent with ID: {} and withBalance: {}", accountConsent.getId(),
                        withBalance);
            List<SpiAccountDetails> accountDetailsList = getSpiAccountDetails(withBalance, accountConsent);

            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(response));

            List<SpiAccountDetails> accountDetailsListWithOwnerName = accountDetailsList.stream()
                                                                              .map(accountDetail -> enrichWithOwnerName(accountDetail, accountConsent.getAccess()))
                                                                              .collect(Collectors.toList());

            List<SpiAccountDetails> payload = filterAccountDetailsByWithBalance(withBalance, accountDetailsListWithOwnerName, accountConsent.getAccess());

            return SpiResponse.<List<SpiAccountDetails>>builder()
                           .payload(payload)
                           .build();
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Request account list failed: consent ID {}, devMessage {}", accountConsent.getId(), devMessage);
            return SpiResponse.<List<SpiAccountDetails>>builder()
                           .error(buildTppMessage(feignException))
                           .build();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    @Override
    public SpiResponse<List<SpiTrustedBeneficiaries>> requestTrustedBeneficiariesList(@NotNull SpiContextData spiContextData, SpiAccountReference accountReference, @NotNull SpiAccountConsent spiAccountConsent, @NotNull SpiAspspConsentDataProvider spiAspspConsentDataProvider) {
        // TODO: replace with real response from ledgers https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/1255
        logger.info("Retrieving mock trusted beneficiaries list for consent: {}", spiAccountConsent);
        SpiTrustedBeneficiaries trustedBeneficiaries = new SpiTrustedBeneficiaries(
                "mocked trusted beneficiaries id",
                SpiAccountReference.builder().iban("mocked debtor iban").build(),
                SpiAccountReference.builder().iban("mocked creditor iban").build(),
                "mocked creditor agent",
                "mocked creditor name",
                "mocked creditor alias",
                "mocked creditor id",
                new SpiAddress("mocked street name", "mocked building number", "mocked town name", "mocked post code", "mocked country")
        );

        return SpiResponse.<List<SpiTrustedBeneficiaries>>builder()
                       .payload(Collections.singletonList(trustedBeneficiaries))
                       .build();
    }

    @Override
    public SpiResponse<SpiAccountDetails> requestAccountDetailForAccount(@NotNull SpiContextData contextData,
                                                                         boolean withBalance,
                                                                         @NotNull SpiAccountReference accountReference,
                                                                         @NotNull SpiAccountConsent accountConsent,
                                                                         @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();

        try {
            GlobalScaResponseTO response = applyAuthorisation(aspspConsentData);

            logger.info("Requested details for account, ACCOUNT-ID: {}, withBalance: {}",
                        accountReference.getResourceId(), withBalance);
            SpiAccountDetails accountDetails = Optional.ofNullable(accountRestClient.getAccountDetailsById(accountReference.getResourceId()).getBody())
                                                       .map(accountMapper::toSpiAccountDetails)
                                                       .orElseThrow(() -> FeignExceptionHandler.getException(HttpStatus.NOT_FOUND, RESPONSE_STATUS_200_WITH_EMPTY_BODY));

            SpiAccountDetails accountDetailsWithOwnerName = enrichWithOwnerName(accountDetails, accountConsent.getAccess());

            if (!withBalance) {
                accountDetailsWithOwnerName.emptyBalances();
            }
            logger.info("The responded account RESOURCE-ID: {}", accountDetailsWithOwnerName.getResourceId());

            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(response));

            return SpiResponse.<SpiAccountDetails>builder()
                           .payload(accountDetailsWithOwnerName)
                           .build();

        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Request account details for account failed: consent ID {}, resource ID {}, devMessage {}", accountConsent.getId(), accountReference.getResourceId(), devMessage);
            return SpiResponse.<SpiAccountDetails>builder()
                           .error(buildTppMessage(feignException))
                           .build();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    @Override
    public SpiResponse<SpiTransactionReport> requestTransactionsForAccount(@NotNull SpiContextData contextData,
                                                                           @NotNull SpiTransactionReportParameters spiTransactionReportParameters,
                                                                           @NotNull SpiAccountReference accountReference,
                                                                           @NotNull SpiAccountConsent accountConsent,
                                                                           @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        // TODO Remove it https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/1100
        if (SpiBookingStatus.INFORMATION == spiTransactionReportParameters.getBookingStatus()) {
            logger.info("Retrieving mock standing order report for account: {}", accountReference.getResourceId());
            SpiTransactionReport transactionReport = new SpiTransactionReport(null, createStandingOrderReportMock(), null,
                                                                              processAcceptMediaType(spiTransactionReportParameters.getAcceptMediaType()), null, null, DEFAULT_TOTAL_PAGES);
            return SpiResponse.<SpiTransactionReport>builder()
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
            GlobalScaResponseTO response = applyAuthorisation(aspspConsentData);

            logger.info("Requested transactions for account: {}, dates from: {}, to: {}, withBalance: {}, entryReferenceFrom: {}, deltaList: {}",
                        accountReference.getResourceId(), dateFrom, dateTo, withBalance, entryReferenceFrom, deltaList);

            int page = Optional.ofNullable(spiTransactionReportParameters.getPageIndex()).orElse(defaultPage);
            int size = Optional.ofNullable(spiTransactionReportParameters.getItemsPerPage()).orElse(defaultSize);

            CustomPageImpl<TransactionTO> transactionsOnPage = Optional.ofNullable(
                    accountRestClient.getTransactionByDatesPaged(accountReference.getResourceId(), dateFrom, dateTo, page, size)
                            .getBody()).orElse(null);
            List<SpiTransaction> transactionsPaged = Optional.ofNullable(transactionsOnPage)
                                                             .map(CustomPageImpl::getContent)
                                                             .map(accountMapper::toSpiTransactions)
                                                             .orElseGet(ArrayList::new);

            List<SpiAccountBalance> balances = getSpiAccountBalances(contextData, withBalance, accountReference,
                                                                     accountConsent, aspspConsentDataProvider);
            if (SpiBookingStatus.ALL == spiTransactionReportParameters.getBookingStatus() && page == 0) {
                logger.info("Retrieving mock standing order report for account: {}", accountReference.getResourceId());
                transactionsPaged.addAll(createStandingOrderReportMock());
            }
            SpiTransactionLinks spiTransactionLinks = transactionLinksService.buildSpiTransactionLinks(page, size, transactionsOnPage);

            String downloadLink = getDownloadLink(transactionsPaged);

            SpiTransactionReport transactionReport = new SpiTransactionReport(downloadLink,
                                                                              transactionsPaged,
                                                                              balances,
                                                                              processAcceptMediaType(acceptMediaType),
                                                                              null,
                                                                              spiTransactionLinks,
                                                                              DEFAULT_TOTAL_PAGES);
            logger.info("Finally found {} transactions.", transactionReport.getTransactions().size());

            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(response));

            return SpiResponse.<SpiTransactionReport>builder()
                           .payload(transactionReport)
                           .build();
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Request transactions for account failed: consent ID {}, resource ID {}, devMessage {}", accountConsent.getId(), accountReference.getResourceId(), devMessage);
            return SpiResponse.<SpiTransactionReport>builder()
                           .error(buildTppMessage(feignException))
                           .build();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    private String getDownloadLink(List<SpiTransaction> transactions) {
        try {
            byte[] bytes = xs2aObjectMapper.writeValueAsBytes(transactions);
            Resource resource = new ByteArrayResource(bytes);
            return fileManagementService.saveFileAndBuildDownloadLink(resource, transactionsFilename);
        } catch (IOException ex) {
            logger.error("Unable to save transactions file, Exception: {}, Message: {}", ex.getClass(), ex.getMessage());
            return StringUtils.EMPTY;
        }
    }

    String processAcceptMediaType(String acceptMediaType) {
        return StringUtils.isBlank(acceptMediaType)
                       || WILDCARD_ACCEPT_HEADER.equals(acceptMediaType)
                       || acceptMediaType.contains(",") ? DEFAULT_ACCEPT_MEDIA_TYPE : acceptMediaType;
    }

    @Override
    public SpiResponse<SpiTransaction> requestTransactionForAccountByTransactionId(@NotNull SpiContextData contextData,
                                                                                   @NotNull String transactionId,
                                                                                   @NotNull SpiAccountReference accountReference,
                                                                                   @NotNull SpiAccountConsent accountConsent,
                                                                                   @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();

        try {
            GlobalScaResponseTO response = applyAuthorisation(aspspConsentData);

            logger.info("Requested transaction with TRANSACTION-ID: {} for ACCOUNT-ID: {}", transactionId,
                        accountReference.getResourceId());
            SpiTransaction transaction = Optional
                                                 .ofNullable(
                                                         accountRestClient.getTransactionById(accountReference.getResourceId(), transactionId).getBody())
                                                 .map(accountMapper::toSpiTransaction)
                                                 .orElseThrow(() -> FeignExceptionHandler.getException(HttpStatus.NOT_FOUND, RESPONSE_STATUS_200_WITH_EMPTY_BODY));
            logger.info("Found transaction with TRANSACTION-ID: {}", transaction.getTransactionId());

            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(response));

            return SpiResponse.<SpiTransaction>builder()
                           .payload(transaction)
                           .build();
        } catch (FeignException feignException) {
            String devMessage = feignExceptionReader.getErrorMessage(feignException);
            logger.error("Request transactions for account by transaction id failed: consent ID {}, resource ID {}, transaction ID {}, devMessage {}", accountConsent.getId(), accountReference.getResourceId(), transactionId, devMessage);
            return SpiResponse.<SpiTransaction>builder()
                           .error(FeignExceptionHandler.getFailureMessage(feignException, SpiMessageErrorCode.RESOURCE_UNKNOWN_403))
                           .build();
        } finally {
            authRequestInterceptor.setAccessToken(null);
        }
    }

    @Override
    public SpiResponse<List<SpiAccountBalance>> requestBalancesForAccount(@NotNull SpiContextData contextData,
                                                                          @NotNull SpiAccountReference accountReference,
                                                                          @NotNull SpiAccountConsent accountConsent,
                                                                          @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        byte[] aspspConsentData = aspspConsentDataProvider.loadAspspConsentData();

        try {
            GlobalScaResponseTO response = applyAuthorisation(aspspConsentData);

            logger.info("Requested Balances for ACCOUNT-ID: {}", accountReference.getResourceId());
            List<SpiAccountBalance> accountBalances = Optional
                                                              .ofNullable(accountRestClient.getBalances(accountReference.getResourceId()).getBody())
                                                              .map(accountMapper::toSpiAccountBalancesList)
                                                              .orElseThrow(() -> FeignExceptionHandler.getException(HttpStatus.NOT_FOUND, RESPONSE_STATUS_200_WITH_EMPTY_BODY));
            logger.info("Found Balances: {}", accountBalances.size());

            aspspConsentDataProvider.updateAspspConsentData(consentDataService.store(response));

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

    @Override
    public SpiResponse<SpiTransactionsDownloadResponse> requestTransactionsByDownloadLink(@NotNull SpiContextData spiContextData,
                                                                                          @NotNull SpiAccountConsent spiAccountConsent,
                                                                                          @NotNull String downloadId,
                                                                                          @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {

        logger.info("Requested downloading list of transactions by download ID: {}", downloadId);
        try {
            Resource resource = fileManagementService.getFileByDownloadLink(downloadId);
            if (!resource.isReadable()) {
                logger.info("Reading transactions file is failed, file is not readable yet: consent ID: [{}], filename: [{}]", spiAccountConsent.getId(), resource.getFilename());
                return getErrorResponse("File is not ready, try again later", SpiMessageErrorCode.RESOURCE_BLOCKED);
            }

            byte[] bytes = readResourceInputStreamBytes(resource);
            int byteLength = bytes.length;

            if (byteLength == 0) {
                logger.error("Reading transactions input stream failed, file is empty: consent ID {}, filename {}", spiAccountConsent.getId(), resource.getFilename());
                return getErrorResponse("Nothing to download, file is empty", SpiMessageErrorCode.RESOURCE_UNKNOWN_404);
            }

            logger.info("Consent ID {}, Decrypted Download id: [{}], Bytes read: [{}]", spiAccountConsent.getId(), downloadId, byteLength);

            SpiTransactionsDownloadResponse transactionsDownloadResponse =
                    new SpiTransactionsDownloadResponse(new ByteArrayInputStream(bytes), resource.getFilename(), byteLength);

            fileManagementService.deleteFileByDownloadLink(downloadId);
            return SpiResponse.<SpiTransactionsDownloadResponse>builder()
                           .payload(transactionsDownloadResponse)
                           .build();
        } catch (FileManagementException fileManagementException) {
            logger.error("Reading transactions file has failed (FileManagementException): consent ID: [{}], message: [{}]", spiAccountConsent.getId(), fileManagementException.getMessage());
            return getErrorResponse(fileManagementException.getMessage(), SpiMessageErrorCode.RESOURCE_UNKNOWN_404);
        }
    }

    private byte[] readResourceInputStreamBytes(Resource resource) throws FileManagementException {
        try (InputStream inputStream = resource.getInputStream()) {
            return inputStream.readAllBytes();
        } catch (IOException ex) {
            logger.error("Unable to read resource input stream bytes, Message: {}", ex.getMessage());
            throw new FileManagementException("File not found or corrupted(IOException). Message:" + ex.getMessage());
        }
    }

    private SpiResponse<SpiTransactionsDownloadResponse> getErrorResponse(@Nullable String message, SpiMessageErrorCode errorCode) {
        SpiTppMessage tppMessage = new SpiTppMessage(errorCode, message);
        return SpiResponse.<SpiTransactionsDownloadResponse>builder()
                       .error(tppMessage)
                       .build();
    }

    private List<SpiAccountDetails> getSpiAccountDetails(boolean withBalance, @NotNull SpiAccountConsent accountConsent) {
        List<SpiAccountDetails> accountDetailsList;
        if (isGlobalConsent(accountConsent.getAccess()) || isAllAvailableAccountsConsent(accountConsent)) {
            logger.info("Consent with ID: {} is a global or available account Consent", accountConsent.getId());
            accountDetailsList = getAccountDetailsByConsentId();
        } else {
            logger.info("Consent with ID: {} is a regular consent", accountConsent.getId());
            accountDetailsList = getAccountDetailsFromReferences(withBalance, accountConsent);
        }
        return accountDetailsList;
    }

    private List<SpiAccountBalance> getSpiAccountBalances(@NotNull SpiContextData contextData,
                                                          boolean withBalance,
                                                          @NotNull SpiAccountReference accountReference,
                                                          @NotNull SpiAccountConsent accountConsent,
                                                          @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        if (withBalance) {
            SpiResponse<List<SpiAccountBalance>> response = requestBalancesForAccount(contextData, accountReference,
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
        return accountConsent.getAisConsentRequestType() == SpiAisConsentRequestType.ALL_AVAILABLE_ACCOUNTS;
    }

    private List<SpiAccountDetails> getAccountDetailsByConsentId() {
        return Optional.ofNullable(accountRestClient.getListOfAccounts().getBody())
                       .map(l -> l.stream().map(accountMapper::toSpiAccountDetails)
                                         .collect(Collectors.toList()))
                       .orElseGet(Collections::emptyList);
    }

    private List<SpiAccountDetails> getAccountDetailsFromReferences(boolean withBalance,
                                                                    SpiAccountConsent accountConsent) {
        SpiAccountAccess accountAccess = accountConsent.getAccess();
        List<SpiAccountReference> references = withBalance ? accountAccess.getBalances() : accountAccess.getAccounts();

        return getAccountDetailsFromReferences(references);
    }

    private List<SpiAccountDetails> getAccountDetailsFromReferences(List<SpiAccountReference> references) {
        List<AccountDetailsTO> accountDetails = accountRestClient.getListOfAccounts().getBody();

        if (accountDetails == null) {
            return Collections.emptyList();
        }

        return accountDetails.stream()
                       .filter(account -> containsAccountReferenceWithIban(references, account.getIban(), account.getCurrency()))
                       .map(accountMapper::toSpiAccountDetails)
                       .collect(Collectors.toList());
    }

    private boolean containsAccountReferenceWithIban(List<SpiAccountReference> references, String iban, Currency currency) {
        return references.stream()
                       .filter(reference -> Optional.ofNullable(reference.getIban())
                                                    .orElseGet(() -> ibanResolverMockService.handleIbanByAccountReference(reference)) // TODO: Remove when ledgers starts supporting card accounts https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/1246
                                                    .equals(iban))

                       .anyMatch(reference -> reference.getCurrency() == null || reference.getCurrency().equals(currency));
    }

    private List<SpiAccountDetails> filterAccountDetailsByWithBalance(boolean withBalance, List<SpiAccountDetails> details,
                                                                      SpiAccountAccess spiAccountAccess) {

        if (withBalance && isConsentSupportedBalances(spiAccountAccess)) {
            return details;
        }

        for (SpiAccountDetails spiAccountDetails : details) {
            if (!withBalance || !isValidAccountByAccess(spiAccountDetails.getIban(), spiAccountAccess.getBalances())) {
                spiAccountDetails.emptyBalances();
            }
        }

        return details;
    }

    private boolean isConsentSupportedBalances(SpiAccountAccess spiAccountAccess) {
        boolean isConsentGlobal = spiAccountAccess.getAllPsd2() != null;
        boolean isConsentForAvailableAccountsWithBalances = spiAccountAccess.getAvailableAccountsWithBalance() != null;
        return isConsentGlobal || isConsentForAvailableAccountsWithBalances;
    }

    private GlobalScaResponseTO applyAuthorisation(byte[] aspspConsentData) {
        GlobalScaResponseTO sca = consentDataService.response(aspspConsentData);
        authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());
        return sca;
    }

    private boolean isValidAccountByAccess(String iban, List<SpiAccountReference> allowedAccountData) {
        return CollectionUtils.isNotEmpty(allowedAccountData)
                       && allowedAccountData.stream()
                                  .anyMatch(a -> iban.equals(a.getIban()));
    }

    private SpiTppMessage buildTppMessage(FeignException exception) {
        return FeignExceptionHandler.getFailureMessage(exception, SpiMessageErrorCode.CONSENT_UNKNOWN_400, feignExceptionReader.getErrorMessage(exception));
    }

    private List<SpiTransaction> createStandingOrderReportMock() {
        SpiStandingOrderDetails standingOrderDetails = new SpiStandingOrderDetails(LocalDate.of(2021, Month.JANUARY, 4),
                                                                                   LocalDate.of(2021, Month.MARCH, 12),
                                                                                   SpiPisExecutionRule.PRECEDING, null,
                                                                                   SpiFrequencyCode.MONTHLYVARIABLE, null, null, SpiPisDayOfExecution.DAY_24, null);
        SpiAdditionalInformationStructured additionalInformationStructured = new SpiAdditionalInformationStructured(standingOrderDetails);
        return Collections.singletonList(new SpiTransaction(null, null, null, null, null,
                                                            null, null, null, null, null,
                                                            buildSpiTransactionInfo(), "PMNT-ICDT-STDO",
                                                            null, null, additionalInformationStructured, buildSpiAccountBalance(),
                                                            null, null, null));
    }

    private SpiTransactionInfo buildSpiTransactionInfo() {
        SpiAccountReference spiAccountReference =
                new SpiAccountReference("11111-11118", "10023-999999999", "DE52500105173911841934",
                                        "52500105173911841934", "AEYPM5403H", "PM5403H****",
                                        null, Currency.getInstance("EUR"), null);
        return new SpiTransactionInfo("John Miles", spiAccountReference, null, null,
                                      null, null, null, null,
                                      null, null, null, null, null);
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

    private SpiAccountDetails enrichWithOwnerName(SpiAccountDetails spiAccountDetails, SpiAccountAccess accountAccess) {
        IbanAccountReference ibanAccountReference = new IbanAccountReference(spiAccountDetails.getIban(), spiAccountDetails.getCurrency());
        if (ownerNameService.shouldContainOwnerName(ibanAccountReference, accountAccess)) {
            return ownerNameService.enrichAccountDetailsWithOwnerName(spiAccountDetails);
        }

        return spiAccountDetails;
    }
}
