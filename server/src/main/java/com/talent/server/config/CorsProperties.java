package com.talent.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Browser origins allowed to call the API directly (the Next.js dev server
 * proxies by default).
 */
@ConfigurationProperties("app.cors")
public record CorsProperties(@DefaultValue("http://localhost:3000") List<String> allowedOrigins) {
}
