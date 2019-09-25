package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.Collections;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

public class FeignExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(FeignExceptionHandler.class);

    private FeignExceptionHandler() {
    }

    static TppMessage getFailureMessage(FeignException e, MessageErrorCode errorCode) {
        logger.error(e.getMessage(), e);

        switch (HttpStatus.valueOf(e.status())) {
            case INTERNAL_SERVER_ERROR:
                return new TppMessage(MessageErrorCode.INTERNAL_SERVER_ERROR);
            case UNAUTHORIZED:
                return new TppMessage(MessageErrorCode.PSU_CREDENTIALS_INVALID);
            default:
                return new TppMessage(errorCode);
        }
    }

    static TppMessage getFailureMessage(FeignException e, MessageErrorCode errorCode, String errorMessageAspsp) {
        return shouldUseNormalErrorMessage(e, errorCode, errorMessageAspsp)
                       ? getFailureMessage(e, errorCode)
                       : getFailureMessage(e, errorCode, errorMessageAspsp);
    }

    private static boolean shouldUseNormalErrorMessage(FeignException e, MessageErrorCode errorCode, String errorMessageAspsp) {
        return StringUtils.isBlank(errorMessageAspsp) ||
                       HttpStatus.valueOf(e.status()) == BAD_REQUEST && errorCode == MessageErrorCode.PAYMENT_FAILED;
    }

    public static FeignException getException(HttpStatus httpStatus, String message) {
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
