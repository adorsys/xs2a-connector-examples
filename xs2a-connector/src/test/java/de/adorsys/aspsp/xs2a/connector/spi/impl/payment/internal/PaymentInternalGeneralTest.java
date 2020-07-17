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

package de.adorsys.aspsp.xs2a.connector.spi.impl.payment.internal;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiCommonPaymentTOMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.psd2.xs2a.core.profile.PaymentType;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPaymentInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentInternalGeneralTest {
    private final static String PAYMENT_PRODUCT = "sepa-credit-transfers";
    private static final byte[] CONSENT_DATA = "consent data".getBytes();

    @InjectMocks
    private PaymentInternalGeneral paymentInternalGeneral;

    @Mock
    private GeneralPaymentService paymentService;
    @Mock
    private LedgersSpiCommonPaymentTOMapper ledgersSpiCommonPaymentTOMapper;

    @Test
    void initiatePaymentInternal() {
        SpiPaymentInfo spiPayment = new SpiPaymentInfo(PAYMENT_PRODUCT);
        spiPayment.setPaymentType(PaymentType.SINGLE);
        PaymentTO paymentTO = new PaymentTO();

        when(ledgersSpiCommonPaymentTOMapper.mapToPaymentTO(PaymentType.SINGLE, spiPayment)).thenReturn(paymentTO);

        paymentInternalGeneral.initiatePaymentInternal(spiPayment, CONSENT_DATA);

        verify(ledgersSpiCommonPaymentTOMapper).mapToPaymentTO(PaymentType.SINGLE, spiPayment);
        verify(paymentService).initiatePaymentInternal(spiPayment, CONSENT_DATA, PaymentTypeTO.SINGLE, paymentTO);
    }
}