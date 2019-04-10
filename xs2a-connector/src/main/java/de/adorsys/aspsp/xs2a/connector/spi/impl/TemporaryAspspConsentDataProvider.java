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

import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;
import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * TODO remove after full SpiAspspConsentDataProvider implementation is done https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/796
 * @deprecated remove after full SpiAspspConsentDataProvider implementation is done
 */
@Deprecated
class TemporaryAspspConsentDataProvider implements SpiAspspConsentDataProvider {
    private AspspConsentData aspspConsentData;

    TemporaryAspspConsentDataProvider(@NotNull AspspConsentData aspspConsentData) {
        this.aspspConsentData = aspspConsentData;
    }

    @Override
    public byte[] loadAspspConsentData() {
        return this.aspspConsentData.getAspspConsentData();
    }

    @Override
    public void updateAspspConsentData(@Nullable byte[] aspspConsentData) {
        this.aspspConsentData = this.aspspConsentData.respondWith(aspspConsentData);
    }

    @Override
    public void clearAspspConsentData() {
        this.aspspConsentData = this.aspspConsentData.respondWith(new byte[0]);
    }

    public AspspConsentData getAspspConsentData() {
        return aspspConsentData;
    }
}
