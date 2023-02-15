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

package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.ledgers.middleware.api.domain.payment.FrequencyCodeTO;
import de.adorsys.psd2.xs2a.core.pis.FrequencyCode;
import de.adorsys.psd2.xs2a.core.pis.PisDayOfExecution;
import de.adorsys.psd2.xs2a.core.pis.PisExecutionRule;

import java.util.Arrays;

class LedgersSpiPaymentMapperHelper {
    private static final int DEFAULT_DAY_OF_EXECUTION = 1;

    private LedgersSpiPaymentMapperHelper() {
    }

    static String mapPisExecutionRule(PisExecutionRule executionRule) {
        if (executionRule == null) {
            return null;
        }
        return executionRule.getValue();
    }

    static int mapPisDayOfExecution(PisDayOfExecution dayOfExecution) {
        if (dayOfExecution == null) {
            return DEFAULT_DAY_OF_EXECUTION;
        }
        return Integer.parseInt(dayOfExecution.getValue());
    }

    // TODO Remove it https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/1100
    static FrequencyCodeTO mapFrequencyCode(FrequencyCode frequencyCode) {
        if (FrequencyCode.MONTHLYVARIABLE == frequencyCode) {
            return FrequencyCodeTO.MONTHLY;
        }
        return Arrays.stream(FrequencyCodeTO.values())
                       .filter(f -> f.name().equals(frequencyCode.name()))
                       .findFirst().orElse(null);
    }
}
