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

package de.adorsys.aspsp.xs2a.connector.spi.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapper;
import de.adorsys.ledgers.middleware.api.domain.account.AccountDetailsTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAResponseTO;
import de.adorsys.ledgers.rest.client.AccountRestClient;
import de.adorsys.ledgers.rest.client.AuthRequestInterceptor;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountBalance;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountDetails;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiTransaction;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiTransactionReport;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiAccountAccess;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.AccountSpi;
import feign.FeignException;
import feign.Response;

@Component
public class AccountSpiImpl implements AccountSpi {
	private static final Logger logger = LoggerFactory.getLogger(AccountSpiImpl.class);

	private final AccountRestClient accountRestClient;
	private final LedgersSpiAccountMapper accountMapper;
	private final AuthRequestInterceptor authRequestInterceptor;
	private final AspspConsentDataService tokenService;

	public AccountSpiImpl(AccountRestClient restClient, LedgersSpiAccountMapper accountMapper,
			AuthRequestInterceptor authRequestInterceptor, AspspConsentDataService tokenService) {
		super();
		this.accountRestClient = restClient;
		this.accountMapper = accountMapper;
		this.authRequestInterceptor = authRequestInterceptor;
		this.tokenService = tokenService;
	}

	@Override
	public SpiResponse<List<SpiAccountDetails>> requestAccountList(@NotNull SpiContextData contextData,
			boolean withBalance, @NotNull SpiAccountConsent accountConsent,
			@NotNull AspspConsentData aspspConsentData) {
		try {
			auth(aspspConsentData);

			logger.info("Requested Details list for consent with id: {} and withBalance : {}", accountConsent.getId(),
					withBalance);
			List<SpiAccountDetails> accountDetailsList = getSpiAccountDetails(withBalance, accountConsent,
					aspspConsentData);
			logger.info("Details for consent are: {}", accountDetailsList);
			return SpiResponse.<List<SpiAccountDetails>>builder()
					.payload(filterAccountDetailsByWithBalance(withBalance, accountDetailsList))
					.aspspConsentData(aspspConsentData).success();
		} catch (FeignException e) {
			logger.error(e.getMessage());
			return SpiResponse.<List<SpiAccountDetails>>builder().aspspConsentData(aspspConsentData)
					.fail(getSpiResponseStatus(e));
		} finally {
			authRequestInterceptor.setAccessToken(null);
		}
	}

	@Override
	public SpiResponse<SpiAccountDetails> requestAccountDetailForAccount(@NotNull SpiContextData contextData,
			boolean withBalance, @NotNull SpiAccountReference accountReference,
			@NotNull SpiAccountConsent accountConsent, @NotNull AspspConsentData aspspConsentData) {
		try {
			auth(aspspConsentData);

			logger.info("Requested details for account with id: {}, and withBalances: {}",
					accountReference.getResourceId(), withBalance);
			SpiAccountDetails accountDetails = Optional
					.ofNullable(accountRestClient.getAccountDetailsById(accountReference.getResourceId()).getBody())
					.map(accountMapper::toSpiAccountDetails)
					.orElseThrow(() -> FeignException.errorStatus("Response status was 200, but the body was empty!",
							Response.builder().status(404).build()));
			if (!withBalance) {
				accountDetails.emptyBalances();
			}
			logger.info("The responded details are: {}", accountDetails);
			return SpiResponse.<SpiAccountDetails>builder().payload(accountDetails).aspspConsentData(aspspConsentData)
					.success();
		} catch (FeignException e) {
			logger.error(e.getMessage());
			return SpiResponse.<SpiAccountDetails>builder().fail(getSpiResponseStatus(e));
		} finally {
			authRequestInterceptor.setAccessToken(null);
		}
	}

