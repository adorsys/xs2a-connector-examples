package de.adorsys.aspsp.xs2a.spi.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.Test;
import org.mapstruct.factory.Mappers;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapper;
import de.adorsys.ledgers.middleware.api.domain.account.FundsConfirmationRequestTO;
import de.adorsys.psd2.xs2a.spi.domain.fund.SpiFundsConfirmationRequest;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;

public class LedgersSpiAccountMapperTest {
    private LedgersSpiAccountMapper accountMapper = Mappers.getMapper(LedgersSpiAccountMapper.class);
    private YamlMapper yamlMapper = new YamlMapper(LedgersSpiAccountMapperTest.class);
    @Test
    public void toFundsConfirmationTO() throws JsonParseException, JsonMappingException, IOException {
        SpiPsuData spiPsuData = yamlMapper.readYml(SpiPsuData.class, "SpiPsuData.yml");
        SpiFundsConfirmationRequest spiFundsConfirmationRequest = yamlMapper.readYml(SpiFundsConfirmationRequest.class, "SpiFundsConfirmation.yml");
        FundsConfirmationRequestTO result = accountMapper.toFundsConfirmationTO(spiPsuData,spiFundsConfirmationRequest);

        assertThat(result).isNotNull();
        assertThat(result).isEqualToComparingFieldByFieldRecursively(yamlMapper.readYml(FundsConfirmationRequestTO.class, "FundsConfirmationRequestTO.yml"));
    }
    
}
