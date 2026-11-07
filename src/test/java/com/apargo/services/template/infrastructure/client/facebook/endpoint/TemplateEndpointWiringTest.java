package com.apargo.services.template.infrastructure.client.facebook.endpoint;

import com.apargo.services.template.infrastructure.client.account.TemplateRoutePolicy;
import com.apargo.services.template.infrastructure.config.properties.FacebookClientProperties;
import com.apargo.services.template.infrastructure.config.properties.TemplateProviderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** The template endpoints wire as the application wires them, from the shipped yaml (doc 47 R4). */
class TemplateEndpointWiringTest {

    @Configuration
    @EnableConfigurationProperties({FacebookClientProperties.class, TemplateProviderProperties.class})
    @Import({MetaTemplateEndpoint.class, PinnacleTemplateEndpoint.class, TemplateEndpointRegistry.class,
            TemplateRoutePolicy.class})
    static class Wiring {
    }

    @Test
    void bothEndpointsRegister() {
        new ApplicationContextRunner()
                .withUserConfiguration(Wiring.class)
                .withPropertyValues(
                        "facebook-service.base-url=https://graph.facebook.com",
                        "facebook-service.api-version=v23.0",
                        "template.providers.pinnacle.base-url=https://partnersv1.pinbot.ai",
                        "template.providers.pinnacle.api-version=v3")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    TemplateEndpointRegistry registry = context.getBean(TemplateEndpointRegistry.class);
                    assertThat(registry.has("META_DIRECT")).isTrue();
                    assertThat(registry.has("PINNACLE")).isTrue();
                    assertThat(context).hasSingleBean(TemplateRoutePolicy.class);
                });
    }

    @Test
    void theShippedYamlKeepsPinnacleTemplatesOff() throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"))) {
            sources.addLast(source);
        }
        TemplateProviderProperties properties = new Binder(ConfigurationPropertySources.from(sources),
                new PropertySourcesPlaceholdersResolver(sources))
                .bind("template", TemplateProviderProperties.class).get();

        TemplateProviderProperties.Provider pinnacle = properties.find("PINNACLE").orElseThrow();
        assertThat(pinnacle.isEnabled()).isFalse();
        assertThat(pinnacle.getBaseUrl()).isEqualTo("https://partnersv1.pinbot.ai");
        assertThat(pinnacle.getApiVersion()).isEqualTo("v3");
        assertThat(pinnacle.routes("1433508011972817")).isFalse();
    }
}
