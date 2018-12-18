package de.adorsys.aspsp.xs2a.test;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import liquibase.integration.spring.SpringLiquibase;

@Configuration
@Profile("liquibase")
public class LiquiBaseConfig {
	@Autowired
	DataSource dataSource;

  @Bean
  public SpringLiquibase liquibase() {
      SpringLiquibase liquibase = new SpringLiquibase();
      liquibase.setChangeLog("classpath:master.xml");
      liquibase.setDataSource(dataSource);
//      liquibase.setDefaultSchema(dbSchema);
      return liquibase;
  }    
}
