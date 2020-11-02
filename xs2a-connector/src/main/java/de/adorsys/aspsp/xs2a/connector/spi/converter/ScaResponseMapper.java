/*
 * Copyright 2018-2020 adorsys GmbH & Co KG
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

import de.adorsys.ledgers.middleware.api.domain.sca.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = OpTypeTO.class)
public interface ScaResponseMapper {

    @Mapping(target = "opType", expression = "java(OpTypeTO.LOGIN)")
    @Mapping(target = "operationObjectId", source = "scaId")
    GlobalScaResponseTO toGlobalScaResponse(SCALoginResponseTO responseTO);

    @Mapping(target = "opType", expression = "java(OpTypeTO.CONSENT)")
    @Mapping(target = "operationObjectId", source = "consentId")
    @Mapping(target = "tan", source = "chosenScaMethod.staticTan")
    GlobalScaResponseTO toGlobalScaResponse(SCAConsentResponseTO responseTO);

    @Mapping(target = "opType", expression = "java(OpTypeTO.PAYMENT)")
    @Mapping(target = "operationObjectId", source = "paymentId")
    @Mapping(target = "tan", source = "chosenScaMethod.staticTan")
    GlobalScaResponseTO toGlobalScaResponse(SCAPaymentResponseTO responseTO);
}
