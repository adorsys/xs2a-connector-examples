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

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapper;
import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiPaymentMapper;
import de.adorsys.aspsp.xs2a.connector.spi.impl.AspspConsentDataService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.SpiMockData;
import de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation.confirmation.PaymentAuthConfirmationCodeService;
import de.adorsys.aspsp.xs2a.connector.spi.impl.payment.GeneralPaymentService;
import de.adorsys.aspsp.xs2a.util.TestSpiDataProvider;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.psd2.xs2a.core.pis.FrequencyCode;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaConfirmation;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiGetPaymentStatusResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPaymentExecutionResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPeriodicPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class})
class PeriodicPaymentSpiImplTest {
    private final static String PAYMENT_PRODUCT = "sepa-credit-transfers";
    private static final SpiContextData SPI_CONTEXT_DATA = TestSpiDataProvider.getSpiContextData();
    private static final String PAYMENT_ID = "c966f143-f6a2-41db-9036-8abaeeef3af7";
    private static final byte[] CONSENT_DATA_BYTES = "consent_data".getBytes();
    private static final String JSON_ACCEPT_MEDIA_TYPE = "application/json";
    private static final String PSU_MESSAGE = "Mocked PSU message from SPI for this payment";

    @InjectMocks
    private PeriodicPaymentSpiImpl periodicPaymentSpi;
    @Mock
    private GeneralPaymentService paymentService;
    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;
    @Mock
    private AspspConsentDataService aspspConsentDataService;
    @Mock
    private PaymentAuthConfirmationCodeService paymentAuthConfirmationCodeService;

    @Spy
    private LedgersSpiPaymentMapper paymentMapper = new LedgersSpiPaymentMapper(Mappers.getMapper(LedgersSpiAccountMapper.class));
    private SpiPeriodicPayment payment;

    @BeforeEach
    void setUp() {
        payment = new SpiPeriodicPayment(PAYMENT_PRODUCT);
        payment.setPaymentId(PAYMENT_ID);
        payment.setPaymentStatus(TransactionStatus.RCVD);
        payment.setFrequency(FrequencyCode.MONTHLY);
    }

    @Test
    void getPaymentById() {
        when(paymentService.getPaymentById(eq(payment), eq(spiAspspConsentDataProvider),
                                           any()))
                .thenReturn(SpiResponse.<SpiPeriodicPayment>builder()
                                    .payload(new SpiPeriodicPayment(PAYMENT_PRODUCT))
                                    .build());

        periodicPaymentSpi.getPaymentById(SPI_CONTEXT_DATA, JSON_ACCEPT_MEDIA_TYPE, payment, spiAspspConsentDataProvider);

        verify(paymentService, times(1)).getPaymentById(eq(payment),
                                                        eq(spiAspspConsentDataProvider),
                                                        any());
    }

    @Test
    void getPaymentStatusById() {
        when(spiAspspConsentDataProvider.loadAspspConsentData()).thenReturn(CONSENT_DATA_BYTES);
        when(paymentService.getPaymentStatusById(PaymentTypeTO.PERIODIC, JSON_ACCEPT_MEDIA_TYPE, PAYMENT_ID, TransactionStatus.RCVD, CONSENT_DATA_BYTES))
                .thenReturn(SpiResponse.<SpiGetPaymentStatusResponse>builder()
                                    .payload(new SpiGetPaymentStatusResponse(TransactionStatus.RCVD, false, SpiGetPaymentStatusResponse.RESPONSE_TYPE_JSON, null, PSU_MESSAGE,
                                                                             SpiMockData.SPI_LINKS,
                                                                             SpiMockData.TPP_MESSAGES))
                                    .build());

        periodicPaymentSpi.getPaymentStatusById(SPI_CONTEXT_DATA, JSON_ACCEPT_MEDIA_TYPE, payment, spiAspspConsentDataProvider);

        verify(spiAspspConsentDataProvider, times(1)).loadAspspConsentData();
        verify(paymentService, times(1)).getPaymentStatusById(PaymentTypeTO.PERIODIC, JSON_ACCEPT_MEDIA_TYPE, PAYMENT_ID, TransactionStatus.RCVD, CONSENT_DATA_BYTES);
    }

    @Test
    void executePaymentWithoutSca() {
        when(paymentService.executePaymentWithoutSca(spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPaymentExecutionResponse>builder()
                                    .payload(new SpiPaymentExecutionResponse(TransactionStatus.RCVD))
                                    .build());

        periodicPaymentSpi.executePaymentWithoutSca(SPI_CONTEXT_DATA, payment, spiAspspConsentDataProvider);

        verify(paymentService, times(1)).executePaymentWithoutSca(spiAspspConsentDataProvider);
    }

    @Test
    void verifyScaAuthorisationAndExecutePayment() {
        SpiScaConfirmation spiScaConfirmation = new SpiScaConfirmation();
        when(paymentService.verifyScaAuthorisationAndExecutePaymentWithPaymentResponse(spiScaConfirmation, spiAspspConsentDataProvider))
                .thenReturn(SpiResponse.<SpiPaymentExecutionResponse>builder()
                                    .payload(new SpiPaymentExecutionResponse(TransactionStatus.RCVD))
                                    .build());

        periodicPaymentSpi.verifyScaAuthorisationAndExecutePaymentWithPaymentResponse(SPI_CONTEXT_DATA, spiScaConfirmation, payment, spiAspspConsentDataProvider);

        verify(paymentService).verifyScaAuthorisationAndExecutePaymentWithPaymentResponse(spiScaConfirmation, spiAspspConsentDataProvider);
    }

    @Test
    void initiatePayment_emptyConsentData() {
        ArgumentCaptor<SpiPeriodicPaymentInitiationResponse> spiPeriodicPaymentInitiationResponseCaptor
                = ArgumentCaptor.forClass(SpiPeriodicPaymentInitiationResponse.class);

        when(paymentService.firstCallInstantiatingPayment(eq(PaymentTypeTO.PERIODIC), eq(payment),
                                                          eq(spiAspspConsentDataProvider), spiPeriodicPaymentInitiationResponseCaptor.capture(), eq(SPI_CONTEXT_DATA.getPsuData()), eq(Collections.emptySet())))
                .thenReturn(SpiResponse.<SpiPeriodicPaymentInitiationResponse>builder()
                                    .payload(new SpiPeriodicPaymentInitiationResponse())
                                    .build());

        periodicPaymentSpi.initiatePayment(SPI_CONTEXT_DATA, payment, spiAspspConsentDataProvider);

        verify(paymentService, times(1)).firstCallInstantiatingPayment(eq(PaymentTypeTO.PERIODIC), eq(payment),
                                                                       eq(spiAspspConsentDataProvider), any(SpiPeriodicPaymentInitiationResponse.class), eq(SPI_CONTEXT_DATA.getPsuData()), eq(Collections.emptySet()));
        assertNull(spiPeriodicPaymentInitiationResponseCaptor.getValue().getPaymentId());
    }
}