	@Override
	public SpiResponse<SpiTransactionReport> requestTransactionsForAccount(@NotNull SpiContextData contextData,
			String acceptMediaType, boolean withBalance, @NotNull LocalDate dateFrom, @NotNull LocalDate dateTo,
			@NotNull SpiAccountReference accountReference, @NotNull SpiAccountConsent accountConsent,
			@NotNull AspspConsentData aspspConsentData) {
		try {
			auth(aspspConsentData);

			logger.info("Requested transactions for account with is: {},  dates from: {}, to: {}, withBalance: {}",
					accountReference.getResourceId(), dateFrom, dateTo, withBalance);
			List<SpiTransaction> transactions = Optional.ofNullable(
					accountRestClient.getTransactionByDates(accountReference.getResourceId(), dateFrom, dateTo).getBody())
					.map(accountMapper::toSpiTransactions).orElseGet(ArrayList::new);
			logger.info("Transactions are: {}", transactions);
			List<SpiAccountBalance> balances = getSpiAccountBalances(contextData, withBalance, accountReference,
					accountConsent, aspspConsentData);

			// TODO: Check what is to be done here. We can return a json array with those
			// transactions.
			SpiTransactionReport transactionReport = new SpiTransactionReport(transactions, balances, acceptMediaType,
					null);
			logger.info("Final transaction report is: {}", transactionReport);
			return SpiResponse.<SpiTransactionReport>builder().payload(transactionReport)
					.aspspConsentData(aspspConsentData).success();
		} catch (FeignException e) {
			logger.error(e.getMessage());
			return SpiResponse.<SpiTransactionReport>builder()
					.aspspConsentData(aspspConsentData)
					.fail(getSpiResponseStatus(e));
		} finally {
			authRequestInterceptor.setAccessToken(null);
		}
	}

	@Override
	public SpiResponse<SpiTransaction> requestTransactionForAccountByTransactionId(@NotNull SpiContextData contextData,
			@NotNull String transactionId, @NotNull SpiAccountReference accountReference,
			@NotNull SpiAccountConsent accountConsent, @NotNull AspspConsentData aspspConsentData) {

		try {
			auth(aspspConsentData);

			logger.info("Requested transaction id is: {}, for account with id: {}", transactionId,
					accountReference.getResourceId());
			SpiTransaction transaction = Optional
					.ofNullable(
							accountRestClient.getTransactionById(accountReference.getResourceId(), transactionId).getBody())
					.map(accountMapper::toSpiTransaction)
					.orElseThrow(() -> FeignException.errorStatus("Response status was 200, but the body was empty!",
							Response.builder().status(404).build()));
			logger.info("Transaction is: {}", transaction);
			return SpiResponse.<SpiTransaction>builder().payload(transaction).aspspConsentData(aspspConsentData)
					.success();
		} catch (FeignException e) {
			logger.error(e.getMessage());
			return SpiResponse.<SpiTransaction>builder().aspspConsentData(aspspConsentData).fail(getSpiResponseStatus(e));
		} finally {
			authRequestInterceptor.setAccessToken(null);
		}
	}

	@Override
	public SpiResponse<List<SpiAccountBalance>> requestBalancesForAccount(@NotNull SpiContextData contextData,
			@NotNull SpiAccountReference accountReference, @NotNull SpiAccountConsent accountConsent,
			@NotNull AspspConsentData aspspConsentData) {
		try {
			auth(aspspConsentData);

			logger.info("Requested Balances for account with is: {}", accountReference.getResourceId());
			List<SpiAccountBalance> accountBalances = Optional
					.ofNullable(accountRestClient.getBalances(accountReference.getResourceId()).getBody())
					.map(accountMapper::toSpiAccountBalancesList)
					.orElseThrow(() -> FeignException.errorStatus("Response status was 200, but the body was empty!",
							Response.builder().status(404).build()));
			logger.info("Balances received are: {}", accountBalances);
			return SpiResponse.<List<SpiAccountBalance>>builder().payload(accountBalances)
					.aspspConsentData(aspspConsentData).success();
		} catch (FeignException e) {
			logger.error(e.getMessage());
			return SpiResponse.<List<SpiAccountBalance>>builder().fail(getSpiResponseStatus(e));
		} finally {
			authRequestInterceptor.setAccessToken(null);
		}
	}

