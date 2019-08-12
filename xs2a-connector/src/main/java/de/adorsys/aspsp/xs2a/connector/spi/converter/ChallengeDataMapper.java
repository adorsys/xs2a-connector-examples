package de.adorsys.aspsp.xs2a.connector.spi.converter;

import org.mapstruct.Mapper;

import de.adorsys.ledgers.middleware.api.domain.sca.ChallengeDataTO;
import de.adorsys.psd2.xs2a.core.sca.ChallengeData;
import org.mapstruct.Mapping;

import java.util.Collections;

@Mapper(componentModel = "spring", imports = {Collections.class})
public interface ChallengeDataMapper {

	@Mapping(target = "data", expression = "java(Collections.emptyList())")
	ChallengeData toChallengeData(ChallengeDataTO to);
}
