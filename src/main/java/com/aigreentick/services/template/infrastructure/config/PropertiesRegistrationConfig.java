package com.aigreentick.services.template.infrastructure.config;

import com.aigreentick.services.template.infrastructure.config.properties.CorsProperties;
import com.aigreentick.services.template.infrastructure.config.properties.FacebookClientProperties;
import com.aigreentick.services.template.infrastructure.config.properties.InternalApiProperties;
import com.aigreentick.services.template.infrastructure.config.properties.MediaServiceProperties;
import com.aigreentick.services.template.infrastructure.config.properties.MediaSyncProperties;
import com.aigreentick.services.template.infrastructure.config.properties.WabaServiceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Single registration point for every {@code @ConfigurationProperties} class
 * in this service.
 *
 * <h2>Why register them here instead of annotating each one</h2>
 *
 * Previously these classes registered themselves — some with
 * {@code @Configuration}, one with {@code @Component}, for no reason other
 * than which one the author reached for that day. Keeping the properties
 * classes free of stereotype annotations leaves them plain POJOs a unit test
 * can construct directly, while this one class is the only place that wires
 * them into the context. Anyone auditing "what configuration does this
 * service bind" reads one file.
 *
 * <p>Add every new properties class to the list below.
 */
@Configuration
@EnableConfigurationProperties({
        FacebookClientProperties.class,
        WabaServiceProperties.class,
        MediaServiceProperties.class,
        MediaSyncProperties.class,
        CorsProperties.class,
        InternalApiProperties.class
})
public class PropertiesRegistrationConfig {
}
