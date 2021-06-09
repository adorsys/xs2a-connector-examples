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

package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.spi.domain.authorisation.SpiScaStatusResponse;
import org.springframework.stereotype.Component;


@Component
public class SpiScaStatusResponseMapper {

    public SpiScaStatusResponse toSpiScaStatusResponse(GlobalScaResponseTO globalScaResponseTO) {
        if (globalScaResponseTO == null) {
            return null;
        }
        return new SpiScaStatusResponse(ScaStatus.valueOf(globalScaResponseTO.getScaStatus().name()),
                                        false,
                                        globalScaResponseTO.getPsuMessage());
    }
}
