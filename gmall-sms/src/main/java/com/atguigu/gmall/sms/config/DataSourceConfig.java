package com.atguigu.gmall.sms.config;

import com.zaxxer.hikari.HikariDataSource;
import io.seata.rm.datasource.DataSourceProxy;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

	@Primary
	@Bean
	public DataSource dataSource(DataSourceProperties dataSourceProperties) {
		HikariDataSource hikariDataSource = new HikariDataSource();
		hikariDataSource.setJdbcUrl(dataSourceProperties.getUrl());
		hikariDataSource.setDriverClassName(dataSourceProperties.getDriverClassName());
		hikariDataSource.setUsername(dataSourceProperties.getUsername());
		hikariDataSource.setPassword(dataSourceProperties.getPassword());
		return new DataSourceProxy(hikariDataSource);
	}
}
