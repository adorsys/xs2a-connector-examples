package de.adorsys.ledgers.xs2a.ctk.core;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.logging.HttpLoggingInterceptor;

import de.adorsys.psd2.client.ApiClient;


public abstract class AbstractITTest {

	@Value("${xs2a.config.readTimeoutInMs:60000}")
	private int readTimeout;
	@Value("${xs2a.config.connectionTimeoutInMs:60000}")
	private int connectionTimeout;
	@Value("${xs2a.baseUrl:http://localhost:8089/}")
	private String xs2aBaseUrl = "http://localhost:8089/";

	private String tppOkRedirectBaseURI = "http://tpp:8080/ok/";
	private String tppNokRedirectBaseURI = "http://tpp:8080/nok/";

	public ApiClient createApiClient() {
		return createApiClient(null);
	}

	public ApiClient createApiClient(String acceptHeader) {
		OkHttpClient client = new OkHttpClient();
		client.setReadTimeout(readTimeout, TimeUnit.MILLISECONDS);
		client.setConnectTimeout(connectionTimeout, TimeUnit.MILLISECONDS);
		client.interceptors().add(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY));

		ApiClient apiClient = new ApiClient() {
			@Override
			public String selectHeaderAccept(String[] accepts) {
				return Optional.ofNullable(acceptHeader).orElseGet(() -> super.selectHeaderAccept(accepts));
			}
		};
		apiClient.setHttpClient(client);
		apiClient.setBasePath(xs2aBaseUrl);
		return apiClient;
	}

	public int getReadTimeout() {
		return readTimeout;
	}

	public int getConnectionTimeout() {
		return connectionTimeout;
	}

	public String getXs2aBaseUrl() {
		return xs2aBaseUrl;
	}

	public String getTppOkRedirectBaseURI() {
		return tppOkRedirectBaseURI;
	}

	public String getTppNokRedirectBaseURI() {
		return tppNokRedirectBaseURI;
	}
}
