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

package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.ledgers.middleware.api.domain.payment.FrequencyCodeTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTO;
import de.adorsys.ledgers.middleware.client.mappers.PaymentMapperTO;
import de.adorsys.psd2.mapper.Xs2aObjectMapper;
import de.adorsys.psd2.model.ExecutionRule;
import de.adorsys.psd2.model.PeriodicPaymentInitiationXmlPart2StandingorderTypeJson;
import de.adorsys.psd2.xs2a.core.profile.PaymentType;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPaymentInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LedgersSpiCommonPaymentTOMapper {
    private final StandardPaymentProductsResolverConnector standardPaymentProductsResolverConnector;
    private final LedgersSpiPaymentToMapper ledgersSpiPaymentToMapper;
    private final PaymentMapperTO paymentMapperTO;
    private final Xs2aObjectMapper xs2aObjectMapper;

    public PaymentTO mapToPaymentTO(PaymentType paymentType, SpiPaymentInfo spiPaymentInfo) {
        if (standardPaymentProductsResolverConnector.isRawPaymentProduct(spiPaymentInfo.getPaymentProduct())) {
            PaymentTO paymentTO = paymentMapperTO.toAbstractPayment(new String(spiPaymentInfo.getPaymentData()),
                                                                    paymentType.name(),
                                                                    spiPaymentInfo.getPaymentProduct());
            paymentTO.setPaymentId(spiPaymentInfo.getPaymentId());
            if (PaymentType.PERIODIC == paymentType) {
                enrichPeriodicPaymentFields(paymentTO, spiPaymentInfo);
            }
            return paymentTO;
        } else {
            switch (paymentType) {
                case SINGLE:
                    return ledgersSpiPaymentToMapper.toPaymentTO_Single(spiPaymentInfo);
                case BULK:
                    return ledgersSpiPaymentToMapper.toPaymentTO_Bulk(spiPaymentInfo);
                case PERIODIC:
                    return ledgersSpiPaymentToMapper.toPaymentTO_Periodic(spiPaymentInfo);
                default:
                    throw new IllegalArgumentException(String.format("Unknown payment type: %s", paymentType.getValue()));
            }
        }
    }

    private PaymentTO enrichPeriodicPaymentFields(PaymentTO paymentTO, SpiPaymentInfo spiPaymentInfo) {
        String paymentData = new String(spiPaymentInfo.getPaymentData());
        try {
            String json = paymentData.substring(paymentData.indexOf("{"), paymentData.lastIndexOf("}") + 1);
            PeriodicPaymentInitiationXmlPart2StandingorderTypeJson periodicTypeJson = xs2aObjectMapper.readValue(json, PeriodicPaymentInitiationXmlPart2StandingorderTypeJson.class);
            paymentTO.setStartDate(periodicTypeJson.getStartDate());
            paymentTO.setEndDate(periodicTypeJson.getEndDate());
            paymentTO.setDayOfExecution(Optional.ofNullable(periodicTypeJson.getDayOfExecution())
                                                .map(d -> Integer.parseInt(d.toString()))
                                                .orElse(null));
            paymentTO.setExecutionRule(Optional.ofNullable(periodicTypeJson.getExecutionRule())
                                               .map(ExecutionRule::toString)
                                               .orElse(null));
            paymentTO.setFrequency(
                    Optional.ofNullable(periodicTypeJson.getFrequency())
                            .map(f -> FrequencyCodeTO.valueOf(f.name()))
                            .orElse(null));
        } catch (Exception e) {
            log.debug("Wrong multipart/form-data content for raw periodic payment: {}", paymentData);
            throw new IllegalArgumentException("Wrong multipart/form-data content for raw periodic payment");
        }
        return paymentTO;
    }
}
