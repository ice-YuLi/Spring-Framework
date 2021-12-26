package com.zsj.core.test.controller.ioc;

import org.springframework.core.convert.converter.Converter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class StringToDateConverter implements Converter<String, Date> {
	// 转换器 org/springframework/context/support/AbstractApplicationContext.java:985
	@Override
	public Date convert(String source) {
		Date date = null;
		try {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			date = sdf.parse(source);
		} catch (ParseException e) {
			e.printStackTrace();
			System.out.println("日期转换失败!");
		}
		return date;
	}
}
