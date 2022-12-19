/*
 * Copyright 2018-2021 adorsys GmbH & Co KG
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

package de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation;

import de.adorsys.aspsp.xs2a.connector.spi.impl.SpiMockData;
import de.adorsys.psd2.xs2a.core.profile.ScaApproach;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.core.tpp.TppInfo;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiStartAuthorisationResponse;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.sca.SpiScaApproach;
import de.adorsys.psd2.xs2a.spi.domain.sca.SpiScaStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class AbstractAuthorisationSpiTest {
    private static final String PSU_ID = "anton.brueckner";
    private static final String AUTHORISATION_ID = "authorisation Id";
    private static final ScaApproach DECOUPLED_APPROACH = ScaApproach.DECOUPLED;
    private static final SpiScaApproach SPI_DECOUPLED_APPROACH = SpiScaApproach.DECOUPLED;
    private static final ScaApproach NON_DECOUPLED_APPROACH = ScaApproach.EMBEDDED;
    private static final SpiScaApproach SPI_NON_DECOUPLED_APPROACH = SpiScaApproach.EMBEDDED;
    private static final ScaStatus SCA_STATUS = ScaStatus.PSUAUTHENTICATED;
    private static final SpiScaStatus SPI_SCA_STATUS = SpiScaStatus.PSUAUTHENTICATED;
    private static final SpiPsuData PSU_ID_DATA_1 = SpiPsuData.builder()
                                                            .psuId(PSU_ID)
                                                            .psuIdType("2")
                                                            .psuCorporateId("3")
                                                            .psuCorporateIdType("4")
                                                            .psuIpAddress("5")
                                                            .psuIpPort("6")
                                                            .psuUserAgent("7")
                                                            .psuGeoLocation("8")
                                                            .psuAccept("9")
                                                            .psuAcceptCharset("10")
                                                            .psuAcceptEncoding("11")
                                                            .psuAcceptLanguage("12")
                                                            .psuHttpMethod("13")
                                                            .psuDeviceId(UUID.randomUUID())
                                                            .build();
    private static final String ACCESS_TOKEN = "access_token";
    private static final SpiContextData SPI_CONTEXT_DATA = new SpiContextData(PSU_ID_DATA_1, new TppInfo(), UUID.randomUUID(), UUID.randomUUID(), ACCESS_TOKEN, null, null, null, null);

    private final AbstractAuthorisationSpi authorisationSpi = Mockito.mock(AbstractAuthorisationSpi.class, Mockito.CALLS_REAL_METHODS);

    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;

    @Test
    void startAuthorisation_decoupledApproach() {
        //Given
        String expected = SpiMockData.DECOUPLED_PSU_MESSAGE;

        //When
        SpiResponse<SpiStartAuthorisationResponse> actual =
                authorisationSpi.startAuthorisation(SPI_CONTEXT_DATA, SPI_DECOUPLED_APPROACH, SPI_SCA_STATUS, AUTHORISATION_ID, null, spiAspspConsentDataProvider);

        //Then
        assertNotNull(actual.getPayload());
        assertEquals(expected, actual.getPayload().getPsuMessage());
    }

    @Test
    void startAuthorisation_nonDecoupledApproach() {
        //Given
        String expected = SpiMockData.PSU_MESSAGE_START_AUTHORISATION;

        //When
        SpiResponse<SpiStartAuthorisationResponse> actual =
                authorisationSpi.startAuthorisation(SPI_CONTEXT_DATA, SPI_NON_DECOUPLED_APPROACH, SPI_SCA_STATUS, AUTHORISATION_ID, null, spiAspspConsentDataProvider);

        //Then
        assertNotNull(actual.getPayload());
        assertEquals(expected, actual.getPayload().getPsuMessage());
    }
}
