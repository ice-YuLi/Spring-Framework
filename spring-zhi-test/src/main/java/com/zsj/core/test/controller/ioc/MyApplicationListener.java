package com.zsj.core.test.controller.ioc;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

public class MyApplicationListener implements ApplicationListener<ContextRefreshedEvent> {

	// ApplicationContext事件机制是观察者设计模式的实现
	// https://blog.csdn.net/liyantianmin/article/details/81017960
	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		// 打印容器中出事Bean的数量

		// (事件监听触发节点 org/springframework/context/support/AbstractApplicationContext.java:1066）
		System.out.println("监听器获得容器中初始化Bean数量：" + event.getApplicationContext().getBeanDefinitionCount());
	}
}
