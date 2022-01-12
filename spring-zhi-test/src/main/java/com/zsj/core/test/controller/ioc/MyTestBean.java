package com.zsj.core.test.controller.ioc;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class MyTestBean implements DisposableBean{

	private Student student;

	private String name;

	public String getName() {
		System.out.println("调用getName()方法");
		return name;
	}

	public void setName(String name) {
		System.out.println("调用setName()方法");
		this.name = name;
	}

	public void outStr() {
		System.out.println(name);
	}

	@Override
	public void destroy() throws Exception {
		System.out.println("MyTestBean 要销毁了");
	}

	public Student getStudent() {
		return student;
	}

	public void setStudent(Student student) {
		this.student = student;
	}
}
