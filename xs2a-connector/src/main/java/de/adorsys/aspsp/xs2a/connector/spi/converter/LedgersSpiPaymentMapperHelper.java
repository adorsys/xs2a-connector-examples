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
