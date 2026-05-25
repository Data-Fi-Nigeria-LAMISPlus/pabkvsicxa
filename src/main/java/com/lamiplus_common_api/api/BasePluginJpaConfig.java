package com.lamiplus_common_api.api;

import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableTransactionManagement
public abstract class BasePluginJpaConfig {
    // No @Bean methods — entityManagerFactory and transactionManager
    // are registered programmatically by GlobalPluginService via
    // PluginEmfFactoryBean and emfRegistry.createLiveTransactionManager()
    // BEFORE context.refresh() is called.
}