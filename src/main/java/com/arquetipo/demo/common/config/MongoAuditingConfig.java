package com.arquetipo.demo.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Activa el poblado automatico de campos anotados con {@code @CreatedDate} /
 * {@code @LastModifiedDate} en los documentos de MongoDB.
 */
@Configuration
@EnableMongoAuditing
public class MongoAuditingConfig {
}
