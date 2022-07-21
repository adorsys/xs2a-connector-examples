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

package de.adorsys.aspsp.xs2a.embedded.connector;

import de.adorsys.psd2.aspsp.profile.web.config.AspspProfileApiSwaggerConfig;
import de.adorsys.psd2.consent.web.aspsp.config.AspspApiSwaggerConfig;
import de.adorsys.psd2.consent.web.psu.config.PsuApiSwaggerConfig;
import de.adorsys.psd2.consent.web.xs2a.config.Xs2aApiSwaggerConfig;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(value = java.lang.annotation.RetentionPolicy.RUNTIME)
@Target(value = {java.lang.annotation.ElementType.TYPE})
@Documented
@Import({
        LedgersXS2AConnectorEmbeddedConfiguration.class,
        Xs2aApiSwaggerConfig.class,
        PsuApiSwaggerConfig.class,
        AspspApiSwaggerConfig.class,
        AspspProfileApiSwaggerConfig.class
})
public @interface EnableLedgersXS2AConnectorEmbedded {
}
