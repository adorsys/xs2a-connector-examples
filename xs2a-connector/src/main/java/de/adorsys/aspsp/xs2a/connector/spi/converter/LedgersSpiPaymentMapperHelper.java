package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.psd2.xs2a.core.pis.PisDayOfExecution;
import de.adorsys.psd2.xs2a.core.pis.PisExecutionRule;

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
}
