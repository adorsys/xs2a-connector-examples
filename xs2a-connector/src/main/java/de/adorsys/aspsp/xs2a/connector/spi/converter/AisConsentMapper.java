package de.adorsys.aspsp.xs2a.connector.spi.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import de.adorsys.ledgers.middleware.api.domain.um.AisConsentTO;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;

@Mapper(componentModel = "spring")
public abstract class AisConsentMapper {
	
	@Mapping(target="tppId", source="tppInfo.authorisationNumber")
	public abstract AisConsentTO toTo(SpiAccountConsent consent);

	protected String mapSpiAccountReferenceToString(SpiAccountReference s)
	{
		return s.getIban();
	}
}
