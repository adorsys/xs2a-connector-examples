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

package de.adorsys.ledgers.rest.client;

import de.adorsys.psd2.consent.api.pis.CmsPayment;
import de.adorsys.psd2.consent.api.pis.CmsPaymentResponse;
import de.adorsys.psd2.consent.api.pis.CreatePisCommonPaymentResponse;
import de.adorsys.psd2.xs2a.core.psu.PsuIdData;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(value = "cmsPsuPis", url = "${cms.url}", path = "/psu-api/v1/payment", primary = false, configuration = FeignConfig.class)
public interface CmsPsuPisClient {
    @PutMapping(path = "/authorisation/{authorisation-id}/psu-data")
    ResponseEntity<CreatePisCommonPaymentResponse> updatePsuInPayment(
            @PathVariable("authorisation-id") String authorisationId,
            @RequestHeader(value = "instance-id", required = false) String instanceId,
            @RequestBody PsuIdData psuIdData);

    @GetMapping(path = "/{payment-id}")
    ResponseEntity<CmsPayment> getPaymentByPaymentId(
            @RequestHeader(value = "psu-id", required = false) String psuId,
            @RequestHeader(value = "psu-id-type", required = false) String psuIdType,
            @RequestHeader(value = "psu-corporate-id", required = false) String psuCorporateId,
            @RequestHeader(value = "psu-corporate-id-type", required = false) String psuCorporateIdType,
            @PathVariable("payment-id") String paymentId,
            @RequestHeader(value = "instance-id", required = false) String instanceId);

    @GetMapping(path = "/redirect/{redirect-id}")
    ResponseEntity<CmsPaymentResponse> getPaymentByRedirectId(
            @RequestHeader(value = "psu-id", required = false) String psuId,
            @RequestHeader(value = "psu-id-type", required = false) String psuIdType,
            @RequestHeader(value = "psu-corporate-id", required = false) String psuCorporateId,
            @RequestHeader(value = "psu-corporate-id-type", required = false) String psuCorporateIdType,
            @PathVariable("redirect-id") String redirectId,
            @RequestHeader(value = "instance-id", required = false) String instanceId);

    @GetMapping(path = "/cancellation/redirect/{redirect-id}")
    ResponseEntity<CmsPaymentResponse> getPaymentByRedirectIdForCancellation(
            @RequestHeader(value = "psu-id", required = false) String psuId,
            @RequestHeader(value = "psu-id-type", required = false) String psuIdType,
            @RequestHeader(value = "psu-corporate-id", required = false) String psuCorporateId,
            @RequestHeader(value = "psu-corporate-id-type", required = false) String psuCorporateIdType,
            @PathVariable("redirect-id") String redirectId,
            @RequestHeader(value = "instance-id", required = false) String instanceId);

    @PutMapping(path = "/{payment-id}/authorisation/{authorisation-id}/status/{status}")
    ResponseEntity<Void> updateAuthorisationStatus(
            @RequestHeader(value = "psu-id", required = false) String psuId,
            @RequestHeader(value = "psu-id-type", required = false) String psuIdType,
            @RequestHeader(value = "psu-corporate-id", required = false) String psuCorporateId,
            @RequestHeader(value = "psu-corporate-id-type", required = false) String psuCorporateIdType,
            @PathVariable("payment-id") String paymentId,
            @PathVariable("authorisation-id") String authorisationId,
            @PathVariable("status") String status,
            @RequestHeader(value = "instance-id", required = false) String instanceId);

    @PutMapping(path = "/{payment-id}/status/{status}")
    ResponseEntity<Void> updatePaymentStatus(
            @PathVariable("payment-id") String paymentId,
            @PathVariable("status") String status,
            @RequestHeader(value = "instance-id", required = false) String instanceId);
}
