/*
 * Copyright 2018-2019 adorsys GmbH & Co KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.adorsys.aspsp.xs2a.connector.spi.impl;

import de.adorsys.aspsp.xs2a.connector.spi.converter.LedgersSpiAccountMapper;
import de.adorsys.aspsp.xs2a.util.JsonReader;
import de.adorsys.ledgers.middleware.api.domain.account.AccountReferenceTO;
import de.adorsys.ledgers.rest.client.UserMgmtRestClient;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.ResponseEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class MultilevelScaServiceTest {
    private static final String IBAN_1 = "DE52500105173911841934";
    private static final String IBAN_2 = "DE52500105173911841935";
    private static final String PSU_ID = "psuId";
    @InjectMocks
    private MultilevelScaServiceImpl multilevelScaService;
    @Mock
    private UserMgmtRestClient userMgmtRestClient;
    @Mock
    private LedgersSpiAccountMapper ledgersSpiAccountMapper;
    private JsonReader jsonReader = new JsonReader();

    @Before
    public void setUp() {
        when(userMgmtRestClient.multilevelAccounts(anyString(), anyList())).thenReturn(ResponseEntity.ok(Boolean.TRUE));
    }

    @Test
    public void isMultilevelScaRequired_psuIdNull() {
        //Given
        SpiPsuData spiPsuData = buildSpiPsuData(null);
        Set<SpiAccountReference> spiAccountReferences = new HashSet<>();
        //When
        boolean multilevelScaRequired = multilevelScaService.isMultilevelScaRequired(spiPsuData, spiAccountReferences);
        //Then
        assertFalse(multilevelScaRequired);
    }

    @Test
    public void isMultilevelScaRequired_psuIdEmpty() {
        //Given
        SpiPsuData spiPsuData = buildSpiPsuData("");
        Set<SpiAccountReference> spiAccountReferences = new HashSet<>();
        //When
        boolean multilevelScaRequired = multilevelScaService.isMultilevelScaRequired(spiPsuData, spiAccountReferences);
        //Then
        assertFalse(multilevelScaRequired);
    }

    @Test
    public void isMultilevelScaRequired_references() {
        //Given
        SpiPsuData spiPsuData = buildSpiPsuData(PSU_ID);
        SpiAccountReference referenceIban_1 = buildSpiAccountReference(IBAN_1);
        SpiAccountReference referenceIban_2 = buildSpiAccountReference(IBAN_2);
        Set<SpiAccountReference> spiAccountReferences = new HashSet<>();
        spiAccountReferences.add(referenceIban_1);
        spiAccountReferences.add(referenceIban_2);
        when(userMgmtRestClient.multilevelAccounts(anyString(), anyList())).thenReturn(ResponseEntity.ok(Boolean.TRUE));
        Stream.of(referenceIban_1, referenceIban_2)
                .forEach(reference -> when(ledgersSpiAccountMapper.mapToAccountReferenceTO(reference)).thenReturn(mapToAccountReferenceTO(reference)));
        //When
        boolean multilevelScaRequired = multilevelScaService.isMultilevelScaRequired(spiPsuData, spiAccountReferences);
        //Then
        assertTrue(multilevelScaRequired);
        verify(userMgmtRestClient, atLeastOnce()).multilevelAccounts(PSU_ID, spiAccountReferences.stream().map(this::mapToAccountReferenceTO).collect(Collectors.toList()));
    }

    private SpiPsuData buildSpiPsuData(String psuId) {
        return SpiPsuData.builder()
                       .psuId(psuId)
                       .build();
    }

    private SpiAccountReference buildSpiAccountReference(String iban) {
        SpiAccountReference spiAccountReference = jsonReader.getObjectFromFile("json/mappers/spi-account-reference.json", SpiAccountReference.class);
        spiAccountReference.setIban(iban);
        return spiAccountReference;
    }

    private AccountReferenceTO mapToAccountReferenceTO(SpiAccountReference spiAccountReference) {
        AccountReferenceTO accountReferenceTO = new AccountReferenceTO();

        accountReferenceTO.setIban(spiAccountReference.getIban());
        accountReferenceTO.setBban(spiAccountReference.getBban());
        accountReferenceTO.setPan(spiAccountReference.getPan());
        accountReferenceTO.setMaskedPan(spiAccountReference.getMaskedPan());
        accountReferenceTO.setMsisdn(spiAccountReference.getMsisdn());
        accountReferenceTO.setCurrency(spiAccountReference.getCurrency());

        return accountReferenceTO;
    }
}
