package de.adorsys.aspsp.xs2a.connector.config.rest;

import java.io.IOException;

import org.springframework.http.HttpStatus;

public class RestException extends IOException {
	private static final long serialVersionUID = 5064440018876021544L;
	private final HttpStatus httpStatus;

    public RestException(HttpStatus httpStatus, String message) {
    	super(message);
        this.httpStatus = httpStatus;
    }

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}
}
