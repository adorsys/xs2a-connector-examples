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


import de.adorsys.ledgers.domain.PaymentType;
import de.adorsys.ledgers.domain.SCAValidationRequest;
import de.adorsys.ledgers.domain.TransactionStatus;
import de.adorsys.ledgers.domain.payment.*;
import de.adorsys.ledgers.domain.sca.AuthCodeDataTO;
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
            @PathVariable(name = "paymentProduct") PaymentProductTO paymentProduct,
            @PathVariable(name = "paymentType") PaymentTypeTO paymentType
    );

    @RequestMapping(value = "/payments/{paymentType}", method = RequestMethod.POST)
    ResponseEntity<SinglePaymentTO> initiateSinglePayment(@PathVariable(name = "paymentType") PaymentType paymentType, @RequestBody SinglePaymentTO payment);

    @RequestMapping(value = "/payments/{paymentType}", method = RequestMethod.POST)
    ResponseEntity<PeriodicPaymentTO> initiatePeriodicPayment(@PathVariable(name = "paymentType") PaymentType paymentType, @RequestBody PeriodicPaymentTO payment);

    @RequestMapping(value = "/payments/{paymentType}", method = RequestMethod.POST)
    ResponseEntity<BulkPaymentTO> initiateBulkPayment(@PathVariable(name = "paymentType") PaymentType paymentType, @RequestBody BulkPaymentTO payment);

    @RequestMapping(value = "/payments/{payment-type}/{payment-product}/{paymentId}")
    ResponseEntity<SinglePaymentTO> getSinglePaymentPaymentById(@PathVariable(name = "payment-type") PaymentTypeTO paymentType,
                                                                @PathVariable(name = "payment-product") PaymentProductTO paymentProduct,
                                                                @PathVariable(name = "paymentId") String paymentId);

    @RequestMapping(value = "/payments/{payment-type}/{payment-product}/{paymentId}")
    ResponseEntity<PeriodicPaymentTO> getPeriodicPaymentPaymentById(@PathVariable(name = "payment-type") PaymentTypeTO paymentType,
                                                                    @PathVariable(name = "payment-product") PaymentProductTO paymentProduct,
                                                                    @PathVariable(name = "paymentId") String paymentId);

    @RequestMapping(value = "/payments/{payment-type}/{payment-product}/{paymentId}")
    ResponseEntity<BulkPaymentTO> getBulkPaymentPaymentById(@PathVariable(name = "payment-type") PaymentTypeTO paymentType,
                                                            @PathVariable(name = "payment-product") PaymentProductTO paymentProduct,
                                                            @PathVariable(name = "paymentId") String paymentId);

    @RequestMapping(value = "/payments/{id}/status", method = RequestMethod.GET)
    ResponseEntity<TransactionStatus> getPaymentStatusById(@PathVariable("id") String id);

    @RequestMapping(value = "/auth-codes/{opId}/validate", method = RequestMethod.POST)
    boolean validate(@PathVariable("opId") String opId, @RequestBody SCAValidationRequest request);

    @RequestMapping(value = "/auth-codes/generate", method = RequestMethod.POST)
    SCAGenerationResponse generate(@RequestBody AuthCodeDataTO data);

    @RequestMapping(value = "/users/authorise", method = RequestMethod.POST)
    boolean authorise(@RequestParam("login") String login, @RequestParam("pin") String pin);

    @RequestMapping(value = "/sca-methods/{userLogin}", method = RequestMethod.GET)
    List<SCAMethodTO> getUserScaMethods(@PathVariable("userLogin") String userLogin);

    @RequestMapping(value = "/payments/cancel/{paymentId}", method = RequestMethod.DELETE)
    void cancelPaymentNoSca(@PathVariable("paymentId") String paymentId);

    @RequestMapping(value = "/payments/cancel-initiation/{psuId}/{paymentId}", method = RequestMethod.POST)
    ResponseEntity<PaymentCancellationResponseTO> initiatePmtCancellation(@PathVariable("psuId") String psuId, @PathVariable("paymentId") String paymentId);
}
