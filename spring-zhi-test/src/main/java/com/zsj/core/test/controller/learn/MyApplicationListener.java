package com.zsj.core.test.controller.learn;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

public class MyApplicationListener implements ApplicationListener<MyApplicationEvent> {
//public class MyApplicationListener implements ApplicationListener{

	@Override
	public void onApplicationEvent(MyApplicationEvent event) {
		System.out.println(event.getSource()+"==== "+event.getTimestamp());
	}

//	@Override
//	public void onApplicationEvent(ApplicationEvent event) {
//		System.out.println(event.getSource()+"==== "+event.getTimestamp());
//	}
}
