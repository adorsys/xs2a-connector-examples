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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeignExceptionHandlerTest {

    @Test
    void getFailureMessage_internalServerError() {
        FeignException feignException = FeignException.errorStatus("message1", FeignExceptionHandler.error(HttpStatus.INTERNAL_SERVER_ERROR));
        SpiTppMessage tppMessage = FeignExceptionHandler.getFailureMessage(feignException, SpiMessageErrorCode.FORMAT_ERROR);

        assertEquals(SpiMessageErrorCode.INTERNAL_SERVER_ERROR, tppMessage.getErrorCode());
        assertEquals("", tppMessage.getMessageText());
    }

    @Test
    void getFailureMessage_otherErrors() {
        FeignException feignException = FeignException.errorStatus("message1", FeignExceptionHandler.error(HttpStatus.BAD_REQUEST));
        SpiTppMessage tppMessage = FeignExceptionHandler.getFailureMessage(feignException, SpiMessageErrorCode.FORMAT_ERROR);

        assertEquals(SpiMessageErrorCode.FORMAT_ERROR, tppMessage.getErrorCode());
        assertEquals("", tppMessage.getMessageText());
    }

    @Test
    void getException() {
        FeignException feignException = FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message1");
        assertEquals(HttpStatus.BAD_REQUEST.value(), feignException.status());
        assertEquals("[400] during [GET] to [] [message1]: []", feignException.getMessage());

        feignException = FeignExceptionHandler.getException(HttpStatus.NOT_FOUND, "message2");
        assertEquals(HttpStatus.NOT_FOUND.value(), feignException.status());
        assertEquals("[404] during [GET] to [] [message2]: []", feignException.getMessage());

        feignException = FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, "message3");
        assertEquals(HttpStatus.UNAUTHORIZED.value(), feignException.status());
        assertEquals("[401] during [GET] to [] [message3]: []", feignException.getMessage());
    }

    @Test
    void getFailureMessage_MessageFromConnector() {
        SpiMessageErrorCode messageErrorCode = SpiMessageErrorCode.PAYMENT_FAILED;
        FeignException feignException = FeignException.errorStatus("message1", FeignExceptionHandler.error(HttpStatus.BAD_REQUEST));
        SpiTppMessage tppMessage = FeignExceptionHandler.getFailureMessage(feignException, messageErrorCode);

        assertEquals(messageErrorCode, tppMessage.getErrorCode());
        assertEquals("", tppMessage.getMessageText());
    }

    @Test
    void getFailureMessage_AspspMessageNull_MessageFromConnector() {
        SpiMessageErrorCode messageErrorCode = SpiMessageErrorCode.PAYMENT_FAILED;
        FeignException feignException = FeignException.errorStatus("message1", FeignExceptionHandler.error(HttpStatus.NOT_FOUND));
        SpiTppMessage tppMessage = FeignExceptionHandler.getFailureMessage(feignException, messageErrorCode);

        assertEquals(messageErrorCode, tppMessage.getErrorCode());
        assertEquals("", tppMessage.getMessageText());
    }

    @Test
    void getFailureMessage_MessageFromAspsp() {
        SpiMessageErrorCode messageErrorCode = SpiMessageErrorCode.PAYMENT_FAILED;
        FeignException feignException = FeignException.errorStatus("message1", FeignExceptionHandler.error(HttpStatus.NOT_FOUND));
        SpiTppMessage tppMessage = FeignExceptionHandler.getFailureMessage(feignException, messageErrorCode);

        assertEquals(messageErrorCode, tppMessage.getErrorCode());
        assertEquals("", tppMessage.getMessageText());
    }

    @Test
    void getFailureMessage_unauthorized() {
        FeignException feignException = FeignException.errorStatus("message1", FeignExceptionHandler.error(HttpStatus.UNAUTHORIZED));
        SpiTppMessage tppMessage = FeignExceptionHandler.getFailureMessage(feignException, SpiMessageErrorCode.FORMAT_ERROR);

        assertEquals(SpiMessageErrorCode.PSU_CREDENTIALS_INVALID, tppMessage.getErrorCode());
        assertEquals("", tppMessage.getMessageText());
    }

    @Test
    void getFailureMessage_resourceAccessException() {
        FeignException feignException = FeignException.errorStatus("message1", FeignExceptionHandler.error(HttpStatus.INTERNAL_SERVER_ERROR));
        feignException.initCause(new ConnectException("Connection refused"));

        assertThrows(ResourceAccessException.class, () -> FeignExceptionHandler.getFailureMessage(feignException, SpiMessageErrorCode.INTERNAL_SERVER_ERROR));
    }
}