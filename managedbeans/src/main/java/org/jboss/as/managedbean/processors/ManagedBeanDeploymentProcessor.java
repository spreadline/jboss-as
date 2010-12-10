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

package org.jboss.as.managedbean.processors;

import static org.jboss.as.naming.deployment.NamespaceBindings.getNamespaceBindings;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.naming.Context;
import javax.naming.LinkRef;
import javax.naming.Reference;

import org.jboss.as.managedbean.config.InterceptorConfiguration;
import org.jboss.as.managedbean.config.ManagedBeanConfiguration;
import org.jboss.as.managedbean.config.ManagedBeanConfigurations;
import org.jboss.as.managedbean.config.ResourceConfiguration;
import org.jboss.as.managedbean.container.FieldResourceInjection;
import org.jboss.as.managedbean.container.ManagedBeanContainer;
import org.jboss.as.managedbean.container.ManagedBeanInterceptor;
import org.jboss.as.managedbean.container.ManagedBeanObjectFactory;
import org.jboss.as.managedbean.container.ManagedBeanService;
import org.jboss.as.managedbean.container.MethodResourceInjection;
import org.jboss.as.managedbean.container.ResourceInjection;
import org.jboss.as.server.deployment.module.ModuleDeploymentProcessor;
import org.jboss.as.naming.deployment.ContextService;
import org.jboss.as.naming.deployment.DuplicateBindingException;
import org.jboss.as.naming.deployment.JndiName;
import org.jboss.as.naming.deployment.ModuleContextConfig;
import org.jboss.as.naming.deployment.NamingLookupValue;
import org.jboss.as.naming.deployment.ResourceBinder;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.modules.Module;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.Value;
import org.jboss.msc.value.Values;

/**
 * Deployment unit processors responsible for adding deployment items for each managed bean configuration.
 *
 * @author John E. Bailey
 */
public class ManagedBeanDeploymentProcessor implements DeploymentUnitProcessor {

    /**
     * Process the deployment and add a managed bean service for each managed bean configuration in the deployment.
     *
     * @param phaseContext the deployment unit context
     * @throws DeploymentUnitProcessingException
     */
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final ManagedBeanConfigurations managedBeanConfigurations = phaseContext.getAttachment(ManagedBeanConfigurations.ATTACHMENT_KEY);
        if(managedBeanConfigurations == null) {
            return; // Skip deployments with no managed beans.
        }
        final ModuleContextConfig moduleContext = phaseContext.getAttachment(ModuleContextConfig.ATTACHMENT_KEY);
        if(moduleContext == null) {
            throw new DeploymentUnitProcessingException("Unable to deploy managed beans without a module naming context");
        }

        final ServiceTarget serviceTarget = phaseContext.getServiceTarget();

        final Module module = phaseContext.getAttachment(ModuleDeploymentProcessor.MODULE_ATTACHMENT_KEY);
        final ClassLoader classLoader = module.getClassLoader();

