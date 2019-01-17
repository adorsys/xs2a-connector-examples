package de.adorsys.aspsp.xs2a.connector.spi.converter;

import org.mapstruct.Mapper;

import de.adorsys.ledgers.middleware.api.domain.um.AisConsentTO;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.consent.SpiAccountAccess;

@Mapper(componentModel = "spring")
public abstract class AisConsentMapper {
	
	public abstract AisConsentTO toTo(SpiAccountConsent consent);

	public abstract SpiAccountAccess toSpi(SpiAccountConsent accountConsent);

	protected String mapSpiAccountReferenceToString(SpiAccountReference s)
	{
		return s.getIban();
	}
}
