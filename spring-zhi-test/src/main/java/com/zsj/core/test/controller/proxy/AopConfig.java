package com.zsj.core.test.controller.proxy;

import com.zsj.core.test.controller.proxy.advice.MyAdvice;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.aop.framework.autoproxy.BeanNameAutoProxyCreator;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.aop.support.DefaultBeanFactoryPointcutAdvisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

@ComponentScan("com.zsj.core.test.controller.proxy")
@EnableAspectJAutoProxy
public class AopConfig {

	/**
	 * 该方法的意思就是直接返回一个 userService 的代理 bean
	 * @return
	 */
//	@Bean
//	public ProxyFactoryBean userService(){
//
//		UserService target = new UserService();
//		ProxyFactoryBean proxyFactoryBean = new ProxyFactoryBean();
//		proxyFactoryBean.setTarget(target);
//		proxyFactoryBean.addAdvice(new MyAdvice());
//
//		return proxyFactoryBean;
//	}

	@Bean
	public BeanNameAutoProxyCreator beanNameAutoProxyCreator(){

		UserService target = new UserService();
		BeanNameAutoProxyCreator beanNameAutoProxyCreator = new BeanNameAutoProxyCreator();
		beanNameAutoProxyCreator.setBeanNames("userSe*"); // 支持通配符
		beanNameAutoProxyCreator.setInterceptorNames("myAdvice");
		beanNameAutoProxyCreator.setProxyTargetClass(true);

		return beanNameAutoProxyCreator;
	}

	@Bean
	public DefaultPointcutAdvisor defaultPointcutAdvisor(){

		NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
		pointcut.addMethodName("test");

		DefaultPointcutAdvisor defaultPointcutAdvisor = new DefaultPointcutAdvisor();
		defaultPointcutAdvisor.setPointcut(pointcut);
		defaultPointcutAdvisor.setAdvice(new MyAdvice());

		return defaultPointcutAdvisor;
	}

	@Bean // 也可以使用 @Import(DefaultAdvisorAutoProxyCreator.class) 替代该方法
	public DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator(){

		DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator = new DefaultAdvisorAutoProxyCreator();

		return defaultAdvisorAutoProxyCreator;
	}

}
