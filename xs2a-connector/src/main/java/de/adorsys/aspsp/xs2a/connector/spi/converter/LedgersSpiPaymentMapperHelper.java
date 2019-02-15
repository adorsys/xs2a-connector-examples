package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.psd2.xs2a.core.pis.PisDayOfExecution;
import de.adorsys.psd2.xs2a.core.pis.PisExecutionRule;

public class LedgersSpiPaymentMapperHelper {


    public static String mapPisExecutionRule(PisExecutionRule rule) {
        return rule == null
                       ? null
                       : rule.getValue();
    }

    public static int mapPisDayOfExecution(PisDayOfExecution day) {
        return day == null
                       ? 1
                       : Integer.parseInt(day.getValue());
    }

}
