package com.zsj.core.test.controller.ioc;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class MyBeanDefinitionRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor {

	@Override
	// BeanDefinitionRegistryPostProcessor可以动态将Bean注册。
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		// 使用说明：https://blog.csdn.net/ztchun/article/details/90814135
		// 创建一个bean的定义类的对象
		RootBeanDefinition rootBeanDefinition = new RootBeanDefinition(MyTestBean.class);
		// 将Bean 的定义注册到Spring环境
		registry.registerBeanDefinition("myTestBeanTwo", rootBeanDefinition);

		System.out.println("调用MyBeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry()方法");
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		System.out.println("调用MyBeanDefinitionRegistryPostProcessor#postProcessBeanFactory()方法");
	}
}
