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

package org.jboss.as.connector.deployers.processors;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.jca.common.api.metadata.ds.DataSources;

/**
 * Utility used to manage attaching an instance of {@link org.jboss.jca.common.api.metadata.ds.DataSources}.
 *
 * @author John Bailey
 */
public class DataSourcesAttachement {
    private static final AttachmentKey<DataSources> ATTACHMENT_KEY = AttachmentKey.create(DataSources.class);

    static DataSources getDataSourcesAttachment(final DeploymentUnit context) {
        return context.getAttachment(ATTACHMENT_KEY);
    }

    static void attachDataSources(final DeploymentUnit context, final DataSources dataSources) {
        context.putAttachment(ATTACHMENT_KEY, dataSources);
    }

    static void detachDataSources(final DeploymentUnit context) {
        context.removeAttachment(ATTACHMENT_KEY);
    }
}
