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

package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.type;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation.confirmation.PaymentAuthConfirmationCodeService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.SinglePaymentSpi;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
public class SinglePaymentSpiImpl extends AbstractPaymentSpi<SpiSinglePayment, SpiSinglePaymentInitiationResponse> implements SinglePaymentSpi {
    private LedgersSpiPaymentMapper paymentMapper;

    @Autowired
    public SinglePaymentSpiImpl(GeneralPaymentService paymentService, LedgersSpiPaymentMapper paymentMapper,
                                AspspConsentDataService consentDataService,
                                PaymentAuthConfirmationCodeService paymentAuthConfirmationCodeService) {
        super(paymentService, consentDataService, paymentAuthConfirmationCodeService);
        this.paymentMapper = paymentMapper;
    }

    @Override
    public @NotNull SpiResponse<SpiSinglePayment> getPaymentById(@NotNull SpiContextData contextData,
                                                                 @NotNull String acceptMediaType,
                                                                 @NotNull SpiSinglePayment payment,
                                                                 @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider) {
        return paymentService.getPaymentById(payment, aspspConsentDataProvider, paymentMapper::toSpiSinglePayment);
    }

    @Override
    protected SpiResponse<SpiSinglePaymentInitiationResponse> processEmptyAspspConsentData(@NotNull SpiSinglePayment payment,
                                                                                           @NotNull SpiAspspConsentDataProvider aspspConsentDataProvider,
                                                                                           @NotNull SpiPsuData spiPsuData) {
        Set<SpiAccountReference> spiAccountReferences = payment.getDebtorAccount() == null
                                                                ? Collections.emptySet()
                                                                : new HashSet<>(Collections.singleton(payment.getDebtorAccount()));
        return paymentService.firstCallInstantiatingPayment(PaymentTypeTO.SINGLE, payment, aspspConsentDataProvider, new SpiSinglePaymentInitiationResponse(), spiPsuData, spiAccountReferences);
    }
}
