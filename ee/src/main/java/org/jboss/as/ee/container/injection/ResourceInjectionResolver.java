/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.ee.container.injection;

import java.util.Collection;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceName;

/**
 * Responsible for converting {@link ResourceInjectionConfiguration} instances into {@link ResourceInjection} as well
 * as resolving all the service dependencies for the injection.
 *
 * @author John Bailey
 */
public interface ResourceInjectionResolver {

    /**
     * Resolve the injection and the service dependencies for this resource.
     *
     * @param deploymentUnit  The current deployment unit
     * @param beanName The bean name
     * @param beanClass The bean class
     * @param configuration   The resource configuration
     * @return The resolved results
     */
    ResolverResult resolve(final DeploymentUnit deploymentUnit, final String beanName, final Class<?> beanClass, final ResourceInjectionConfiguration configuration);

    /**
     * Container object for all the information necessary to properly setup a bean container injection.
     */
    interface ResolverResult {
        /**
         * The injection object.
         *
         * @return The injection
         */
        ResourceInjection getInjection();

        /**
         * The service name of the context to bind the resource.
         *
         * @return The context service name
         */
        ServiceName getBindContextName();

        /**
         * The local name to use when binding the resource
         *
         * @return The local bind name
         */
        String getBindName();

        /**
         * The target name representing the value of the injection.
         *
         * @return The target name
         */
        String getBindTargetName();

        /**
         * The dependencies generated by this resource injection.
         *
         * @return The dependencies
         */
        Collection<ResolverDependency<?>> getDependencies();

        /**
         * Determines whether the injection should be bound into JNDI.
         *
         * @return {@code true} if the binding should take place, {@code false} otherwise.
         */
        boolean shouldBind();
    }

    /**
     * Holder object for the information required to establish a proper resolved dependency.
     *
     * @param <T> The injection type type
     */
    interface ResolverDependency<T> {
        /**
         * The service name for the dependency
         *
         * @return The dependency name
         */
        ServiceName getServiceName();

        /**
         * The injector (if available) for this dependency
         *
         * @return The injector
         */
        Injector<T> getInjector();

        /**
         * The injection type (if the injector is available) of this dependency.
         *
         * @return The injection type
         */
        Class<T> getInjectorType();
    }

}
