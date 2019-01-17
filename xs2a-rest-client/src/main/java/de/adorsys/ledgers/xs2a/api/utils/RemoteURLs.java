package de.adorsys.ledgers.xs2a.api.utils;

public interface RemoteURLs {
	String LEDGERS_URL="${ledgers.url:http://localhost:8088}";
	String PROFILE_URL="${aspsp-profile.baseurl:http://localhost:48080/api/v1}";
	String XS2A_URL="${xs2a.baseUrl:http://localhost:8089}";
}
