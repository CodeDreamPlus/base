package com.codedream.auto.factories;

/**
 * <p>Description: [自动配置注解类型]</p >
 * Created on 2022-03-17
 *
 * @author mo
 */
public enum AutoTypeEnum {
	/**
	 * 注解处理的类型
	 */
	COMPONENT("org.springframework.stereotype.Component", "org.springframework.boot.autoconfigure.EnableAutoConfiguration");

	/**
	 * 注解全名
	 */
	private final String annotationName;
	/**
	 * spring.factories文件对应key
	 */
	private final String configureKey;

	AutoTypeEnum(String annotationName, String configureKey) {
		this.annotationName = annotationName;
		this.configureKey = configureKey;
	}

	public final String getAnnotationName() {
		return annotationName;
	}

	public final String getConfigureKey() {
		return configureKey;
	}

}
