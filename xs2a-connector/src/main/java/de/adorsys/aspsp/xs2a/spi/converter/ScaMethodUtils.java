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

package de.adorsys.aspsp.xs2a.spi.converter;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import de.adorsys.ledgers.middleware.api.domain.um.ScaUserDataTO;

public abstract class ScaMethodUtils {

    public static List<String> toScaMethods(List<ScaUserDataTO> scaMethods){
    	if(scaMethods==null || scaMethods.isEmpty()) {
    		return Collections.emptyList();
    	}
    	return scaMethods.stream().map(s -> s.getId()).collect(Collectors.toList());
    }
    
    public static String toScaMethod(ScaUserDataTO m) {
    	return m==null
    			? null
    					: m.getId();
    }
}
