package de.adorsys.aspsp.xs2a.spi.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.Test;
import org.mapstruct.factory.Mappers;

import de.adorsys.ledgers.middleware.api.domain.account.FundsConfirmationRequestTO;
import de.adorsys.psd2.xs2a.spi.domain.fund.SpiFundsConfirmationRequest;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import pro.javatar.commons.reader.YamlReader;

public class LedgersSpiAccountMapperTest {
    private LedgersSpiAccountMapper accountMapper = Mappers.getMapper(LedgersSpiAccountMapper.class);

    @Test
    public void toFundsConfirmationTO() {
        SpiPsuData spiPsuData = readYml(SpiPsuData.class, "SpiPsuData.yml");
        SpiFundsConfirmationRequest spiFundsConfirmationRequest = readYml(SpiFundsConfirmationRequest.class, "SpiFundsConfirmation.yml");
        FundsConfirmationRequestTO result = accountMapper.toFundsConfirmationTO(spiPsuData,spiFundsConfirmationRequest);

        assertThat(result).isNotNull();
        assertThat(result).isEqualToComparingFieldByFieldRecursively(readYml(FundsConfirmationRequestTO.class,"FundsConfirmationRequestTO.yml"));
    }

    private static <T> T readYml(Class<T> aClass, String file) {
        try {
            return YamlReader.getInstance().getObjectFromResource(LedgersSpiAccountMapper.class, file, aClass);
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalStateException("Resource file not found", e);
        }
    }
}
