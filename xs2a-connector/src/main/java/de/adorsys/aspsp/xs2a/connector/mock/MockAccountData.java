/*
 * Copyright 2018-2020 adorsys GmbH & Co KG
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

package de.adorsys.aspsp.xs2a.connector.mock;

import de.adorsys.psd2.xs2a.spi.domain.common.SpiAmount;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiAddress;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Currency;
import java.util.List;

public class MockAccountData {
    public static final SpiAmount CREDIT_LIMIT = new SpiAmount(Currency.getInstance("EUR"), new BigDecimal(10000));
    public static final String MARKUP_FEE_PERCENTAGE = "markupFeePercentage";
    public static final SpiAddress CARD_ACCEPTOR_ADDRESS = new SpiAddress("street", "buildNum", "town", "post", "EU");
    public static final String MERCHANT_CATEGORY_CODE = "merchantCategoryCode";
    public static final String MASKED_PAN = "493702******0836";
    public static final String TRANSACTION_DETAILS = "transactionDetails";
    public static final String TERMINAL_ID = "terminalId";
    public static final OffsetDateTime ACCEPTOR_TRANSACTION_DATE_TIME =
            OffsetDateTime.of(2020, 3, 3, 10, 0, 0, 0, ZoneOffset.UTC);
    public static final String CARD_ACCEPTOR_PHONE = "+61-(02)9999999999-9999";

    // TODO: remove mocked data https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/1241
    public static final String DISPLAY_NAME = "mock display name";
    public static final List<String> REMITTANCE_UNSTRUCTURED_ARRAY = Collections.singletonList("mock remittance unstructured array");
    public static final List<String> REMITTANCE_STRUCTURED_ARRAY = Collections.singletonList("mock remittance reference");
    public static final String ADDITIONAL_INFORMATION = "mock additional information";

    private MockAccountData() {
    }
}
