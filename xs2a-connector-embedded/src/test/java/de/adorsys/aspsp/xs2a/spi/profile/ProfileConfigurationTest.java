package de.adorsys.aspsp.xs2a.spi.profile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class ProfileConfigurationTest {

	@Test
	public void test() throws JsonParseException, JsonMappingException, IOException {
		ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
		InputStream resourceAsStream = ProfileConfigurationTest.class.getResourceAsStream("/bank_profile_ledgers.yml");
		AspspSettingsHolder settings = objectMapper.readValue(resourceAsStream, AspspSettingsHolder.class);
		List<String> availablePaymentProducts = settings.getSetting().getAvailablePaymentProducts();
		Assert.assertTrue(availablePaymentProducts.size()==2);
	}

}