        for(ManagedBeanConfiguration managedBeanConfiguration : managedBeanConfigurations.getConfigurations().values()) {
            processManagedBean(phaseContext.getDeploymentUnit(), classLoader, moduleContext, managedBeanConfiguration, serviceTarget);
        }
    }

    public void undeploy(DeploymentUnit context) {
    }

    private void processManagedBean(final DeploymentUnit deploymentContext, final ClassLoader classLoader, final ModuleContextConfig moduleContext, final ManagedBeanConfiguration managedBeanConfiguration, final ServiceTarget serviceTarget) throws DeploymentUnitProcessingException {
        final Class<?> beanClass = managedBeanConfiguration.getType();
        final String managedBeanName = managedBeanConfiguration.getName();

        final List<ResourceInjection<?>> resourceInjections = new ArrayList<ResourceInjection<?>>();
        final List<ManagedBeanInterceptor<?>> interceptors = new ArrayList<ManagedBeanInterceptor<?>>();
        final ManagedBeanService<?> managedBeanService = createManagedBeanService(beanClass, classLoader, managedBeanConfiguration, resourceInjections, interceptors);

        final ServiceName moduleContextServiceName = moduleContext.getContextServiceName();

        final ServiceName managedBeanServiceName = ManagedBeanService.SERVICE_NAME.append(deploymentContext.getName(), managedBeanName);
        final ServiceBuilder<?> serviceBuilder = serviceTarget.addService(managedBeanServiceName, managedBeanService);

        final ServiceName managedBeanContextServiceName = moduleContextServiceName.append(managedBeanName, "context");
        final JndiName managedBeanContextJndiName = moduleContext.getContextName().append(managedBeanName + "-context");

        // Process managed bean resources
        for (ResourceConfiguration resourceConfiguration : managedBeanConfiguration.getResourceConfigurations()) {
            final ResourceInjection<?> resourceInjection = processResource(deploymentContext, moduleContext, resourceConfiguration.getInjectedType(), resourceConfiguration, serviceTarget, serviceBuilder, managedBeanContextServiceName, managedBeanContextJndiName);
            if(resourceInjection != null) {
                resourceInjections.add(resourceInjection);
            }
        }

        // Process managed bean interceptors
        for (InterceptorConfiguration interceptorConfiguration : managedBeanConfiguration.getInterceptorConfigurations()) {
            interceptors.add(processInterceptor(interceptorConfiguration.getInterceptorClass(), deploymentContext, moduleContext, interceptorConfiguration, serviceTarget, serviceBuilder, managedBeanContextServiceName, managedBeanContextJndiName));
        }

        serviceBuilder.install();

        final ContextService actualBeanContext = new ContextService(managedBeanContextJndiName);
        serviceTarget.addService(managedBeanContextServiceName, actualBeanContext)
            .addDependency(moduleContextServiceName, Context.class, actualBeanContext.getParentContextInjector())
            .install();

        // Add an object factory reference for this managed bean
        final Reference managedBeanFactoryReference = ManagedBeanObjectFactory.createReference(beanClass, managedBeanServiceName.toString());
        final ResourceBinder<Reference> managedBeanFactoryBinder = new ResourceBinder<Reference>(moduleContext.getContextName().append(managedBeanName), Values.immediateValue(managedBeanFactoryReference));
        final ServiceName referenceBinderName = moduleContextServiceName.append(managedBeanName);
        serviceTarget.addService(referenceBinderName, managedBeanFactoryBinder)
            .addDependency(moduleContextServiceName, Context.class, managedBeanFactoryBinder.getContextInjector())
            .addDependency(managedBeanServiceName)
            .install();
    }

    private <T> ManagedBeanService<T> createManagedBeanService(final Class<T> beanClass, final ClassLoader classLoader, final ManagedBeanConfiguration managedBeanConfiguration, List<ResourceInjection<?>> resourceInjections, final List<ManagedBeanInterceptor<?>> interceptors) {
        return new ManagedBeanService<T>(new ManagedBeanContainer<T>(beanClass, classLoader, managedBeanConfiguration.getPostConstructMethods(), managedBeanConfiguration.getPreDestroyMethods(), resourceInjections, interceptors));
    }

    private <T> ResourceInjection<T> processResource(final DeploymentUnit deploymentContext, final ModuleContextConfig moduleContext, final Class<T> valueType, final ResourceConfiguration resourceConfiguration, final ServiceTarget serviceTarget, final ServiceBuilder<?> serviceBuilder, final ServiceName beanContextServiceName, final JndiName managedBeanContextJndiName) throws DeploymentUnitProcessingException {
        final JndiName localContextName = managedBeanContextJndiName.append(resourceConfiguration.getLocalContextName());
        final String targetContextName = resourceConfiguration.getTargetContextName();

        final NamingLookupValue<T> lookupValue = new NamingLookupValue<T>(localContextName);
        final ResourceInjection<T> resourceInjection = getResourceInjection(resourceConfiguration, lookupValue);

        // Now add a binder for the local context
        final ServiceName binderName = beanContextServiceName.append(localContextName.getLocalName());
        if(resourceInjection != null) {
            serviceBuilder.addDependency(binderName);
            serviceBuilder.addDependency(beanContextServiceName, Context.class, lookupValue.getContextInjector());
        }

        final LinkRef linkRef = new LinkRef(targetContextName.startsWith("java") ? targetContextName : moduleContext.getContextName().append(targetContextName).getAbsoluteName());
        final boolean shouldBind;
        try {
            shouldBind = getNamespaceBindings(deploymentContext).addBinding(localContextName, linkRef);
        } catch (DuplicateBindingException e) {
            throw new DeploymentUnitProcessingException("Unable to process managed bean resource.", e);
        }
        if(shouldBind) {
            final ResourceBinder<LinkRef> resourceBinder = new ResourceBinder<LinkRef>(localContextName, Values.immediateValue(linkRef));

            final ServiceBuilder<Object> binderServiceBuilder = serviceTarget.addService(binderName, resourceBinder);
            binderServiceBuilder.addDependency(beanContextServiceName, Context.class, resourceBinder.getContextInjector());

            if(targetContextName.startsWith("java:")) {
                binderServiceBuilder.addOptionalDependency(ResourceBinder.JAVA_BINDER.append(targetContextName));
            } else {
                binderServiceBuilder.addOptionalDependency(moduleContext.getContextServiceName().append(targetContextName));
            }
            binderServiceBuilder.install();
        }

        return resourceInjection;
    }

    private <T> ResourceInjection<T> getResourceInjection(final ResourceConfiguration resourceConfiguration, Value<T> value) {
        switch(resourceConfiguration.getTargetType()) {
            case FIELD:
                return new FieldResourceInjection<T>(Values.immediateValue(Field.class.cast(resourceConfiguration.getTarget())), value, resourceConfiguration.getInjectedType().isPrimitive());
            case METHOD:
                return new MethodResourceInjection<T>(Values.immediateValue(Method.class.cast(resourceConfiguration.getTarget())), value, resourceConfiguration.getInjectedType().isPrimitive());
            default:
                return null;
        }
    }

    private <T> ManagedBeanInterceptor<T> processInterceptor(final Class<T> interceptorType, final DeploymentUnit deploymentContext, final ModuleContextConfig moduleContext, final InterceptorConfiguration interceptorConfiguration, final ServiceTarget serviceTarget, final ServiceBuilder<?> serviceBuilder, final ServiceName managedBeanContextName, final JndiName managedBeanContextJndiName) throws DeploymentUnitProcessingException {
        final List<ResourceInjection<?>> resourceInjections = new ArrayList<ResourceInjection<?>>();

        for (ResourceConfiguration resourceConfiguration : interceptorConfiguration.getResourceConfigurations()) {
            final ResourceInjection<?> resourceInjection = processResource(deploymentContext, moduleContext, resourceConfiguration.getInjectedType(), resourceConfiguration, serviceTarget, serviceBuilder, managedBeanContextName, managedBeanContextJndiName);
            if(resourceInjection != null) {
                resourceInjections.add(resourceInjection);
            }
        }
        return new ManagedBeanInterceptor<T>(interceptorType, interceptorConfiguration.getAroundInvokeMethod(), resourceInjections);
    }
}