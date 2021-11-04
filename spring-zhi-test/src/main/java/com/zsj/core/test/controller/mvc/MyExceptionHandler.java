package com.zsj.core.test.controller.mvc;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class MyExceptionHandler implements HandlerExceptionResolver {

	public static final Log logs = LogFactory.getLog(MyExceptionHandler.class);

	@Override
	public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {

		request.setAttribute("exception", ex.toString()) ;
		request.setAttribute ("exceptionStack", ex) ;
		logs.error(ex.toString(), ex) ;

		return new ModelAndView("error/exception");
	}
	// 这个类必须声明到 Spring 中去，让 Spring 管理它，在 Spring 的配置文件 applicationContext.xml 中增加以下内
	// <bean id="exceptionHandler" class="com.zsj.core.test.controller.mvc.MyExceptionHandler />
}
