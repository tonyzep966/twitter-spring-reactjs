package com.gmail.merikbest2015.twitterspringreactjs.configuration;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class ProxyConfiguration {

	@Value("${proxy.enabled}")
	private Boolean proxyEnabled;

	@Value("${proxy.server.host}")
	private String proxyHost;

	@Value("${proxy.server.port}")
	private Integer proxyPort;
}
