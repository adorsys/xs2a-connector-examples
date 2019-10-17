package de.adorsys.aspsp.xs2a.util;

import de.adorsys.psd2.xs2a.core.tpp.TppInfo;
import de.adorsys.psd2.xs2a.spi.domain.SpiContextData;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;

import java.util.UUID;

public class TestSpiDataProvider {

    private static final UUID X_REQUEST_ID = UUID.randomUUID();
    private static final UUID INTERNAL_REQUEST_ID = UUID.randomUUID();
    private static final String AUTHORISATION = "Bearer 1111111";
    private static final String PSU_ID = "psuId";
    private static final String PSU_ID_TYPE = "psuIdType";
    private static final String PSU_CORPORATE_ID = "psuCorporateId";
    private static final String PSU_CORPORATE_ID_TYPE = "psuCorporateIdType";
    private static final String PSU_IP_ADDRESS = "psuIpAddress";

    public static SpiContextData getSpiContextData() {
        return new SpiContextData(
                new SpiPsuData(PSU_ID, PSU_ID_TYPE, PSU_CORPORATE_ID, PSU_CORPORATE_ID_TYPE, PSU_IP_ADDRESS),
                new TppInfo(),
                X_REQUEST_ID,
                INTERNAL_REQUEST_ID,
                AUTHORISATION
        );
    }

}
