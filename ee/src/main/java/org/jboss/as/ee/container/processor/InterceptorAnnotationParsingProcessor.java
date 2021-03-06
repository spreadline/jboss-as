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

package org.jboss.as.ee.container.processor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptors;
import javax.interceptor.InvocationContext;
import org.jboss.as.ee.container.BeanContainerConfiguration;
import org.jboss.as.ee.container.interceptor.MethodInterceptorAllFilter;
import org.jboss.as.ee.container.interceptor.MethodInterceptorConfiguration;
import org.jboss.as.ee.container.interceptor.MethodInterceptorMatchFilter;
import org.jboss.as.ee.container.interceptor.MethodInterceptorFilter;
import org.jboss.as.ee.container.service.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

/**
 * Deployment processor responsible for analyzing each attached {@link org.jboss.as.ee.container.BeanContainerConfiguration} instance to configure
 * required method interceptors.
 *
 * @author John Bailey
 */
public class InterceptorAnnotationParsingProcessor implements DeploymentUnitProcessor {
    private static final DotName INTERCEPTORS_ANNOTATION_NAME = DotName.createSimple(Interceptors.class.getName());
    private static final DotName AROUND_INVOKE_ANNOTATION_NAME = DotName.createSimple(AroundInvoke.class.getName());

    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final List<BeanContainerConfiguration> containerConfigs = deploymentUnit.getAttachment(Attachments.BEAN_CONTAINER_CONFIGS);
        if (containerConfigs == null || containerConfigs.isEmpty()) {
            return;
        }

        final CompositeIndex index = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.COMPOSITE_ANNOTATION_INDEX);
        if (index == null) {
            return;
        }

        for (BeanContainerConfiguration containerConfig : containerConfigs) {
            final ClassInfo classInfo = index.getClassByName(DotName.createSimple(containerConfig.getBeanClass()));
            containerConfig.addMethodInterceptorConfigs(getInterceptorConfigs(classInfo, index));
        }
    }

    public void undeploy(DeploymentUnit context) {
    }

    private List<MethodInterceptorConfiguration> getInterceptorConfigs(final ClassInfo classInfo, final CompositeIndex index) {
        final Map<DotName, List<AnnotationInstance>> classAnnotations = classInfo.annotations();
        if (classAnnotations == null) {
            return Collections.emptyList();
        }

        final List<AnnotationInstance> interceptorAnnotations = classAnnotations.get(INTERCEPTORS_ANNOTATION_NAME);
        if (interceptorAnnotations == null || interceptorAnnotations.isEmpty()) {
            return Collections.emptyList();
        }

        final List<MethodInterceptorConfiguration> interceptorConfigurations = new ArrayList<MethodInterceptorConfiguration>(interceptorAnnotations.size());

        for (AnnotationInstance annotationInstance : interceptorAnnotations) {

            final AnnotationValue value = annotationInstance.value();
            if(value != null) for (Type interceptorClass : value.asClassArray()) {
                final ClassInfo interceptorClassInfo = index.getClassByName(interceptorClass.name());
                if (interceptorClassInfo == null) {
                    continue; // TODO: Process without index info
                }

                final AnnotationTarget target = annotationInstance.target();
                final MethodInterceptorFilter methodFilter;
                if (target instanceof MethodInfo) {
                    final MethodInfo methodInfo = MethodInfo.class.cast(target);
                    final List<String> argTypes = new ArrayList<String>(methodInfo.args().length);
                    for (Type argType : methodInfo.args()) {
                        argTypes.add(argType.name().toString());
                    }
                    methodFilter = new MethodInterceptorMatchFilter(methodInfo.name(), argTypes.toArray(new String[argTypes.size()]));
                } else {
                    methodFilter = MethodInterceptorAllFilter.INSTANCE;
                }

                final MethodInfo methodInfo = getAroundInvokeMethod(interceptorClassInfo);
                interceptorConfigurations.add(new MethodInterceptorConfiguration(annotationInstance.name().toString(), methodInfo.name(), acceptsInvocationContext(interceptorClassInfo, methodInfo), methodFilter));
            }
        }

        //Look for any @AroundInvoke methods on bean class
        final MethodInfo methodInfo = getAroundInvokeMethod(classInfo);
        if (methodInfo != null) {
            interceptorConfigurations.add(new MethodInterceptorConfiguration(classInfo.name().toString(), methodInfo.name(), acceptsInvocationContext(classInfo, methodInfo), MethodInterceptorAllFilter.INSTANCE));
        }
        return interceptorConfigurations;
    }

    private MethodInfo getAroundInvokeMethod(final ClassInfo classInfo) {
        final Map<DotName, List<AnnotationInstance>> classAnnotations = classInfo.annotations();
        final List<AnnotationInstance> instances = classAnnotations.get(AROUND_INVOKE_ANNOTATION_NAME);
        if (instances == null || instances.isEmpty()) {
            return null;
        }

        if (instances.size() > 1) {
            throw new IllegalArgumentException("Only one method may be annotated with " + AROUND_INVOKE_ANNOTATION_NAME + " per interceptor.");
        }

        final AnnotationTarget target = instances.get(0).target();
        if (!(target instanceof MethodInfo)) {
            throw new IllegalArgumentException(AROUND_INVOKE_ANNOTATION_NAME + " is only valid on method targets.");
        }
        return MethodInfo.class.cast(target);
    }

    private boolean acceptsInvocationContext(final ClassInfo classInfo, final MethodInfo methodInfo) {
        final Type[] args = methodInfo.args();
        switch (args.length) {
            case 0:
                return false;
            case 1:
                if (!InvocationContext.class.getName().equals(args[0].name().toString())) {
                    throw new IllegalArgumentException("Invalid argument type.  Methods annotated with " + AROUND_INVOKE_ANNOTATION_NAME + " must have either no parameter or a single InvocationContext argument.");
                }
                return true;
            default:
                throw new IllegalArgumentException("Invalid number of arguments for method " + methodInfo.name() + " annotated with " + AROUND_INVOKE_ANNOTATION_NAME + " on class " + classInfo.name());
        }
    }
}
