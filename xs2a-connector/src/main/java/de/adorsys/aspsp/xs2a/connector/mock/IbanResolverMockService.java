/*
 * Copyright 2018-2023 adorsys GmbH & Co KG
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

package de.adorsys.aspsp.xs2a.connector.mock;

import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// TODO: Remove when ledgers starts supporting card accounts https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/1246
@Service
public class IbanResolverMockService {
    private Map<String, String> ibanMap = new HashMap<>();

    @PostConstruct
    public void setup() {

        ibanMap.put("493702******0836", "DE69760700240340283600"); // none
        ibanMap.put("4937023494670836", "DE69760700240340283600"); // none

        ibanMap.put("525412******3241", "DE80760700240271232400"); //single
        ibanMap.put("5254127692833241", "DE80760700240271232400"); //single

        ibanMap.put("037504******4669", "DE38760700240320465700"); //multiple
        ibanMap.put("0375049529584669", "DE38760700240320465700"); //multiple

        ibanMap.put("111122******4444", "DE17123456780000000001");
        ibanMap.put("1111222233334444", "DE17123456780000000001");

        ibanMap.put("222233******5555", "DE87123456780000000002");
        ibanMap.put("2222333344445555", "DE87123456780000000002");

        ibanMap.put("333344******6666", "DE60123456780000000003");
        ibanMap.put("3333444455556666", "DE60123456780000000003");

        ibanMap.put("444455******7777", "DE33123456780000000004");
        ibanMap.put("4444555566667777", "DE33123456780000000004");
    }

    public String handleIbanByAccountReference(SpiAccountReference accountReference) {
        return Optional.ofNullable(ibanMap.get(accountReference.getMaskedPan()))
                       .orElse(ibanMap.get(accountReference.getPan()));
    }

    public Optional<String> getIbanByMaskedPan(String maskedPan) {
        return Optional.ofNullable(ibanMap.get(maskedPan));
    }

    public String getMaskedPanByIban(String iban) {

        // Find all keys by the definite value.
        Set<String> keys = ibanMap.entrySet()
                                   .stream()
                                   .filter(entry -> entry.getValue().equals(iban))
                                   .map(Map.Entry::getKey)
                                   .collect(Collectors.toSet());

        // Select those, which contains masked symbols (as ASPSP always returns masked PAN).
        return keys.stream()
                       .filter(key -> key.contains("******"))
                       .findFirst()
                       .orElse("No value of masked PAN for this IBAN");
    }

}
