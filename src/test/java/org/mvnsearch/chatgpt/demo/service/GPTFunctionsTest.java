package org.mvnsearch.chatgpt.demo.service;

import org.junit.jupiter.api.Test;
import org.mvnsearch.chatgpt.model.function.GPTFunctionUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class GPTFunctionsTest {

	@Test
	void testExtractFunctions() throws Exception {

		List<String> expected = List.of("sendEmail", "executeSQLQuery");
		List<String> list = GPTFunctionUtils.extractFunctions(GPTFunctions.class)
			.values()
			.stream()
			.map(m -> GPTFunctionUtils.toTextPlain(m))
			.toList();
		for (String s : list) {
			System.err.println(s);

		}
//		assertIterableEquals(expected, list);
	}

}