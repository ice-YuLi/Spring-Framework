package com.zsj.core.test.controller.aop.introduction;

import org.aopalliance.aop.Advice;
import org.springframework.aop.Advisor;
import org.springframework.aop.DynamicIntroductionAdvice;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultIntroductionAdvisor;
import org.springframework.aop.support.DelegatePerTargetObjectIntroductionInterceptor;

// https://blog.csdn.net/f641385712/article/details/89303088

public class SomeInteDelegatePerTargetObjectIntroductionInterceptor implements IOtherInte{

	@Override
	public void doOther() {
		System.out.println("给人贴标签 doOther...");
	}

	// 方法测试
	public static void main(String[] args) {
		ProxyFactory factory = new ProxyFactory(new Person());
		factory.setProxyTargetClass(true); // 强制私用CGLIB 以保证我们的Person方法也能正常调用

		// 此处采用IntroductionInterceptor 这个引介增强的拦截器
		Advice advice = new DelegatePerTargetObjectIntroductionInterceptor(OtherImpl.class, IOtherInte.class);

		// 切点+通知（注意：此处放的是复合切面）
		Advisor advisor = new DefaultIntroductionAdvisor((DynamicIntroductionAdvice) advice, IOtherInte.class);
		//Advisor advisor = new DefaultPointcutAdvisor(cut, advice);
		factory.addAdvisor(advisor);

		IOtherInte otherInte = (IOtherInte) factory.getProxy();
		otherInte.doOther();

		System.out.println("===============================");

		// Person本身自己的方法  也得到了保留
		Person p = (Person) factory.getProxy();
		p.run();
		p.say();
	}
}
class OtherImpl implements IOtherInte {

	@Override
	public void doOther() {
		System.out.println("我是OtherImpl");
	}
}