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

package de.adorsys.ledgers;


import de.adorsys.ledgers.domain.account.AccountBalanceTO;
import de.adorsys.ledgers.domain.account.AccountDetailsTO;
import de.adorsys.ledgers.domain.account.FundsConfirmationRequestTO;
import de.adorsys.ledgers.domain.account.TransactionTO;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@FeignClient(value = "ledgersAccount", url = "${ledgers.url}")
public interface LedgersAccountRestClient {

    @RequestMapping(value = "/accounts/{accountId}", method = RequestMethod.GET)
    ResponseEntity<AccountDetailsTO> getDetailsByAccountId(@PathVariable(name = "accountId") String accountId);

    @RequestMapping(value = "/accounts/balances/{accountId}", method = RequestMethod.GET)
    ResponseEntity<List<AccountBalanceTO>> getBalancesByAccountId(@PathVariable(name = "accountId") String accountId);

    @RequestMapping(value = "/accounts/{accountId}/transactions/{transactionId}", method = RequestMethod.GET)
    ResponseEntity<TransactionTO> getTransactionById(@PathVariable(name = "accountId") String accountId,
                                                     @PathVariable(name = "transactionId") String transactionId);

    @RequestMapping(value = "/accounts/{accountId}/transactions", method = RequestMethod.GET)
    ResponseEntity<List<TransactionTO>> getTransactionByDates(@PathVariable(name = "accountId") String accountId,
                                                              @RequestParam("dateFrom") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFrom,
                                                              @RequestParam("dateTo") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateTo);

    @RequestMapping(value = "/accounts/users/{userLogin}", method = RequestMethod.GET)
    ResponseEntity<List<AccountDetailsTO>> getAccountDetailsByUserLogin(@PathVariable(name = "userLogin") String userLogin);

    @RequestMapping(value = "/accounts/iban/{iban}", method = RequestMethod.GET)
    ResponseEntity<List<AccountDetailsTO>> getAccountDetailsByIban(@PathVariable(name = "iban") String iban); //TODO - no endpoint at Ledgers!!! Have to solve it later on!

    @RequestMapping(value = "/accounts/funds-confirmation", method = RequestMethod.POST)
    ResponseEntity<Boolean> fundsConfirmation(@RequestBody FundsConfirmationRequestTO request);
}


