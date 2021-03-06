/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.test.embedded.domain.xml;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;

import junit.framework.Assert;

import org.jboss.arquillian.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.model.AbstractDomainModelUpdate;
import org.jboss.as.model.AbstractHostModelUpdate;
import org.jboss.as.model.AbstractServerModelUpdate;
import org.jboss.as.model.DomainModel;
import org.jboss.as.model.Element;
import org.jboss.as.model.HostModel;
import org.jboss.as.model.ModelXmlParsers;
import org.jboss.as.model.Namespace;
import org.jboss.as.model.ServerModel;
import org.jboss.as.test.modular.utils.ShrinkWrapUtils;
import org.jboss.modules.ModuleLoadException;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.staxmapper.XMLMapper;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that the models generated by our standard configs can be serialized.
 *
 * @author Brian Stansberry
 */
@RunWith(Arquillian.class)
public class StandardConfigsSerializationUnitTestCase {

    @Deployment
    public static Archive<?> getDeployment(){
        return ShrinkWrapUtils.createJavaArchive("domain-xml/standard-serialization.jar", StandardConfigsSerializationUnitTestCase.class);
    }

    @Test
    public void testHost() throws Exception {
        URL url = getXmlUrl("domain/configuration/host.xml");
        Reader reader = getReader(url);
        HostModel model = parseHost(reader);
        serializeDeserialize(model);
    }

    @Test
    public void testDomain() throws Exception {
        URL url = getXmlUrl("domain/configuration/domain.xml");
        Reader reader = getReader(url);
        DomainModel model = parseDomain(reader);
        serializeDeserialize(model);
    }

    @Test
    public void testStandalone() throws Exception {
        URL url = getXmlUrl("standalone/configuration/standalone.xml");
        Reader reader = getReader(url);
        ServerModel model = parseServer(reader);
        serializeDeserialize(model);
    }

    private DomainModel parseDomain(final Reader reader) throws ModuleLoadException {
        final XMLMapper mapper = XMLMapper.Factory.create();
        registerStandardDomainReaders(mapper);
        try {
            final List<AbstractDomainModelUpdate<?>> domainUpdates = new ArrayList<AbstractDomainModelUpdate<?>>();
            mapper.parseDocument(domainUpdates, XMLInputFactory.newInstance().createXMLStreamReader(new BufferedReader(reader)));
            final DomainModel domainModel = new DomainModel();
            for(final AbstractDomainModelUpdate<?> update : domainUpdates) {
                domainModel.update(update);
            }
            return domainModel;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Caught exception during processing of domain.xml", e);
        }
    }

    private HostModel parseHost(final Reader reader) throws ModuleLoadException {
        final XMLMapper mapper = XMLMapper.Factory.create();
        registerStandardHostReaders(mapper);
        try {
            final List<AbstractHostModelUpdate<?>> hostUpdates = new ArrayList<AbstractHostModelUpdate<?>>();
            mapper.parseDocument(hostUpdates, XMLInputFactory.newInstance().createXMLStreamReader(new BufferedReader(reader)));
            final HostModel hostModel = new HostModel();
            for(final AbstractHostModelUpdate<?> update : hostUpdates) {
                hostModel.update(update);
            }
            return hostModel;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Caught exception during processing of host.xml", e);
        }
    }

    private ServerModel parseServer(final Reader reader) throws ModuleLoadException {
        final XMLMapper mapper = XMLMapper.Factory.create();
        registerStandardServerReaders(mapper);
        try {
            final List<AbstractServerModelUpdate<?>> serverUpdates = new ArrayList<AbstractServerModelUpdate<?>>();
            mapper.parseDocument(serverUpdates, XMLInputFactory.newInstance().createXMLStreamReader(new BufferedReader(reader)));
            final ServerModel serverModel = new ServerModel();
            for(final AbstractServerModelUpdate<?> update : serverUpdates) {
                serverModel.update(update);
            }
            return serverModel;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Caught exception during processing of standalone.xml", e);
        }
    }

    private void serializeDeserialize(Object object) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        try {
            oos.writeObject(object);
            oos.close();
        }
        catch (Exception e) {
            Assert.fail(e.getMessage());
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);

        Object deserialized = null;
        try {
            deserialized = ois.readObject();
        }
        finally {
            ois.close();
        }

        Assert.assertEquals(object.getClass().getName(), deserialized.getClass().getName());
    }

    private synchronized void registerStandardDomainReaders(XMLMapper mapper) throws ModuleLoadException {
        mapper.registerRootElement(new QName(Namespace.CURRENT.getUriString(), Element.DOMAIN.getLocalName()), ModelXmlParsers.DOMAIN_XML_READER);
    }

    private synchronized void registerStandardHostReaders(XMLMapper mapper) throws ModuleLoadException {
        mapper.registerRootElement(new QName(Namespace.CURRENT.getUriString(), Element.HOST.getLocalName()), ModelXmlParsers.HOST_XML_READER);
    }

    private synchronized void registerStandardServerReaders(XMLMapper mapper) throws ModuleLoadException {
        mapper.registerRootElement(new QName(Namespace.CURRENT.getUriString(), Element.SERVER.getLocalName()), ModelXmlParsers.SERVER_XML_READER);
    }

    private URL getXmlUrl(String xmlName) throws MalformedURLException {
        // user.dir will point to the root of this module
        File f = new File(getASHome());
        f = new File(f, xmlName);
        return f.toURI().toURL();
    }

    private Reader getReader(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        InputStream is = connection.getInputStream();
        InputStreamReader isr = new InputStreamReader(is);
        return isr;
    }

    private static String getASHome() {
       File f = new File(".");
       f = f.getAbsoluteFile();
       while(f.getParentFile() != null) {
          if("testsuite".equals(f.getName())) {
             Assert.assertNotNull("Expected to find a parent directory for " + f.getAbsolutePath(), f.getParentFile());
             f = f.getParentFile();
             f = new File(f, "build");
             Assert.assertTrue("The server 'build' dir exists", f.exists());
             f = new File(f, "target");
             File[] children = f.listFiles();
             f = null;
             if (children != null)
                 for (File child : children)
                     if (child.getName().startsWith("jboss-"))
                         f = child;

             if(f == null || !f.exists())
                 Assert.fail("The server hasn't been built yet.");
             Assert.assertTrue("The server 'build/target' dir exists", f.exists());
             return f.getAbsolutePath();
          } else {
             f = f.getParentFile();
          }
       }
       return null;
    }
}