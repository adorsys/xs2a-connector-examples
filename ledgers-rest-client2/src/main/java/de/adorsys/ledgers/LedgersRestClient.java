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


import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import de.adorsys.ledgers.domain.PaymentType;
import de.adorsys.ledgers.domain.SCAValidationRequest;
import de.adorsys.ledgers.domain.TransactionStatus;
import de.adorsys.ledgers.domain.payment.BulkPaymentTO;
import de.adorsys.ledgers.domain.payment.PaymentCancellationResponseTO;
import de.adorsys.ledgers.domain.payment.PaymentProductTO;
import de.adorsys.ledgers.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.domain.payment.PeriodicPaymentTO;
import de.adorsys.ledgers.domain.payment.SinglePaymentTO;
import de.adorsys.ledgers.domain.sca.AuthCodeDataTO;
import de.adorsys.ledgers.domain.sca.SCAGenerationResponse;
import de.adorsys.ledgers.domain.sca.SCAMethodTO;
import de.adorsys.ledgers.domain.um.BearerTokenTO;

@FeignClient(value = "ledgers", url = "${ledgers.url}")
public interface LedgersRestClient {
    String AUTH_TOKEN = "Authorization";

    @RequestMapping(value = "/payments/execute-no-sca/{paymentId}/{paymentProduct}/{paymentType}", method = RequestMethod.POST)
    ResponseEntity<TransactionStatus> executePaymentNoSca(
    		@RequestHeader(AUTH_TOKEN) String accessTokenHeader,
            @PathVariable(name = "paymentId") String paymentId,
            @PathVariable(name = "paymentProduct") PaymentProductTO paymentProduct,
            @PathVariable(name = "paymentType") PaymentTypeTO paymentType
    );

    @RequestMapping(value = "/payments/{paymentType}", method = RequestMethod.POST)
    ResponseEntity<SinglePaymentTO> initiateSinglePayment(
    		@RequestHeader(AUTH_TOKEN) String accessTokenHeader,
    		@PathVariable(name = "paymentType") PaymentType paymentType, @RequestBody SinglePaymentTO payment);

    @RequestMapping(value = "/payments/{paymentType}", method = RequestMethod.POST)
    ResponseEntity<PeriodicPaymentTO> initiatePeriodicPayment(
    		@RequestHeader(AUTH_TOKEN) String accessTokenHeader,
    		@PathVariable(name = "paymentType") PaymentType paymentType, @RequestBody PeriodicPaymentTO payment);

    @RequestMapping(value = "/payments/{paymentType}", method = RequestMethod.POST)
    ResponseEntity<BulkPaymentTO> initiateBulkPayment(
    		@RequestHeader(AUTH_TOKEN) String accessTokenHeader,
    		@PathVariable(name = "paymentType") PaymentType paymentType, @RequestBody BulkPaymentTO payment);

    @RequestMapping(value = "/payments/{payment-type}/{payment-product}/{paymentId}")
    ResponseEntity<SinglePaymentTO> getSinglePaymentPaymentById(
    		@RequestHeader(AUTH_TOKEN) String accessTokenHeader,
    		@PathVariable(name = "payment-type") PaymentTypeTO paymentType,
    		@PathVariable(name = "payment-product") PaymentProductTO paymentProduct,
            @PathVariable(name = "paymentId") String paymentId);

    @RequestMapping(value = "/payments/{payment-type}/{payment-product}/{paymentId}")
    ResponseEntity<PeriodicPaymentTO> getPeriodicPaymentPaymentById(
    		@RequestHeader(AUTH_TOKEN) String accessTokenHeader,
    		@PathVariable(name = "payment-type") PaymentTypeTO paymentType,
    		@PathVariable(name = "payment-product") PaymentProductTO paymentProduct,
    		@PathVariable(name = "paymentId") String paymentId);

    @RequestMapping(value = "/payments/{payment-type}/{payment-product}/{paymentId}")
    ResponseEntity<BulkPaymentTO> getBulkPaymentPaymentById(
    		@RequestHeader(AUTH_TOKEN) String accessTokenHeader,
    		@PathVariable(name = "payment-type") PaymentTypeTO paymentType,
    		@PathVariable(name = "payment-product") PaymentProductTO paymentProduct,
            @PathVariable(name = "paymentId") String paymentId);

    @RequestMapping(value = "/payments/{paymentId}/status", method = RequestMethod.GET)
    ResponseEntity<TransactionStatus> getPaymentStatusById(
    		@RequestHeader(AUTH_TOKEN) String accessTokenHeader,
    		@PathVariable("paymentId") String id);

    @RequestMapping(value = "/auth-codes/{opId}/validate", method = RequestMethod.POST)
    ResponseEntity<Boolean> validate(
    		@RequestHeader(AUTH_TOKEN) String accessTokenHeader,
    		@PathVariable("opId") String opId, @RequestBody SCAValidationRequest request);

    @RequestMapping(value = "/auth-codes/generate", method = RequestMethod.POST)
    ResponseEntity<SCAGenerationResponse> generate(
    		@RequestHeader(AUTH_TOKEN) String accessTokenHeader,
    		@RequestBody AuthCodeDataTO data);

    @RequestMapping(value = "/users/authorise", method = RequestMethod.POST)
    ResponseEntity<BearerTokenTO> authorise(@RequestParam("login") String login, @RequestParam("pin") String pin, @RequestParam("role") String role);
    
    @RequestMapping(value = "/sca-methods/{userLogin}", method = RequestMethod.GET)
    List<SCAMethodTO> getUserScaMethods(
    		@RequestHeader(AUTH_TOKEN) String accessTokenHeader,
    		@PathVariable("userLogin") String userLogin);

    @RequestMapping(value = "/payments/cancel/{paymentId}", method = RequestMethod.DELETE)
    ResponseEntity<Void> cancelPaymentNoSca(
    		@RequestHeader(AUTH_TOKEN) String accessTokenHeader,
    		@PathVariable("paymentId") String paymentId);

    @RequestMapping(value = "/payments/cancel-initiation/{psuId}/{paymentId}", method = RequestMethod.POST)
    ResponseEntity<PaymentCancellationResponseTO> initiatePmtCancellation(
    		@RequestHeader(AUTH_TOKEN) String accessTokenHeader,
    		@PathVariable("psuId") String psuId, @PathVariable("paymentId") String paymentId);
    
    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    ResponseEntity<BearerTokenTO> validateToken(@RequestParam("accessToken")String token);

    @PostMapping(path="/{payment-id}/auth", params= {"authCode", "opId"})
    public ResponseEntity<BearerTokenTO> authorizePayment(@PathVariable(name = "payment-id") String paymentId,
    		@RequestParam(name="authCode") String authCode,
    		@RequestParam(name="opId") String opId);    
}
