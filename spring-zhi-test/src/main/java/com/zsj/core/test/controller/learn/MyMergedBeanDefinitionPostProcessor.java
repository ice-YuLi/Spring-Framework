package com.zsj.core.test.controller.learn;

import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.stereotype.Component;

// @Component
public class MyMergedBeanDefinitionPostProcessor implements MergedBeanDefinitionPostProcessor {

	@Override
	public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
		if("userService".equals(beanName)){
			beanDefinition.getPropertyValues().add("orderService", new OrderService());
		}

	}
}
