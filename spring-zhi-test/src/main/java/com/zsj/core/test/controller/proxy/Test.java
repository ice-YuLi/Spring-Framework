package com.zsj.core.test.controller.proxy;

import com.zsj.core.test.controller.learn.ConfigurationConfig;
import org.aopalliance.intercept.Interceptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Test {

	public static void main(String[] args) {

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
//		context.register(AopConfig.class);
		context.register(MyTransaction.class);
		context.refresh();

//		UserService userService = (UserService) context.getBean("userService");
//		userService.test();

		MyTransactionService myTransactionService = (MyTransactionService) context.getBean("myTransactionService");
		myTransactionService.transaction();

	}
}
