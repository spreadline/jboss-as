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

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class SubsystemRemove extends AbstractModelElementUpdate<ProfileElement> {

    private static final long serialVersionUID = -5436702014420361771L;

    private final String namespaceUri;

    /**
     * Construct a new instance.
     *
     * @param namespaceUri the namespace URI for the corresponding subsystem
     */
    protected SubsystemRemove(final String namespaceUri) {
        this.namespaceUri = namespaceUri;
    }

    /**
     * {@inheritDoc}
     */
    public AbstractSubsystemAdd<?> getCompensatingUpdate(final ProfileElement original) {
        return original.getSubsystem(namespaceUri).getAdd();
    }

    /** {@inheritDoc} */
    public Class<ProfileElement> getModelElementType() {
        return ProfileElement.class;
    }

    /** {@inheritDoc} */
    protected void applyUpdate(final ProfileElement element) throws UpdateFailedException {
        final AbstractSubsystemElement<?> subsystem = element.getSubsystem(namespaceUri);
        if (! subsystem.isEmpty()) {
            throw new UpdateFailedException("Subsystem " + namespaceUri + " configuration is not empty");
        }
        element.removeSubsystem(namespaceUri);
    }

    /**
     * Get the namespace URI for this remove.
     *
     * @return the namespace URI
     */
    public String getNamespaceUri() {
        return namespaceUri;
    }
}