	private List<SpiAccountDetails> getSpiAccountDetails(boolean withBalance, @NotNull SpiAccountConsent accountConsent,
			AspspConsentData aspspConsentData) {
		List<SpiAccountDetails> accountDetailsList;
		if (isBankOfferedConsent(accountConsent.getAccess())) {
			logger.info("Consent with id: {} is a Bank Offered Consent", accountConsent.getId());
			accountDetailsList = getAccountDetailsByConsentId(aspspConsentData);
		} else {
			logger.info("Consent with id: {} is a regular consent", accountConsent.getId());
			accountDetailsList = getAccountDetailsFromReferences(withBalance, accountConsent, aspspConsentData);
		}
		return accountDetailsList;
	}

	private List<SpiAccountBalance> getSpiAccountBalances(@NotNull SpiContextData contextData, boolean withBalance,
			@NotNull SpiAccountReference accountReference, @NotNull SpiAccountConsent accountConsent,
			@NotNull AspspConsentData aspspConsentData) {
		if (withBalance) {
			SpiResponse<List<SpiAccountBalance>> response = requestBalancesForAccount(contextData, accountReference,
					accountConsent, aspspConsentData);
			if (response.isSuccessful()) {
				return response.getPayload();
			} else {
				throw FeignException.errorStatus("Requested transaction can`t be found",
						Response.builder().status(404).build());
			}
		} else {
			return null;
		}
	}

	private boolean isBankOfferedConsent(SpiAccountAccess accountAccess) {
		return CollectionUtils.isEmpty(accountAccess.getBalances())
				&& CollectionUtils.isEmpty(accountAccess.getTransactions())
				&& CollectionUtils.isEmpty(accountAccess.getAccounts());
	}

	private List<SpiAccountDetails> getAccountDetailsByConsentId(
			AspspConsentData aspspConsentData) {
		try {
			auth(aspspConsentData);
			
			return Optional.ofNullable(accountRestClient.getListOfAccounts().getBody())
					.map(l -> l.stream().map(accountMapper::toSpiAccountDetails).collect(Collectors.toList()))
					.orElseGet(Collections::emptyList);
		} finally {
			authRequestInterceptor.setAccessToken(null);
		}
	}

	private List<SpiAccountDetails> getAccountDetailsFromReferences(boolean withBalance,
			SpiAccountConsent accountConsent, AspspConsentData aspspConsentData) { // TODO remove consentId param, when
																					// SpiAccountConsent contains it
		// https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/430
		SpiAccountAccess accountAccess = accountConsent.getAccess();
		List<SpiAccountReference> references = withBalance ? accountAccess.getBalances() : accountAccess.getAccounts();

		return getAccountDetailsFromReferences(references, aspspConsentData);
	}

	private List<SpiAccountDetails> getAccountDetailsFromReferences(List<SpiAccountReference> references,
			AspspConsentData aspspConsentData) {
		return references.stream().map(r -> getAccountDetailsByAccountReference(r, aspspConsentData))
				.filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());
	}

	private Optional<SpiAccountDetails> getAccountDetailsByAccountReference(SpiAccountReference reference,
			AspspConsentData aspspConsentData) {
		if (reference == null) {
			return Optional.empty();
		}

		try {
			auth(aspspConsentData);
			
			// TODO don't use IBAN as an account identifier
			// https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/440
			AccountDetailsTO response = accountRestClient.getAccountDetailsByIban(reference.getIban()).getBody();
	
			// TODO don't use currency as an account identifier
			// https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/440
			return Optional.ofNullable(response).map(accountMapper::toSpiAccountDetails);
		} finally {
			authRequestInterceptor.setAccessToken(null);
		}
	}

	private List<SpiAccountDetails> filterAccountDetailsByWithBalance(boolean withBalance,
			List<SpiAccountDetails> details) {
		if (!withBalance) {
			details.forEach(SpiAccountDetails::emptyBalances);
		}
		return details;
	}

	private SpiResponseStatus getSpiResponseStatus(FeignException e) {
		logger.error(e.getMessage());
		return e.status() == 500 ? SpiResponseStatus.TECHNICAL_FAILURE : SpiResponseStatus.LOGICAL_FAILURE;
	}
	
	private SCAResponseTO auth(AspspConsentData aspspConsentData) {
		SCAResponseTO sca = tokenService.response(aspspConsentData);
		authRequestInterceptor.setAccessToken(sca.getBearerToken().getAccess_token());
		return sca;
	}
}
