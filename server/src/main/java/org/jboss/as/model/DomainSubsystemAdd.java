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

package org.jboss.as.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Add a subsystem to a domain profile.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class DomainSubsystemAdd extends AbstractDomainModelUpdate<Void> {

    private static final long serialVersionUID = -9076890219875153928L;

    private final String profileName;
    private final AbstractSubsystemAdd<?> subsystemAdd;

    /**
     * Construct a new instance.
     *
     * @param profileName the name of the profile that the change applies to
     * @param subsystemAdd the subsystem add
     */
    public DomainSubsystemAdd(final String profileName, final AbstractSubsystemAdd<?> subsystemAdd) {
        this.profileName = profileName;
        this.subsystemAdd = subsystemAdd;
    }

    /**
     * Get the profile name to add the subsystem to.
     *
     * @return the profile name
     */
    public String getProfileName() {
        return profileName;
    }

    /**
     * Get the namespace URI of the subsystem.
     *
     * @return the namespace URI
     */
    public String getNamespaceUri() {
        return subsystemAdd.getNamespaceUri();
    }

    @Override
    protected void applyUpdate(final DomainModel element) throws UpdateFailedException {
        final String namespaceUri = subsystemAdd.getNamespaceUri();
        if (! element.getProfile(profileName).addSubsystem(namespaceUri, subsystemAdd.createSubsystemElement())) {
            throw new UpdateFailedException("Subsystem '" + namespaceUri + "' is already configured in profile '" + profileName + "'");
        }
    }

    @Override
    public DomainSubsystemRemove getCompensatingUpdate(final DomainModel original) {
        return new DomainSubsystemRemove(profileName, subsystemAdd.getCompensatingUpdate(original.getProfile(profileName)));
    }

    @Override
    public ServerSubsystemAdd getServerModelUpdate() {
        return new ServerSubsystemAdd(subsystemAdd);
    }

    @Override
    public List<String> getAffectedServers(DomainModel domainModel, HostModel hostModel) throws UpdateFailedException {
        if (getServerModelUpdate() == null) {
            return Collections.emptyList();
        }
        else {
            List<String> result = new ArrayList<String>();
            for (String server : hostModel.getActiveServerNames()) {
                String serverGroupName =  hostModel.getServer(server).getServerGroup();

                if (profileName.equals(domainModel.getServerGroup(serverGroupName).getProfileName())) {
                    result.add(server);
                }
            }
            return result;
        }
    }
}
