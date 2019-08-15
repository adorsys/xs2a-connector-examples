package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.psd2.xs2a.core.pis.PisDayOfExecution;
import de.adorsys.psd2.xs2a.core.pis.PisExecutionRule;
import org.junit.Test;

import static org.junit.Assert.*;

public class LedgersSpiPaymentMapperHelperTest {

    @Test
    public void mapPisDayOfExecution() {
        assertEquals(1, LedgersSpiPaymentMapperHelper.mapPisDayOfExecution(null));
        for (PisDayOfExecution execution : PisDayOfExecution.values()) {
            assertEquals(Integer.parseInt(execution.getValue()), LedgersSpiPaymentMapperHelper.mapPisDayOfExecution(execution));
        }
    }

    @Test
    public void mapPisExecutionRule() {
        assertNull(LedgersSpiPaymentMapperHelper.mapPisExecutionRule(null));
        for (PisExecutionRule executionRule : PisExecutionRule.values()) {
            assertEquals(executionRule.getValue(), LedgersSpiPaymentMapperHelper.mapPisExecutionRule(executionRule));
        }
    }
}