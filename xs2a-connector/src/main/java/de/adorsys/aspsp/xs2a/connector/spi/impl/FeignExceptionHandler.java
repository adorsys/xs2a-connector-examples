package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.Collections;

class FeignExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(FeignExceptionHandler.class);

    static final String REQUEST_WAS_FAILED_MESSAGE = "Request was failed";

    private FeignExceptionHandler() {
    }

    static TppMessage getFailureMessage(FeignException e, MessageErrorCode errorCode, String errorMessage) {
        logger.error(e.getMessage(), e);
        return HttpStatus.INTERNAL_SERVER_ERROR.value() == e.status()
                       ? new TppMessage(MessageErrorCode.INTERNAL_SERVER_ERROR, REQUEST_WAS_FAILED_MESSAGE)
                       : new TppMessage(errorCode, errorMessage);

    }

    static FeignException getException(HttpStatus httpStatus, String message) {
        return FeignException.errorStatus(message, error(httpStatus));
    }

    static Response error(HttpStatus httpStatus) {
        return Response.builder()
                       .status(httpStatus.value())
                       .request(Request.create(Request.HttpMethod.GET, "", Collections.emptyMap(), null))
                       .headers(Collections.emptyMap())
                       .build();
    }
}
