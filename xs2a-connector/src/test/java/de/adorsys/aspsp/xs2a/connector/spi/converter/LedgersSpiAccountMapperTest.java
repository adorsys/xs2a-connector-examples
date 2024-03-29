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

package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.account.*;
import de.adorsys.ledgers.middleware.api.domain.payment.AmountTO;
import de.adorsys.psd2.xs2a.spi.domain.account.*;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiAmount;
import de.adorsys.psd2.xs2a.spi.domain.fund.SpiFundsConfirmationRequest;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {LedgersSpiAccountMapperImpl.class})
class LedgersSpiAccountMapperTest {

    @Autowired
    private LedgersSpiAccountMapper ledgersSpiAccountMapper;
    private final JsonReader jsonReader = new JsonReader();

    @Test
    void toFundsConfirmationTOWithRealData() {
        SpiPsuData inputData = jsonReader.getObjectFromFile("json/mappers/spi-psu-data.json", SpiPsuData.class);
        SpiFundsConfirmationRequest inputDataRequest = jsonReader.getObjectFromFile("json/mappers/spi-funds-confirmation-request.json", SpiFundsConfirmationRequest.class);
        FundsConfirmationRequestTO actualResult = ledgersSpiAccountMapper.toFundsConfirmationTO(inputData, inputDataRequest);
        FundsConfirmationRequestTO expectedResult = jsonReader.getObjectFromFile("json/mappers/funds-confirmation-request-to.json", FundsConfirmationRequestTO.class);
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void toFundsConfirmationTOWithNull() {
        FundsConfirmationRequestTO actualResult = ledgersSpiAccountMapper.toFundsConfirmationTO(null, null);
        assertNull(actualResult);
    }

    @Test
    void toSpiAccountDetailsListWithRealData() {
        AccountDetailsTO inputData = jsonReader.getObjectFromFile("json/mappers/account-details-to.json", AccountDetailsTO.class);
        SpiAccountDetails actualResult = ledgersSpiAccountMapper.toSpiAccountDetails(inputData);
        SpiAccountDetails expectedResult = jsonReader.getObjectFromFile("json/mappers/spi-account-details.json", SpiAccountDetails.class);
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void toSpiAccountDetailsListWithNull() {
        SpiAccountDetails actualResult = ledgersSpiAccountMapper.toSpiAccountDetails(null);
        assertNull(actualResult);
    }

    @Test
    void toSpiTransactionWithRealData() {
        TransactionTO inputData = jsonReader.getObjectFromFile("json/mappers/transaction-to.json", TransactionTO.class);
        SpiTransaction actualResult = ledgersSpiAccountMapper.toSpiTransaction(inputData);
        SpiTransaction expectedResult = jsonReader.getObjectFromFile("json/mappers/spi-transaction.json", SpiTransaction.class);
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void toSpiTransactionWithNull() {
        SpiTransaction actualResult = ledgersSpiAccountMapper.toSpiTransaction(null);
        assertNull(actualResult);
    }

    @Test
    void toSpiAccountReferenceWithRealData() {
        AccountReferenceTO inputData = jsonReader.getObjectFromFile("json/mappers/account-reference-to.json", AccountReferenceTO.class);
        SpiAccountReference actualResult = ledgersSpiAccountMapper.toSpiAccountReference(inputData);
        SpiAccountReference expectedResult = jsonReader.getObjectFromFile("json/mappers/spi-account-reference.json", SpiAccountReference.class);
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void toSpiAccountReferenceWithNull() {
        SpiAccountReference actualResult = ledgersSpiAccountMapper.toSpiAccountReference(null);
        assertNull(actualResult);
    }

    @Test
    void toSpiExchangeRateWithRealData() {
        ExchangeRateTO inputData = jsonReader.getObjectFromFile("json/mappers/exchange-rate-to.json", ExchangeRateTO.class);
        SpiExchangeRate actualResult = ledgersSpiAccountMapper.toSpiExchangeRate(inputData);
        SpiExchangeRate expectedResult = jsonReader.getObjectFromFile("json/mappers/spi-exchange-rate.json", SpiExchangeRate.class);
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void toSpiExchangeRateWithNull() {
        SpiExchangeRate actualResult = ledgersSpiAccountMapper.toSpiExchangeRate(null);
        assertNull(actualResult);
    }

    @Test
    void toSpiAmountWithRealData() {
        AmountTO inputData = jsonReader.getObjectFromFile("json/mappers/amount-to.json", AmountTO.class);
        SpiAmount actualResult = ledgersSpiAccountMapper.toSpiAmount(inputData);
        SpiAmount expectedResult = jsonReader.getObjectFromFile("json/mappers/spi-amount.json", SpiAmount.class);
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void toSpiAmountWithNull() {
        SpiAmount actualResult = ledgersSpiAccountMapper.toSpiAmount(null);
        assertNull(actualResult);
    }

    @Test
    void toSpiCardTransaction() {
        TransactionTO inputData = jsonReader.getObjectFromFile("json/mappers/transaction-to.json", TransactionTO.class);
        SpiCardTransaction actualResult = ledgersSpiAccountMapper.toSpiCardTransaction(inputData);
        SpiCardTransaction expectedResult = jsonReader.getObjectFromFile("json/mappers/spi-card-transaction.json", SpiCardTransaction.class);
        assertEquals(expectedResult, actualResult);
    }
}