package com.zsj.core.test.controller.ioc;

import org.springframework.beans.factory.FactoryBean;

public class MyTestBeanFactoryBean implements FactoryBean<MyTestBean> {

	private String nameInfo;

	@Override
	public MyTestBean getObject() throws Exception {

		MyTestBean myTestBean = new MyTestBean();
		myTestBean.setName(this.nameInfo);

		return myTestBean;
	}

	@Override
	public Class<MyTestBean> getObjectType() {
		return MyTestBean.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

	public String getNameInfo() {
		return nameInfo;
	}

	public void setNameInfo(String nameInfo) {
		this.nameInfo = nameInfo;
	}
}
