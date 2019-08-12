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
