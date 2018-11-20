/*
 * Copyright 2018-2018 adorsys GmbH & Co KG
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

package de.adorsys.aspsp.xs2a.spi.impl;

import de.adorsys.aspsp.xs2a.spi.converter.LedgersSpiAccountMapper;
import de.adorsys.ledgers.LedgersAccountRestClient;
import de.adorsys.ledgers.domain.account.FundsConfirmationRequestTO;
import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.exception.RestException;
import de.adorsys.psd2.xs2a.spi.domain.fund.SpiFundsConfirmationRequest;
import de.adorsys.psd2.xs2a.spi.domain.psu.SpiPsuData;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponseStatus;
import de.adorsys.psd2.xs2a.spi.service.FundsConfirmationSpi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class FundsConfirmationSpiImpl implements FundsConfirmationSpi {
    private static final Logger logger = LoggerFactory.getLogger(SinglePaymentSpiImpl.class);

    private final LedgersAccountRestClient restClient;
    private final LedgersSpiAccountMapper accountMapper;

    public FundsConfirmationSpiImpl(LedgersAccountRestClient restClient, LedgersSpiAccountMapper accountMapper) {
        this.restClient = restClient;
        this.accountMapper = accountMapper;
    }

    @Override
    public @NotNull SpiResponse<Boolean> performFundsSufficientCheck(@NotNull SpiPsuData psuData, @Nullable String consentId, @NotNull SpiFundsConfirmationRequest spiFundsConfirmationRequest, @NotNull AspspConsentData aspspConsentData) {
        try {
            logger.info("Funds confirmation request e={}", spiFundsConfirmationRequest);
            FundsConfirmationRequestTO request = accountMapper.toFundsConfirmationTO(psuData, spiFundsConfirmationRequest);
            Boolean fundsAvailable = restClient.fundsConfirmation(request).getBody();
            logger.info("And got the response ={}", fundsAvailable);
            return SpiResponse.<Boolean>builder()
                           .aspspConsentData(aspspConsentData)
                           .payload(fundsAvailable)
                           .success();
        } catch (RestException e) {
            return SpiResponse.<Boolean>builder()
                           .aspspConsentData(aspspConsentData)
                           .fail(getSpiFailureResponse(e));
        }
    }

    @NotNull
    private SpiResponseStatus getSpiFailureResponse(RestException e) {
        logger.error(e.getMessage(), e);
        return (e.getHttpStatus() == HttpStatus.INTERNAL_SERVER_ERROR)
                       ? SpiResponseStatus.TECHNICAL_FAILURE
                       : SpiResponseStatus.LOGICAL_FAILURE;
    }

}
