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

import de.adorsys.ledgers.middleware.api.domain.general.AddressTO;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiAddress;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(componentModel = "spring")
public interface AddressMapper {

    @Mappings({
            @Mapping(target = "city", source = "townName"),
            @Mapping(target = "street", source = "streetName"),
            @Mapping(target = "postalCode", source = "postCode")
    })
    AddressTO toAddressTO(SpiAddress spiAddress);
}
