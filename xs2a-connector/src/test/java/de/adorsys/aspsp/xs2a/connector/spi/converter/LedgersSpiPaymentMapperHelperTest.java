package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.ledgers.middleware.api.domain.payment.FrequencyCodeTO;
import de.adorsys.psd2.xs2a.core.pis.FrequencyCode;
import de.adorsys.psd2.xs2a.core.pis.PisDayOfExecution;
import de.adorsys.psd2.xs2a.core.pis.PisExecutionRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LedgersSpiPaymentMapperHelperTest {

    @Test
    void mapPisDayOfExecution() {
        assertEquals(1, LedgersSpiPaymentMapperHelper.mapPisDayOfExecution(null));
        for (PisDayOfExecution execution : PisDayOfExecution.values()) {
            assertEquals(Integer.parseInt(execution.getValue()), LedgersSpiPaymentMapperHelper.mapPisDayOfExecution(execution));
        }
    }

    @Test
    void mapPisExecutionRule() {
        assertNull(LedgersSpiPaymentMapperHelper.mapPisExecutionRule(null));
        for (PisExecutionRule executionRule : PisExecutionRule.values()) {
            assertEquals(executionRule.getValue(), LedgersSpiPaymentMapperHelper.mapPisExecutionRule(executionRule));
        }
    }

    @Test
    void mapFrequencyCode() {
        assertEquals(FrequencyCodeTO.DAILY, LedgersSpiPaymentMapperHelper.mapFrequencyCode(FrequencyCode.DAILY));
        assertEquals(FrequencyCodeTO.WEEKLY, LedgersSpiPaymentMapperHelper.mapFrequencyCode(FrequencyCode.WEEKLY));
        assertEquals(FrequencyCodeTO.EVERYTWOWEEKS, LedgersSpiPaymentMapperHelper.mapFrequencyCode(FrequencyCode.EVERYTWOWEEKS));
        assertEquals(FrequencyCodeTO.MONTHLY, LedgersSpiPaymentMapperHelper.mapFrequencyCode(FrequencyCode.MONTHLY));
        assertEquals(FrequencyCodeTO.EVERYTWOMONTHS, LedgersSpiPaymentMapperHelper.mapFrequencyCode(FrequencyCode.EVERYTWOMONTHS));
        assertEquals(FrequencyCodeTO.QUARTERLY, LedgersSpiPaymentMapperHelper.mapFrequencyCode(FrequencyCode.QUARTERLY));
        assertEquals(FrequencyCodeTO.SEMIANNUAL, LedgersSpiPaymentMapperHelper.mapFrequencyCode(FrequencyCode.SEMIANNUAL));
        assertEquals(FrequencyCodeTO.ANNUAL, LedgersSpiPaymentMapperHelper.mapFrequencyCode(FrequencyCode.ANNUAL));

        assertEquals(FrequencyCodeTO.MONTHLY, LedgersSpiPaymentMapperHelper.mapFrequencyCode(FrequencyCode.MONTHLYVARIABLE));
    }
}