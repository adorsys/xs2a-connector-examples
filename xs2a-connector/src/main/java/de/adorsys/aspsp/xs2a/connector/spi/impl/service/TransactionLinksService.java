/*
 * Copyright 2018-2021 adorsys GmbH & Co KG
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

package de.adorsys.aspsp.xs2a.connector.spi.impl.service;

import de.adorsys.ledgers.middleware.api.domain.account.TransactionTO;
import de.adorsys.ledgers.util.domain.CustomPageImpl;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiTransactionLinks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class TransactionLinksService {
    private static final String PAGE_INDEX_QUERY_PARAM = "pageIndex";
    private final HttpServletRequest request;

    public SpiTransactionLinks buildSpiTransactionLinks(int currentPage, int itemsPerPage, CustomPageImpl<TransactionTO> transactionsOnPage) {
        if (transactionsOnPage == null) {
            return null;
        }
        String uri = request.getRequestURI();
        UriComponentsBuilder firstPage = UriComponentsBuilder.fromPath(uri)
                                                 .queryParam(PAGE_INDEX_QUERY_PARAM, 0);
        UriComponentsBuilder nextPage = UriComponentsBuilder.fromPath(uri)
                                                .queryParam(PAGE_INDEX_QUERY_PARAM, currentPage + 1);
        UriComponentsBuilder previousPage = UriComponentsBuilder.fromPath(uri)
                                                    .queryParam(PAGE_INDEX_QUERY_PARAM, currentPage - 1);
        UriComponentsBuilder lastPage = UriComponentsBuilder.fromPath(uri)
                                                .queryParam(PAGE_INDEX_QUERY_PARAM, transactionsOnPage.getTotalPages() - 1);
        Arrays.asList(firstPage, nextPage, previousPage, lastPage).forEach(u -> u.queryParam("itemsPerPage", itemsPerPage));

        String first = transactionsOnPage.isFirstPage() ? null : firstPage.toUriString();
        String next = transactionsOnPage.isLastPage() ? null : nextPage.toUriString();
        String previous = transactionsOnPage.isFirstPage() ? null : previousPage.toUriString();
        String last = transactionsOnPage.isLastPage() ? null : lastPage.toUriString();
        return new SpiTransactionLinks(first, next, previous, last);
    }
}
