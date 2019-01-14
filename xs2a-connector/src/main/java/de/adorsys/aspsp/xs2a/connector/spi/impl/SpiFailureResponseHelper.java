package de.adorsys.aspsp.xs2a.connector.spi.impl;

import org.slf4j.Logger;

import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import feign.FeignException;

public class SpiFailureResponseHelper {

    public static SpiResponseStatus getSpiFailureResponse(FeignException e, Logger logger) {
        logger.error(e.getMessage(), e);
        return e.status() == 500
                       ? SpiResponseStatus.TECHNICAL_FAILURE
                       : SpiResponseStatus.LOGICAL_FAILURE;
    }
}
