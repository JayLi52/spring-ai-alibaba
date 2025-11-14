/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.graph.serializer.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.ai.chat.messages.UserMessage;

import java.io.IOException;
import java.util.Map;

/**
 * Jackson module that adds serializer/deserializer support for {@link UserMessage}.
 */
public class UserMessageJacksonModule extends SimpleModule {

	public UserMessageJacksonModule() {
		super("UserMessageJacksonModule");
		addSerializer(UserMessage.class, new UserMessageJsonSerializer());
		addDeserializer(UserMessage.class, new UserMessageJsonDeserializer());
	}

	private static class UserMessageJsonSerializer extends com.fasterxml.jackson.databind.JsonSerializer<UserMessage> {

		@Override
		public void serialize(UserMessage value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
			gen.writeStartObject();
			gen.writeStringField("text", value.getText());
			if (value.getMetadata() != null && !value.getMetadata().isEmpty()) {
				gen.writeObjectField("metadata", value.getMetadata());
			}
			gen.writeEndObject();
		}
	}

	private static class UserMessageJsonDeserializer extends JsonDeserializer<UserMessage> {

		@Override
		public UserMessage deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
			JsonNode node = p.getCodec().readTree(p);
			JsonNode textNode = node.get("text");
			if (textNode == null || textNode.isNull()) {
				ctxt.reportInputMismatch(UserMessage.class, "Missing 'text' field for UserMessage");
			}

			Map<String, Object> metadata = null;
			JsonNode metadataNode = node.get("metadata");
			if (metadataNode != null && !metadataNode.isNull()) {
				metadata = ((ObjectMapper) p.getCodec()).convertValue(metadataNode, Map.class);
			}

			return UserMessage.builder().text(textNode.asText()).metadata(metadata).build();
		}
	}

}

