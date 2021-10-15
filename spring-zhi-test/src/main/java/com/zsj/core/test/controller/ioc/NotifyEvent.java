package com.zsj.core.test.controller.ioc;

import org.springframework.context.ApplicationEvent;

public class NotifyEvent extends ApplicationEvent {

	private static final long serialVersionUID = 1L;

	private String email;

	private String content;

	public NotifyEvent(Object source, String email, String content) {
		super(source);
		this.email = email;
		this.content = content;
	}

	public NotifyEvent(Object source) {
		super(source);
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
}
