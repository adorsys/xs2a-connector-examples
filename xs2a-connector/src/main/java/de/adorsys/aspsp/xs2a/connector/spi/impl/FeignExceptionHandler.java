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

package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.psd2.xs2a.spi.domain.error.SpiMessageErrorCode;
import de.adorsys.psd2.xs2a.spi.domain.error.SpiTppMessage;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.nio.charset.Charset;
import java.util.Collections;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

public class FeignExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(FeignExceptionHandler.class);

    private FeignExceptionHandler() {
    }

    public static SpiTppMessage getFailureMessage(FeignException e, SpiMessageErrorCode errorCode) {
        logger.error(e.getMessage(), e);

        if (e.getCause() instanceof ConnectException) {
            throw new ResourceAccessException(e.getMessage());
        }

        switch (HttpStatus.valueOf(e.status())) {
            case INTERNAL_SERVER_ERROR:
                return new SpiTppMessage(SpiMessageErrorCode.INTERNAL_SERVER_ERROR);
            case UNAUTHORIZED:
                return new SpiTppMessage(SpiMessageErrorCode.PSU_CREDENTIALS_INVALID);
            default:
                return new SpiTppMessage(errorCode);
        }
    }

    public static SpiTppMessage getFailureMessage(FeignException e, SpiMessageErrorCode errorCode, String errorMessageAspsp) {
        return shouldUseNormalErrorMessage(e, errorCode, errorMessageAspsp)
                       ? getFailureMessage(e, errorCode)
                       : new SpiTppMessage(errorCode, errorMessageAspsp);
    }

    private static boolean shouldUseNormalErrorMessage(FeignException e, SpiMessageErrorCode errorCode, String errorMessageAspsp) {
        return StringUtils.isBlank(errorMessageAspsp) ||
                       HttpStatus.valueOf(e.status()) == BAD_REQUEST && errorCode == SpiMessageErrorCode.PAYMENT_FAILED;
    }

    public static FeignException getException(HttpStatus httpStatus, String message) {
        return FeignException.errorStatus(message, error(httpStatus));
    }

    static Response error(HttpStatus httpStatus) {
        return Response.builder()
                       .status(httpStatus.value())
                       .request(Request.create(Request.HttpMethod.GET, "", Collections.emptyMap(), null, Charset.defaultCharset(), null))
                       .headers(Collections.emptyMap())
                       .build();
    }
}
