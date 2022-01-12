package com.zsj.core.test.controller.learn;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.zsj.core.test.controller.learn")
public class ConfigurationConfig {

	@Bean
	public UserService userService(){
//		System.out.println(orderService());
//		System.out.println(orderService());
//		System.out.println(orderService());
//		System.out.println(orderService());
		return new UserService();
	}

//	@Bean
//	public OrderService orderService(){
//		return new OrderService();
//	}
}
