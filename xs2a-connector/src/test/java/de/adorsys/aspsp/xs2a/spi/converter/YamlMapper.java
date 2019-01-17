package de.adorsys.aspsp.xs2a.spi.converter;

import java.io.IOException;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

public class YamlMapper {
	private Class<?> sourceKlass;
	YAMLMapper mapper = new YAMLMapper();
	
    public YamlMapper(Class<?> sourceKlass) {
		this.sourceKlass = sourceKlass;
		mapper.findAndRegisterModules();
		mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

	}
    public <T> T readYml(Class<T> aClass, String file) throws IOException{
    	return mapper.readValue(sourceKlass.getResourceAsStream(file), aClass);
    }
}
