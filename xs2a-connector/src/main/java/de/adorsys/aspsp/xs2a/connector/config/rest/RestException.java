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
