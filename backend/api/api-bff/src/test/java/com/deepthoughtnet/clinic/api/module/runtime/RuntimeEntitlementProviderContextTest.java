package com.deepthoughtnet.clinic.api.module.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.deepthoughtnet.clinic.api.module.ModuleEntitlementInterceptor;
import com.deepthoughtnet.clinic.api.module.ModuleRouteRegistry;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementService;
import com.deepthoughtnet.clinic.identity.service.TenantSubscriptionService;
import com.deepthoughtnet.clinic.platform.core.config.CommercialRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ComponentScan;

class RuntimeEntitlementProviderContextTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void routingProviderIsPrimaryAndInterceptorResolvesThroughIt() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LegacyTenantRuntimeEntitlementProvider.class);
            assertThat(context).hasSingleBean(CommercialTenantRuntimeEntitlementProvider.class);
            assertThat(context).hasSingleBean(FeatureFlagTenantRuntimeEntitlementProvider.class);
            assertThat(context).hasSingleBean(ModuleEntitlementInterceptor.class);
            assertThat(context).getBeans(TenantRuntimeEntitlementProvider.class).hasSize(3);

            ConfigurableApplicationContext applicationContext = context.getSourceApplicationContext();
            ConfigurableListableBeanFactory beanFactory = applicationContext.getBeanFactory();

            assertThat(beanFactory.getBeanDefinition("legacyTenantRuntimeEntitlementProvider").isPrimary()).isFalse();
            assertThat(beanFactory.getBeanDefinition("commercialTenantRuntimeEntitlementProvider").isPrimary()).isFalse();
            assertThat(beanFactory.getBeanDefinition("featureFlagTenantRuntimeEntitlementProvider").isPrimary()).isTrue();
            assertThat(context.getBean(TenantRuntimeEntitlementProvider.class)).isInstanceOf(FeatureFlagTenantRuntimeEntitlementProvider.class);
            assertThat(context.getBean(ModuleEntitlementInterceptor.class)).isNotNull();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = ModuleEntitlementInterceptor.class)
    static class TestConfig {
        @Bean
        TenantSubscriptionService tenantSubscriptionService() {
            return mock(TenantSubscriptionService.class);
        }

        @Bean
        CommercialEffectiveEntitlementService commercialEffectiveEntitlementService() {
            return mock(CommercialEffectiveEntitlementService.class);
        }

        @Bean
        CommercialRuntimeProperties commercialRuntimeProperties() {
            return new CommercialRuntimeProperties();
        }

        @Bean
        ModuleRouteRegistry moduleRouteRegistry() {
            return mock(ModuleRouteRegistry.class);
        }
    }
}
