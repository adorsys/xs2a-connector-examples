package de.adorsys.aspsp.xs2a.spi.impl;

import java.io.UnsupportedEncodingException;

import de.adorsys.psd2.xs2a.core.consent.AspspConsentData;

public class TokenUtils {
	
	public static String read(AspspConsentData aspspConsentData) {
		byte[] aspspConsentDataBytes = aspspConsentData.getAspspConsentData();
		if(aspspConsentDataBytes!=null) {
			try {
				return new String(aspspConsentDataBytes, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				throw new IllegalStateException(e);
			}
		} else {
			return null;
		}
	}
	
	public static AspspConsentData store(String accessToken, AspspConsentData aspspConsentData) {
		if(accessToken==null) {
			return aspspConsentData;
		} else {
			try {
				return aspspConsentData.respondWith(accessToken.getBytes("UTF-8"));
			} catch (UnsupportedEncodingException e) {
				throw new IllegalStateException(e);
			}
		}
	}
}
