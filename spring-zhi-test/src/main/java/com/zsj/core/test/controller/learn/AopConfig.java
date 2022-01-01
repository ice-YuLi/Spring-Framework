package com.zsj.core.test.controller.learn;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Scope;

@ComponentScan("com.zsj.core.test.controller.learn")
//@PropertySource("classpath:application.properties")
public class AopConfig {

	@Bean
//	@Scope("prototype")
	public UserService userService(){
		return new UserService();
	}

	@Bean
	public OrderService orderService(){
		return new OrderService();
	}
}
