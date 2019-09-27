package de.adorsys.aspsp.xs2a.connector.spi.impl.authorisation;

import de.adorsys.psd2.xs2a.spi.domain.SpiAspspConsentDataProvider;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wrapper around SpiAspspConsentDataProvider with encryptedConsentId in fields
 *
 * Needed for testing PSU message generation in decoupled approach, which uses reflection to extract consent ID from provider
 */
@RequiredArgsConstructor
public class SpiAspspConsentDataProviderWithEncryptedId implements SpiAspspConsentDataProvider {
    private final SpiAspspConsentDataProvider spiAspspConsentDataProvider;
    private final String encryptedConsentId;

    @NotNull
    @Override
    public byte[] loadAspspConsentData() {
        return spiAspspConsentDataProvider.loadAspspConsentData();
    }

    @Override
    public void updateAspspConsentData(@Nullable byte[] bytes) {
        spiAspspConsentDataProvider.updateAspspConsentData(bytes);
    }

    @Override
    public void clearAspspConsentData() {
        spiAspspConsentDataProvider.clearAspspConsentData();
    }
}
