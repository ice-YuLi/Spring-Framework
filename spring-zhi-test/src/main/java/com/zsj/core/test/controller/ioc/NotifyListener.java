package com.zsj.core.test.controller.ioc;

import org.springframework.context.ApplicationListener;

public class NotifyListener implements ApplicationListener<NotifyEvent> {

	@Override
	public void onApplicationEvent(NotifyEvent event) {
		System.out.println("邮件地址：" + event.getEmail());
		System.out.println("邮件内容：" + event.getContent());
	}
}
