/*
 * Copyright 2018-2019 adorsys GmbH & Co KG
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

package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.type;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PeriodicPaymentTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPeriodicPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.PeriodicPaymentSpi;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
public class PeriodicPaymentSpiImpl extends AbstractPaymentSpi<SpiPeriodicPayment, SpiPeriodicPaymentInitiationResponse> implements PeriodicPaymentSpi {

    private final LedgersSpiPaymentMapper paymentMapper;
    private final GeneralPaymentService paymentService;

    @Autowired
    public PeriodicPaymentSpiImpl(GeneralPaymentService paymentService, LedgersSpiPaymentMapper paymentMapper) {
        super(paymentService);
        this.paymentService = paymentService;
        this.paymentMapper = paymentMapper;
    }

    @Override
    public @NotNull SpiResponse<SpiPeriodicPayment> getPaymentById(@NotNull SpiContextData contextData,
                                                                   @NotNull String acceptMediaType,
                                                                   @NotNull SpiPeriodicPayment payment,
                                                                   @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return paymentService.getPaymentById(payment, aspspConsentDataProvider, PeriodicPaymentTO.class,
                                             paymentMapper::mapToSpiPeriodicPayment, PaymentTypeTO.PERIODIC);
    }

    @Override
    protected SpiResponse<SpiPeriodicPaymentInitiationResponse> processEmptyAspspConsentData(@NotNull SpiPeriodicPayment payment,
                                                                                             @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider,
                                                                                             @NotNull SpiPsuData spiPsuData) {
        Set<SpiAccountReference> spiAccountReferences = new HashSet<>(Collections.singleton(payment.getDebtorAccount()));
        return paymentService.firstCallInstantiatingPayment(PaymentTypeTO.PERIODIC, payment, aspspConsentDataProvider, new SpiPeriodicPaymentInitiationResponse(), spiPsuData, spiAccountReferences);
    }

    @NotNull
    @Override
    protected SpiPeriodicPaymentInitiationResponse getToSpiPaymentResponse(SCAPaymentResponseTO response) {
        return paymentMapper.toSpiPeriodicResponse(response);
    }

}
