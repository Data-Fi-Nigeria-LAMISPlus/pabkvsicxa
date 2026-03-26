package com.lamiplus_common_api.api;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.persistence.EntityManagerFactory;


@EnableTransactionManagement
public abstract class BasePluginJpaConfig {

    @Bean(name = "transactionManager")
    public PlatformTransactionManager transactionManager(
            // Explicitly qualify to the plugin-local EMF singleton we registered.
            // Without @Qualifier, Spring might walk up to the parent context and
            // find the core application's entityManagerFactory instead.
            @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory) {

        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory);
        return transactionManager;


    }
}