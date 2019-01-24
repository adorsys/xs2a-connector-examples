package de.adorsys.aspsp.xs2a.connector.spi.converter;

import org.mapstruct.Mapper;

import de.adorsys.ledgers.middleware.api.domain.sca.SCAConsentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCALoginResponseTO;

@Mapper(componentModel = "spring")
public interface ScaLoginToConsentResponseMapper {
	SCAConsentResponseTO toConsentResponse(SCALoginResponseTO loginResponse);
}
