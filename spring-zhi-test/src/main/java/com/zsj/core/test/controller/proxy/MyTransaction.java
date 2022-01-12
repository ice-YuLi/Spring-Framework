package com.zsj.core.test.controller.proxy;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
public class MyTransaction {

	@Bean
	public JdbcTemplate jdbcTemplate(){
		return new JdbcTemplate(dataSource());
	}

	@Bean
	public PlatformTransactionManager platformTransactionManager(){
		DataSourceTransactionManager dataSourceTransactionManager = new DataSourceTransactionManager();
		dataSourceTransactionManager.setDataSource(dataSource());
		dataSourceTransactionManager.setGlobalRollbackOnParticipationFailure(true);
		return dataSourceTransactionManager;
	}

	@Bean
	public DataSource dataSource(){
		DriverManagerDataSource driverManagerDataSource = new DriverManagerDataSource();
//		driverManagerDataSource.setDriverClassName("com.mysql.jdbc.Driver");
//		driverManagerDataSource.setUrl("jdbc:mysql://10.18.58.35:10000/ucdb");
//		driverManagerDataSource.setUsername("root");
//		driverManagerDataSource.setPassword("Ecif.123");
		return driverManagerDataSource;
	}

	@Bean
	public MyTransactionService myTransactionService(){
		return new MyTransactionService();
	}


}

