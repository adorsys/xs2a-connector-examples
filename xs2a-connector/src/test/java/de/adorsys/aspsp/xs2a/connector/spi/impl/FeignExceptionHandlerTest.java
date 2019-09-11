package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import feign.FeignException;
import org.junit.Test;
import org.springframework.http.HttpStatus;

import static org.junit.Assert.assertEquals;

public class FeignExceptionHandlerTest {

    @Test
    public void getFailureMessage_internalServerError() {
        FeignException feignException = FeignException.errorStatus("message1", FeignExceptionHandler.error(HttpStatus.INTERNAL_SERVER_ERROR));
        TppMessage tppMessage = FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.FORMAT_ERROR, "message2");

        assertEquals(MessageErrorCode.INTERNAL_SERVER_ERROR, tppMessage.getErrorCode());
        assertEquals(FeignExceptionHandler.REQUEST_WAS_FAILED_MESSAGE, tppMessage.getMessageText());
    }

    @Test
    public void getFailureMessage_otherErrors() {
        FeignException feignException = FeignException.errorStatus("message1", FeignExceptionHandler.error(HttpStatus.BAD_REQUEST));
        TppMessage tppMessage = FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.FORMAT_ERROR, "message2");

        assertEquals(MessageErrorCode.FORMAT_ERROR, tppMessage.getErrorCode());
        assertEquals("message2", tppMessage.getMessageText());
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
        String errorMessageAspsp = "aspsp";
        String errorMessageConnector = "connector";
        MessageErrorCode messageErrorCode = MessageErrorCode.PAYMENT_FAILED;
        FeignException feignException = FeignException.errorStatus("message1", FeignExceptionHandler.error(HttpStatus.BAD_REQUEST));
        TppMessage tppMessage = FeignExceptionHandler.getFailureMessage(feignException, messageErrorCode, errorMessageAspsp, errorMessageConnector);

        assertEquals(messageErrorCode, tppMessage.getErrorCode());
        assertEquals(errorMessageConnector, tppMessage.getMessageText());
    }

    @Test
    public void getFailureMessage_AspspMessageNull_MessageFromConnector() {
        String errorMessageAspsp = null;
        String errorMessageConnector = "connector";
        MessageErrorCode messageErrorCode = MessageErrorCode.PAYMENT_FAILED;
        FeignException feignException = FeignException.errorStatus("message1", FeignExceptionHandler.error(HttpStatus.NOT_FOUND));
        TppMessage tppMessage = FeignExceptionHandler.getFailureMessage(feignException, messageErrorCode, errorMessageAspsp, errorMessageConnector);

        assertEquals(messageErrorCode, tppMessage.getErrorCode());
        assertEquals(errorMessageConnector, tppMessage.getMessageText());
    }

    @Test
    public void getFailureMessage_MessageFromAspsp() {
        String errorMessageAspsp = "aspsp";
        String errorMessageConnector = "connector";
        MessageErrorCode messageErrorCode = MessageErrorCode.PAYMENT_FAILED;
        FeignException feignException = FeignException.errorStatus("message1", FeignExceptionHandler.error(HttpStatus.NOT_FOUND));
        TppMessage tppMessage = FeignExceptionHandler.getFailureMessage(feignException, messageErrorCode, errorMessageAspsp, errorMessageConnector);

        assertEquals(messageErrorCode, tppMessage.getErrorCode());
        assertEquals(errorMessageAspsp, tppMessage.getMessageText());
    }

    @Test
    public void getFailureMessage_unauthorized() {
        String errorMessage = "message2";
        FeignException feignException = FeignException.errorStatus("message1", FeignExceptionHandler.error(HttpStatus.UNAUTHORIZED));
        TppMessage tppMessage = FeignExceptionHandler.getFailureMessage(feignException, MessageErrorCode.FORMAT_ERROR, errorMessage);

        assertEquals(MessageErrorCode.PSU_CREDENTIALS_INVALID, tppMessage.getErrorCode());
        assertEquals(errorMessage, tppMessage.getMessageText());
    }
}