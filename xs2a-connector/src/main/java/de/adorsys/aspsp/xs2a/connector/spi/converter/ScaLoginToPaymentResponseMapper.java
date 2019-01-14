package de.adorsys.aspsp.xs2a.connector.spi.converter;

import org.mapstruct.Mapper;

import de.adorsys.ledgers.middleware.api.domain.sca.SCALoginResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;

@Mapper(componentModel = "spring")
public interface ScaLoginToPaymentResponseMapper {
	SCAPaymentResponseTO toPaymentResponse(SCALoginResponseTO loginResponse);
}
