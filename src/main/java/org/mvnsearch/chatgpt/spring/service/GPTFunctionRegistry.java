package org.mvnsearch.chatgpt.spring.service;

import org.mvnsearch.chatgpt.model.completion.chat.ChatFunction;
import org.mvnsearch.chatgpt.model.function.ChatGPTJavaFunction;
import org.mvnsearch.chatgpt.model.function.GPTFunctionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository for all GPT callback functions.
 *
 * @author Josh Long
 */
class GPTFunctionRegistry
		implements BeanRegistrationAotProcessor, BeanPostProcessor, ApplicationListener<ApplicationReadyEvent> {

	private static final Logger log = LoggerFactory.getLogger(GPTFunctionRegistry.class);

	private final Map<String, ChatGPTJavaFunction> allJsonSchemaFunctions = new ConcurrentHashMap<>();

	private final Map<String, ChatFunction> allChatFunctions = new ConcurrentHashMap<>();

	public ChatFunction getChatFunction(String functionName) {
		return this.allChatFunctions.get(functionName);
	}

	public ChatGPTJavaFunction getJsonSchemaFunction(String functionName) {
		return this.allJsonSchemaFunctions.get(functionName);
	}

	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		log.info("ChatGPTService initialized with {} functions", this.allJsonSchemaFunctions.size());
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		try {
			Map<String, ChatGPTJavaFunction> functions = GPTFunctionUtils.extractFunctions(bean.getClass());
			if (!functions.isEmpty()) {
				log.info("found {} functions on bean name {} with class {}", functions.size(), beanName,
						bean.getClass().getName());
				for (Map.Entry<String, ChatGPTJavaFunction> entry : functions.entrySet()) {
					ChatGPTJavaFunction jsonSchemaFunction = entry.getValue();
					jsonSchemaFunction.setTarget(bean);
					String functionName = entry.getKey();
					this.allJsonSchemaFunctions.put(functionName, jsonSchemaFunction);
					this.allChatFunctions.put(functionName, jsonSchemaFunction.toChatFunction());
				}
			}
		} //
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		return bean;
	}

	static class GPTFunctionBeanRegistrationAotContribution implements BeanRegistrationAotContribution {

		private final Class<?> beanClass;

		private final Map<String, ChatGPTJavaFunction> functions;

		GPTFunctionBeanRegistrationAotContribution(Class<?> beanClass, Map<String, ChatGPTJavaFunction> functions) {
			this.beanClass = beanClass;
			this.functions = functions;
		}

		@Override
		public void applyTo(GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode) {
			ReflectionHints reflection = generationContext.getRuntimeHints().reflection();
			MemberCategory[] memberCategories = MemberCategory.values();
			reflection.registerType(beanClass, memberCategories);
			GPTFunctionUtils.getAllClassesInType(beanClass).forEach(c -> reflection.registerType(c, memberCategories));
			for (ChatGPTJavaFunction function : functions.values()) {
				Method method = function.getJavaMethod();
				reflection.registerType(method.getReturnType(), memberCategories);
				for (Class<?> pt : method.getParameterTypes()) {
					reflection.registerType(pt, memberCategories);
				}
			}
		}

	}

	@Override
	public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
		Class<?> beanClass = registeredBean.getBeanClass();
		try {
			Map<String, ChatGPTJavaFunction> functions = GPTFunctionUtils.extractFunctions(beanClass);
			functions.forEach(
					(functionName, function) -> log.info("Registering hints for function {} on bean {} with class {}",
							functionName, registeredBean.getBeanName(), registeredBean.getBeanClass().getName()));
			return !functions.isEmpty() ? new GPTFunctionBeanRegistrationAotContribution(beanClass, functions) : null;
		} //
		catch (Exception e) {
			throw new RuntimeException(
					String.format("couldn't read the functions on bean class %s", beanClass.getName()), e);
		}
	}

	@Override
	public boolean isBeanExcludedFromAotProcessing() {
		return false;
	}

}
