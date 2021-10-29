package com.zsj.core.test.controller.ioc;

import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;

public class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor, Ordered {

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		System.out.println("调用postProcessBeanFactory()方法");

		BeanDefinition bd = beanFactory.getBeanDefinition("myTestBean");
		MutablePropertyValues pv =  bd.getPropertyValues();
		if (pv.contains("name")) {
//			pv.addPropertyValue("name", "在BeanFactoryPostProcessor中修改之后的名字");
		}
	}

	@Override
	public int getOrder() {
		return 99;
	}
}
