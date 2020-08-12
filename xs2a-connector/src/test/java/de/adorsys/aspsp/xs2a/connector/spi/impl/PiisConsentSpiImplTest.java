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

package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.aspsp.xs2a.util.TestSpiDataProvider;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiInitiatePiisConsentResponse;
import de.adorsys.psd2.xs2a.spi.domain.piis.SpiPiisConsent;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PiisConsentSpiImplTest {
    private static final SpiContextData SPI_CONTEXT_DATA = TestSpiDataProvider.getSpiContextData();
    private JsonReader jsonReader = new JsonReader();
    private SpiPiisConsent spiPiisConsent = jsonReader.getObjectFromFile("json/spi/impl/spi-piis-consent.json", SpiPiisConsent.class);

    @InjectMocks
    private PiisConsentSpiImpl piisConsentSpi;

    @Mock
    private MultilevelScaService multilevelScaService;
    @Mock
    private SpiAspspConsentDataProvider spiAspspConsentDataProvider;

    @Test
    void initiatePiisConsent_insufficientPermissionException() {
        //Given
        when(multilevelScaService.isMultilevelScaRequired(SPI_CONTEXT_DATA.getPsuData(), Collections.singleton(spiPiisConsent.getAccount()))).thenThrow(getFeignException());
        //When
        SpiResponse<SpiInitiatePiisConsentResponse> spiInitiatePiisConsentResponseSpiResponse = piisConsentSpi.initiatePiisConsent(SPI_CONTEXT_DATA, spiPiisConsent, spiAspspConsentDataProvider);
        //Then
        assertFalse(spiInitiatePiisConsentResponseSpiResponse.getErrors().isEmpty());
        assertNull(spiInitiatePiisConsentResponseSpiResponse.getPayload());
        assertTrue(spiInitiatePiisConsentResponseSpiResponse.getErrors().contains(new TppMessage(MessageErrorCode.FORMAT_ERROR_UNKNOWN_ACCOUNT)));
    }

    private FeignException getFeignException() {
        return FeignException.errorStatus("User doesn't have access to the requested account",
                                          buildErrorResponseForbidden());
    }

    private Response buildErrorResponseForbidden() {
        return Response.builder()
                       .status(403)
                       .request(Request.create(Request.HttpMethod.GET, "", Collections.emptyMap(), null))
                       .headers(Collections.emptyMap())
                       .build();
    }

}
