package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import feign.FeignException;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import java.net.ConnectException;

import static org.junit.Assert.assertEquals;

public class FeignExceptionHandlerTest {

    @Test
    public void getFailureMessage_internalServerError() {
        FeignException feignException = FeignException.errorStatus("message1", FeignExceptionHandler.error(HttpStatus.INTERNAL_SERVER_ERROR));
        TppMessage tppMessage = FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.FORMAT_ERROR);

        assertEquals(MessageErrorCode.INTERNAL_SERVER_ERROR, tppMessage.getErrorCode());
        assertEquals("", tppMessage.getMessageText());
    }

    @Test
    public void getFailureMessage_otherErrors() {
        FeignException feignException = FeignException.errorStatus("message1", FeignExceptionHandler.error(HttpStatus.BAD_REQUEST));
        TppMessage tppMessage = FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.FORMAT_ERROR);

        assertEquals(MessageErrorCode.FORMAT_ERROR, tppMessage.getErrorCode());
        assertEquals("", tppMessage.getMessageText());
    }

    @Test
    public void getException() {
        FeignException feignException = FeignExceptionHandler.getException(HttpStatus.BAD_REQUEST, "message1");
        assertEquals(HttpStatus.BAD_REQUEST.value(), feignException.status());
        assertEquals("status 400 reading message1", feignException.getMessage());

        feignException = FeignExceptionHandler.getException(HttpStatus.NOT_FOUND, "message2");
        assertEquals(HttpStatus.NOT_FOUND.value(), feignException.status());
        assertEquals("status 404 reading message2", feignException.getMessage());

        feignException = FeignExceptionHandler.getException(HttpStatus.UNAUTHORIZED, "message3");
        assertEquals(HttpStatus.UNAUTHORIZED.value(), feignException.status());
        assertEquals("status 401 reading message3", feignException.getMessage());
    }

    @Test
    public void getFailureMessage_MessageFromConnector() {
        MessageErrorCode messageErrorCode = MessageErrorCode.PAYMENT_FAILED;
        FeignException feignException = FeignException.errorStatus("message1", FeignExceptionHandler.error(HttpStatus.BAD_REQUEST));
        TppMessage tppMessage = FeignExceptionHandler.getFailureMessage(feignException, messageErrorCode);

        assertEquals(messageErrorCode, tppMessage.getErrorCode());
        assertEquals("", tppMessage.getMessageText());
    }

    @Test
    public void getFailureMessage_AspspMessageNull_MessageFromConnector() {
        MessageErrorCode messageErrorCode = MessageErrorCode.PAYMENT_FAILED;
        FeignException feignException = FeignException.errorStatus("message1", FeignExceptionHandler.error(HttpStatus.NOT_FOUND));
        TppMessage tppMessage = FeignExceptionHandler.getFailureMessage(feignException, messageErrorCode);

        assertEquals(messageErrorCode, tppMessage.getErrorCode());
        assertEquals("", tppMessage.getMessageText());
    }

    @Test
    public void getFailureMessage_MessageFromAspsp() {
        MessageErrorCode messageErrorCode = MessageErrorCode.PAYMENT_FAILED;
        FeignException feignException = FeignException.errorStatus("message1", FeignExceptionHandler.error(HttpStatus.NOT_FOUND));
        TppMessage tppMessage = FeignExceptionHandler.getFailureMessage(feignException, messageErrorCode);

        assertEquals(messageErrorCode, tppMessage.getErrorCode());
        assertEquals("", tppMessage.getMessageText());
    }

    @Test
    public void getFailureMessage_unauthorized() {
        FeignException feignException = FeignException.errorStatus("message1", FeignExceptionHandler.error(HttpStatus.UNAUTHORIZED));
        TppMessage tppMessage = FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.FORMAT_ERROR);

        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, tppMessage.getErrorCode());
        assertEquals("", tppMessage.getMessageText());
    }

    @Test(expected = ResourceAccessException.class)
    public void getFailureMessage_resourceAccessException() {
        FeignException feignException = FeignException.errorStatus("message1", FeignExceptionHandler.error(HttpStatus.INTERNAL_SERVER_ERROR));
        feignException.initCause(new ConnectException("Connection refused"));

        FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.INTERNAL_SERVER_ERROR);
    }
}