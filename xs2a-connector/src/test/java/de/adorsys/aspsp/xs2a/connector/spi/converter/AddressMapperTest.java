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
import de.adorsys.ledgers.middleware.api.domain.general.AddressTO;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {AddressMapperImpl.class})
class AddressMapperTest {

    @Autowired
    private AddressMapper mapper;

    private JsonReader jsonReader = new JsonReader();

    @Test
    void toAddressTO_success() {
        SpiAddress spiAddress = jsonReader.getObjectFromFile("json/spi/converter/spi-address.json", SpiAddress.class);
        AddressTO addressTO = mapper.toAddressTO(spiAddress);

        AddressTO expectedAddressTO = jsonReader.getObjectFromFile("json/spi/converter/address-to.json", AddressTO.class);
        assertEquals(expectedAddressTO, addressTO);
    }

    @Test
    void toAddressTO_nullValue() {
        AddressTO addressTO = mapper.toAddressTO(null);
        assertNull(addressTO);
    }
}