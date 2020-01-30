package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.aspsp.xs2a.connector.spi.impl.IbanResolverMockService;
import de.adorsys.ledgers.middleware.api.domain.um.AisAccountAccessTypeTO;
import de.adorsys.ledgers.middleware.api.domain.um.AisConsentTO;
import de.adorsys.psd2.xs2a.core.ais.AccountAccessType;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountConsent;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

@Mapper(componentModel = "spring")
public abstract class AisConsentMapper {
	@Autowired
	private IbanResolverMockService ibanResolverMockService;

	@Mapping(target="tppId", source="tppInfo.authorisationNumber")
	@Mapping(target="access.availableAccounts", source="access.availableAccounts", qualifiedByName = "mapToAccountAccessType")
	@Mapping(target="access.allPsd2", source="access.allPsd2", qualifiedByName = "mapToAccountAccessType")
	public abstract AisConsentTO mapToAisConsent(SpiAccountConsent consent);

	protected String mapSpiAccountReferenceToString(SpiAccountReference s) {
		return Optional.ofNullable(s.getIban()) // Currently mocked data is used here. https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/1152
				       .orElseGet(() -> ibanResolverMockService.handleIbanByAccountReference(s));
	}

	//TODO Delete this method when Ledgers start support `ALL_ACCOUNTS_WITH_OWNER_NAME` value https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/1126
	public AisAccountAccessTypeTO mapToAccountAccessType(AccountAccessType accountAccessType) {
		if (accountAccessType == null) {
			return null;
		}
		switch (accountAccessType) {
			case ALL_ACCOUNTS :
			case ALL_ACCOUNTS_WITH_OWNER_NAME :
				return AisAccountAccessTypeTO.ALL_ACCOUNTS;
			default:
				throw new IllegalArgumentException(String.format("Unknown account access type: %s", accountAccessType.getDescription()));
		}
	}

}
