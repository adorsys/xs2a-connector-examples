/*
 * Copyright 2018-2018 adorsys GmbH & Co KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.adorsys.ledgers.consent.standalone.config;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.jpa.HibernateEntityManagerFactory;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HibernateListenerConfig {
    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    private final ServiceInstanceIdEventListener serviceInstanceIdEventListener;

    public HibernateListenerConfig(ServiceInstanceIdEventListener serviceInstanceIdEventListener) {
		this.serviceInstanceIdEventListener = serviceInstanceIdEventListener;
	}


	@PostConstruct
    public void registerListeners() {
        if (entityManagerFactory instanceof HibernateEntityManagerFactory) {
            final HibernateEntityManagerFactory hibernateEntityManagerFactory = (HibernateEntityManagerFactory) entityManagerFactory;
            final SessionFactoryImpl sessionFactory = (SessionFactoryImpl) hibernateEntityManagerFactory.getSessionFactory();

            final EventListenerRegistry registry = sessionFactory.getServiceRegistry()
                                                       .getService(EventListenerRegistry.class);

            registry.getEventListenerGroup(EventType.PRE_INSERT)
                .appendListener(serviceInstanceIdEventListener);
        }
    }
}
