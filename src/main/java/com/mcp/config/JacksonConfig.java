package com.mcp.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;


@Configuration
public class JacksonConfig {

	@Bean
	public ObjectMapper objectMapper() {
		ObjectMapper mapper = new ObjectMapper();

		// Ignore null values from response
		mapper.setDefaultPropertyInclusion(
				JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL));

		// Do not write dates as timestamps
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

		// Configure custom format for Date
		mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"));

		// Configure Java 8 date/time types format (no milliseconds)
		JavaTimeModule module = new JavaTimeModule();

		module.addSerializer(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
			private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

			@Override
			public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
					throws IOException {
				gen.writeString(formatter.format(value));
			}
		});

		module.addSerializer(Instant.class, new JsonSerializer<Instant>() {
			private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
					.withZone(ZoneOffset.UTC);

			@Override
			public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
				gen.writeString(formatter.format(value));
			}
		});

		module.addSerializer(ZonedDateTime.class, new JsonSerializer<ZonedDateTime>() {
			private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

			@Override
			public void serialize(ZonedDateTime value, JsonGenerator gen, SerializerProvider serializers)
					throws IOException {
				gen.writeString(formatter.format(value));
			}
		});

		module.addSerializer(OffsetDateTime.class, new JsonSerializer<OffsetDateTime>() {
			private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

			@Override
			public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers)
					throws IOException {
				gen.writeString(formatter.format(value));
			}
		});

		mapper.registerModule(module);

		return mapper;
	}
}
