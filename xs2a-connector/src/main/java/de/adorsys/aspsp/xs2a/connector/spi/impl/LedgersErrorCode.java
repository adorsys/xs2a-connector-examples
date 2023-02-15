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

import java.util.Optional;

public enum LedgersErrorCode {
    REQUEST_VALIDATION_FAILURE,
    INSUFFICIENT_FUNDS,
    SCA_VALIDATION_ATTEMPT_FAILED,
    PSU_AUTH_ATTEMPT_INVALID;

    public static Optional<LedgersErrorCode> getFromString(String errorCode) {
        try {
            return Optional.of(LedgersErrorCode.valueOf(errorCode));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
