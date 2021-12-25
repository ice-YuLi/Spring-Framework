package com.zsj.core.test.controller.learn;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan("com.zsj.core.test.controller.learn")
public class AopConfig {

	@Bean(autowire= Autowire.BY_NAME)
	public UserService userService(){
		return new UserService();
	}
}
