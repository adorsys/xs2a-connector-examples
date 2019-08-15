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

package de.adorsys.aspsp.xs2a.connector.spi.converter;

import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

class ScaMethodUtils {

    private ScaMethodUtils() {
    }

    static List<String> toScaMethods(List<ScaUserDataTO> scaMethods) {
        return CollectionUtils.isEmpty(scaMethods)
                       ? Collections.emptyList()
                       : scaMethods.stream().map(ScaUserDataTO::getId).collect(Collectors.toList());
    }

    static String toScaMethod(ScaUserDataTO scaUserDataTO) {
        return Optional.ofNullable(scaUserDataTO)
                       .map(ScaUserDataTO::getId)
                       .orElse(null);
    }
}
