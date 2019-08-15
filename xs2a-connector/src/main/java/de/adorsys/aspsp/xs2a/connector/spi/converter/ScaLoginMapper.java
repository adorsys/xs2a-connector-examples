package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.ledgers.middleware.api.domain.sca.SCAConsentResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCALoginResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ScaLoginMapper {

    SCAConsentResponseTO toConsentResponse(SCALoginResponseTO loginResponse);

    SCAPaymentResponseTO toPaymentResponse(SCALoginResponseTO loginResponse);
}
