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


import de.adorsys.ledgers.domain.*;
import de.adorsys.ledgers.domain.sca.SCAGenerationRequest;
import de.adorsys.ledgers.domain.sca.SCAGenerationResponse;
import de.adorsys.ledgers.domain.sca.SCAMethodTO;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(value = "ledgers", url = "${ledgers.url}")
public interface LedgersRestClient {

    @RequestMapping(value = "/payments/execute-no-sca/{paymentId}/{paymentProduct}/{paymentType}", method = RequestMethod.POST)
    ResponseEntity<TransactionStatus> executePaymentNoSca(
            @PathVariable(name = "paymentId") String paymentId,
            @PathVariable(name = "paymentProduct") PaymentProduct paymentProduct,
            @PathVariable(name = "paymentType") PaymentType paymentType
    );

    @RequestMapping(value = "/payments/{paymentType}", method = RequestMethod.POST)
    ResponseEntity<?> initiatePayment(@PathVariable(name = "paymentType") PaymentType paymentType, @RequestBody Object payment);

    @RequestMapping(value = "/payments/{id}/status", method = RequestMethod.GET)
    TransactionStatus getPaymentStatusById(@PathVariable String id);

    @RequestMapping(value = "/auth-codes/{opId}/validate", method = RequestMethod.POST)
    boolean validate(@PathVariable String opId, @RequestBody SCAValidationRequest request);

    @RequestMapping(value = "/auth-codes/generate", method = RequestMethod.POST)
    SCAGenerationResponse generate(@RequestBody SCAGenerationRequest req);

    @RequestMapping(value = "/users/authorise", method = RequestMethod.POST)
    boolean authorise(@RequestParam("login")String login, @RequestParam("pin") String pin);

    @RequestMapping(value = "/sca-methods/{userLogin}", method = RequestMethod.GET)
    List<SCAMethodTO> getUserScaMethods(@PathVariable String userLogin);
}
