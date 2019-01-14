package de.adorsys.aspsp.xs2a.connector.spi.converter;

import org.mapstruct.Mapper;

import de.adorsys.ledgers.middleware.api.domain.sca.ChallengeDataTO;
import de.adorsys.psd2.xs2a.core.sca.ChallengeData;

@Mapper(componentModel = "spring")
public interface ChallengeDataMapper {
	ChallengeData toChallengeData(ChallengeDataTO to);
}
