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
import de.adorsys.ledgers.middleware.api.domain.account.AccountReferenceTO;
import de.adorsys.ledgers.rest.client.UserMgmtRestClient;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import feign.FeignException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class MultilevelScaServiceImpl implements MultilevelScaService {
    private final UserMgmtRestClient userMgmtRestClient;
    private final LedgersSpiAccountMapper ledgersSpiAccountMapper;

    @Override
    public Optional<Boolean> isMultilevelScaRequired(SpiPsuData spiPsuData, Set<SpiAccountReference> spiAccountReferences) {
        String psuId = spiPsuData.getPsuId();

        if (StringUtils.isBlank(psuId)) {
            return Optional.of(Boolean.FALSE);
        }

        List<AccountReferenceTO> accountReferences = spiAccountReferences.stream()
                                                             .map(ledgersSpiAccountMapper::mapToAccountReferenceTO)
                                                             .collect(Collectors.toList());

        ResponseEntity<Boolean> response;

        try {
            response = userMgmtRestClient.multilevelAccounts(psuId, accountReferences);
        } catch (FeignException e) {
            log.error("Error during REST call to ledgers for account multilevel checking, PSU ID: {}", psuId);
            return Optional.empty();
        }

        return Optional.of(BooleanUtils.toBoolean(response.getBody()));
    }

}
