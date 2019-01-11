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

package de.adorsys.ledgers.xs2a.ctk.profile;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

import de.adorsys.ledgers.xs2a.ctk.utils.RemoteURLs;
import de.adorsys.psd2.aspsp.profile.domain.AspspSettings;
import de.adorsys.psd2.xs2a.core.profile.ScaApproach;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;

@FeignClient(value = "aspspProfileClient", url = RemoteURLs.PROFILE_URL, path="/aspsp-profile")
@Api(value = "Aspsp profile", tags = "Aspsp profile", description = "Provides access to aspsp profile")
public interface AspspProfileClient {

    @GetMapping
    @ApiOperation(value = "Reads aspsp specific settings")
    @ApiResponse(code = 200, message = "Ok", response = AspspSettings.class)
    public ResponseEntity<AspspSettings> getAspspSettings();

    @GetMapping(path = "/sca-approach")
    @ApiOperation(value = "Reads sca approach value")
    @ApiResponse(code = 200, message = "Ok", response = ScaApproach.class)
    public ResponseEntity<ScaApproach> getScaApproach();
}
