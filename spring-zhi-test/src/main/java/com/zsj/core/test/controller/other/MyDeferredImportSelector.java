package com.zsj.core.test.controller.other;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.type.AnnotationMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * @Import 的三种用法：
 * 1. 实现 ImportSelector
 * 2. 实现 DeferredImportSelector
 * 3. 实现 ImportBeanDefinitionRegistrar
 */
public class MyDeferredImportSelector implements DeferredImportSelector {


	@Override
	public Class<? extends Group> getImportGroup() {
		// 分组
		return MyGroup.class;
	}

	@Override
	public String[] selectImports(AnnotationMetadata importingClassMetadata) {
		return new String[0];
	}
}

class MyGroup implements DeferredImportSelector.Group{

	AnnotationMetadata metadata;

	@Override
	public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {

		this.metadata = metadata;
	}

	@Override
	public Iterable<Entry> selectImports() {

		List<Entry> list = new ArrayList<>();
		list.add(new Entry(this.metadata, "com.zsj.core.test.controller.proxy.UserService"));

		return null;
	}
}