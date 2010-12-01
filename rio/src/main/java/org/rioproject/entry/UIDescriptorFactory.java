/*
 * Copyright 2008 the original author or authors.
 * Copyright 2005 Sun Microsystems, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rioproject.entry;

import net.jini.lookup.entry.UIDescriptor;
import net.jini.lookup.ui.MainUI;
import net.jini.lookup.ui.attribute.UIFactoryTypes;
import net.jini.lookup.ui.factory.JComponentFactory;
import net.jini.lookup.ui.factory.JDialogFactory;
import net.jini.lookup.ui.factory.JFrameFactory;
import net.jini.lookup.ui.factory.JWindowFactory;
import net.jini.url.httpmd.HttpmdUtil;
import org.rioproject.resources.serviceui.UIComponentFactory;
import org.rioproject.resources.serviceui.UIDialogFactory;
import org.rioproject.resources.serviceui.UIFrameFactory;
import org.rioproject.resources.serviceui.UIWindowFactory;

import java.io.IOException;
import java.net.URL;
import java.rmi.MarshalledObject;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;

/**
 * A helper utility that creates a UIDescriptor as part of the ServiceUI project.
 *
 * @author Dennis Reedy
 */
public class UIDescriptorFactory {

   /**
     * Get a UIDescriptor for a JComponent
     *
     * @param codebase The codebase
     * @param jarName The jar name
     * @param className The classname
     * @return A UIDescriptor
     * @throws IOException if the MarshalledObject cannot be created
     */
    public static UIDescriptor getJComponentDesc(String codebase,
                                                 String jarName,
                                                 String className)
        throws IOException {
        if(jarName == null)
            throw new NullPointerException("jarName is null");
        return (getJComponentDesc(codebase,
                                  new String[]{jarName},
                                  className));
    }

    /**
     * Get a UIDescriptor for a JComponent
     *
     * @param codebase The codebase
     * @param jars The jars to use
     * @param className The classname
     * @return A UIDescriptor
     * @throws IOException if the MarshalledObject cannot be created
     */
    public static UIDescriptor getJComponentDesc(String codebase,
                                                 String[] jars,
                                                 String className)
        throws IOException {
        if(codebase == null)
            throw new NullPointerException("codebase is null");
        if(jars == null)
            throw new NullPointerException("jars are null");
        if(className == null)
            throw new NullPointerException("className is null");
        UIDescriptor desc = null;
        try {
            desc = getUIDescriptor(MainUI.ROLE,
                                   JComponentFactory.TYPE_NAME,
                                   codebase,
                                   jars,
                                   className,
                                   false,
                                   null);
        } catch(NoSuchAlgorithmException e) {
            /* will not happen */
        }
        return (desc);
    }

    /**
     * Get a UIDescriptor for a JComponent using HTTPMD support
     *
     * @param codebase The codebase
     * @param jarName The jar name
     * @param className The classname
     * @param algorithm The algorithm to use
     * @return A UIDescriptor
     * @throws NoSuchAlgorithmException If the algorithm to use is not found
     * @throws IOException if the MarshalledObject cannot be created
     */
    public static UIDescriptor getJComponentDesc(String codebase,
                                                 String jarName,
                                                 String className,
                                                 String algorithm)
        throws IOException, NoSuchAlgorithmException {
        if(codebase == null)
            throw new NullPointerException("codebase is null");
        if(jarName == null)
            throw new NullPointerException("jarName is null");
        if(className == null)
            throw new NullPointerException("className is null");
        return (getUIDescriptor(MainUI.ROLE,
                                JComponentFactory.TYPE_NAME,
                                codebase,
                                jarName,
                                className,
                                true,
                                algorithm));
    }

    /**
     * Get a UIDescriptor for a JComponent using HTTPMD support
     *
     * @param codebase The codebase
     * @param jars The jars to use
     * @param className The classname
     * @param algorithm The algorithm to use
     * @return A UIDescriptor
     * @throws NoSuchAlgorithmException If the algorithm to use is not found
     * @throws IOException if the MarshalledObject cannot be created
     */
    public static UIDescriptor getJComponentDesc(String codebase,
                                                 String[] jars,
                                                 String className,
                                                 String algorithm)
        throws IOException, NoSuchAlgorithmException {
        if(codebase == null)
            throw new NullPointerException("codebase is null");
        if(jars == null)
            throw new NullPointerException("jars are null");
        if(className == null)
            throw new NullPointerException("className is null");
        return (getUIDescriptor(MainUI.ROLE,
                                JComponentFactory.TYPE_NAME,
                                codebase,
                                jars,
                                className,
                                true,
                                algorithm));
    }

    /**
     * Get a UIDescriptor for a JFrame
     *
     * @param codebase The codebase
     * @param jarName The jar name
     * @param className The classname
     *
     * @return A UIDescriptor
     * @throws IOException if the MarshalledObject cannot be created
     */
    public static UIDescriptor getJFrameDesc(String codebase,
                                             String jarName,
                                             String className)
        throws IOException {
        if(codebase == null)
            throw new NullPointerException("codebase is null");
        if(jarName == null)
            throw new NullPointerException("jarName is null");
        if(className == null)
            throw new NullPointerException("className is null");
        UIDescriptor desc = null;
        try {
            desc = getUIDescriptor(MainUI.ROLE,
                                   JFrameFactory.TYPE_NAME,
                                   codebase,
                                   jarName,
                                   className,
                                   false,
                                   null);
        } catch(NoSuchAlgorithmException e) {
            /* will not happen */
        }
        return (desc);
    }

    /**
     * Get a UIDescriptor for a JFrame
     *
     * @param codebase The codebase
     * @param jars The jars to use
     * @param className The classname
     * @return A UIDescriptor
     * @throws IOException if the MarshalledObject cannot be created
     */
    public static UIDescriptor getJFrameDesc(String codebase,
                                             String[] jars,
                                             String className)
    throws IOException {
        if(codebase==null)
            throw new NullPointerException("codebase is null");
        if(jars==null)
            throw new NullPointerException("jars are null");
        if(className==null)
            throw new NullPointerException("className is null");
        UIDescriptor desc = null;
        try {
            desc = getUIDescriptor(MainUI.ROLE,
                                   JFrameFactory.TYPE_NAME,
                                   codebase,
                                   jars,
                                   className,
                                   false,
                                   null);
        } catch (NoSuchAlgorithmException e) {
            /* will not happen */
        }
        return(desc);
    }


    /**
     * Get a UIDescriptor for a JFrame using HTTPMD support
     *
     * @param codebase The codebase
     * @param jarName The jar name
     * @param className The classname
     * @param algorithm The algorithm to use
     * @return A UIDescriptor
     * @throws NoSuchAlgorithmException If the algorithm to use is not found
     * @throws IOException if the MarshalledObject cannot be created
     */
    public static UIDescriptor getJFrameDesc(String codebase,
                                             String jarName,
                                             String className,
                                             String algorithm)
        throws IOException, NoSuchAlgorithmException {
        if(codebase == null)
            throw new NullPointerException("codebase is null");
        if(jarName == null)
            throw new NullPointerException("jarName is null");
        if(className == null)
            throw new NullPointerException("className is null");
        return (getUIDescriptor(MainUI.ROLE,
                                JFrameFactory.TYPE_NAME,
                                codebase,
                                jarName,
                                className,
                                true,
                                algorithm));
    }

    /**
     * Get a UIDescriptor for a JFrame using HTTPMD support
     *
     * @param codebase The codebase
     * @param jars The jars to use
     * @param className The classname
     * @param algorithm The algorithm to use
     * @return A UIDescriptor
     * @throws NoSuchAlgorithmException If the algorithm to use is not found
     * @throws IOException if the MarshalledObject cannot be created
     */
    public static UIDescriptor getJFrameDesc(String codebase,
                                             String[] jars,
                                             String className,
                                             String algorithm)
        throws IOException, NoSuchAlgorithmException {
        if(codebase == null)
            throw new NullPointerException("codebase is null");
        if(jars == null)
            throw new NullPointerException("jars are null");
        if(className == null)
            throw new NullPointerException("className is null");
        return(getUIDescriptor(MainUI.ROLE,
                               JFrameFactory.TYPE_NAME,
                               codebase,
                               jars,
                               className,
                               true,
                               algorithm));
    }

    /**
     * Get a UIDescriptor
     *
     * @param role The role
     * @param typeName The type
     * @param codebase The codebase
     * @param jarName The jar name
     * @param className The classname
     * @return A UIDescriptor
     * @throws IOException if the MarshalledObject cannot be created
     */
    public static UIDescriptor getUIDescriptor(String role,
                                               String typeName,
                                               String codebase,
                                               String jarName,
                                               String className)
        throws IOException {
        if(role == null)
            throw new NullPointerException("role is null");
        if(typeName == null)
            throw new NullPointerException("typeName is null");
        if(codebase == null)
            throw new NullPointerException("codebase is null");
        if(jarName == null)
            throw new NullPointerException("jarName is null");
        if(className == null)
            throw new NullPointerException("className is null");
        UIDescriptor desc = null;
        try {
            desc = getUIDescriptor(role,
                                   typeName,
                                   codebase,
                                   jarName,
                                   className,
                                   false,
                                   null);
        } catch(NoSuchAlgorithmException e) {
            /* will not happen */
        }
        return (desc);
    }
    
    /**
     * Get a UIDescriptor
     * 
     * @param role The role
     * @param typeName The type
     * @param codebase The codebase
     * @param jarName The jar name
     * @param className The classname
     * @param computeHttpmd Whether to compute message digest
     * @param algorithm The algorithm to use
     * @return A UIDescriptor
     * @throws NoSuchAlgorithmException If the algorithm to use is not found
     * @throws IOException if the MarshalledObject cannot be created
     */
    public static UIDescriptor getUIDescriptor(String role,
                                               String typeName,
                                               String codebase,
                                               String jarName,
                                               String className,
                                               boolean computeHttpmd,
                                               String algorithm) 
    throws NoSuchAlgorithmException, IOException {

        if(jarName==null)
            throw new NullPointerException("jarName is null");
        return(getUIDescriptor(role,
                               typeName,
                               codebase,
                               new String[]{jarName},
                               className,
                               computeHttpmd,
                               algorithm));
    }

    /**
     * Get a UIDescriptor
     *
     * @param role The role
     * @param typeName The type
     * @param codebase The codebase
     * @param jars The jars to use
     * @param className The classname
     * @param computeHttpmd Whether to compute message digest
     * @param algorithm The algorithm to use
     * @return A UIDescriptor
     * @throws NoSuchAlgorithmException If the algorithm to use is not found
     * @throws IOException if the MarshalledObject cannot be created
     */
    public static UIDescriptor getUIDescriptor(String role,
                                               String typeName,
                                               String codebase,
                                               String[] jars,
                                               String className,
                                               boolean computeHttpmd,
                                               String algorithm)
        throws NoSuchAlgorithmException, IOException {
        if(role == null)
            throw new NullPointerException("role is null");
        if(typeName == null)
            throw new NullPointerException("typeName is null");
        if(codebase == null)
            throw new NullPointerException("codebase is null");
        if(jars == null)
            throw new NullPointerException("jars are null");
        if(className == null)
            throw new NullPointerException("className is null");
        UIDescriptor desc = new UIDescriptor();
        desc.role = role;
        UIFactoryTypes types  ;
        MarshalledObject factory;
        URL[] urls = new URL[jars.length];
        if(computeHttpmd) {
            String httpmdCodebase = "httpmd://"+codebase.substring(7);
            algorithm = (algorithm == null?"sha":algorithm);
            for(int i = 0; i < urls.length; i++) {
                URL tempURL =
                    new URL(codebase+(codebase.endsWith("/")?"":"/")+jars[i]);
                String digest = HttpmdUtil.computeDigest(tempURL, algorithm);
                urls[i] = new URL(httpmdCodebase+jars[i]+";"+
                                  algorithm+"="+digest);
            }
        } else {
            for(int i = 0; i < urls.length; i++) {
                urls[i] =
                    new URL(codebase+(codebase.endsWith("/")?"":"/")+jars[i]);
            }
        }
        if(typeName.equals(JComponentFactory.TYPE_NAME)) {
            types = new UIFactoryTypes(
                Collections.singleton(JComponentFactory.TYPE_NAME));
            factory =
                new MarshalledObject<UIComponentFactory>(new UIComponentFactory(urls, className));
            desc.toolkit = JComponentFactory.TOOLKIT;
        } else if(typeName.equals(JDialogFactory.TYPE_NAME)) {
            types = new UIFactoryTypes(
                Collections.singleton(JDialogFactory.TYPE_NAME));
            factory = new MarshalledObject<UIDialogFactory>(new UIDialogFactory(urls, className));
            desc.toolkit = JDialogFactory.TOOLKIT;
        } else if(typeName.equals(JFrameFactory.TYPE_NAME)) {
            types = new UIFactoryTypes(
                Collections.singleton(JFrameFactory.TYPE_NAME));
            factory = new MarshalledObject<UIFrameFactory>(new UIFrameFactory(urls, className));
            desc.toolkit = JFrameFactory.TOOLKIT;
        } else if(typeName.equals(JWindowFactory.TYPE_NAME)) {
            types = new UIFactoryTypes(
                Collections.singleton(JWindowFactory.TYPE_NAME));
            factory = new MarshalledObject<UIWindowFactory>(new UIWindowFactory(urls, className));
            desc.toolkit = JWindowFactory.TOOLKIT;
        } else {
            throw new IllegalArgumentException("unknown typeName "+typeName);
        }
        desc.attributes = Collections.singleton(types);
        desc.factory = factory;
        return (desc);
    }
    
    /**
     * Get a UIDescriptor for a JComponentFactory
     * 
     * @param role The role
     * @param factory A JComponentFactory
     * 
     * @return A UIDescriptor
     * 
     * @throws IOException if the MarshalledObject cannot be created
     */
    public static UIDescriptor getUIDescriptor(String role,
                                               JComponentFactory factory)
        throws IOException {
        if(role==null)
            throw new NullPointerException("role is null");
        if(factory==null)
            throw new NullPointerException("factory is null");
        UIDescriptor desc = new UIDescriptor();
        desc.role = role;
        desc.toolkit = JComponentFactory.TOOLKIT;
        desc.attributes = 
            Collections.singleton(
                     new UIFactoryTypes(
                              Collections.singleton(JComponentFactory.TYPE_NAME)));
        desc.factory = new MarshalledObject<JComponentFactory>(factory);
        return (desc);
    }

    /**
     * Get a UIDescriptor for a JDialogFactory
     * 
     * @param role The role
     * @param factory A JDialogFactory
     * 
     * @return A UIDescriptor
     * 
     * @throws IOException if the MarshalledObject cannot be created
     */
    public static UIDescriptor getUIDescriptor(String role,
                                               JDialogFactory factory)
        throws IOException {
        if(role==null)
            throw new NullPointerException("role is null");
        if(factory==null)
            throw new NullPointerException("factory is null");
        UIDescriptor desc = new UIDescriptor();
        desc.role = role;
        desc.toolkit = JDialogFactory.TOOLKIT;
        desc.attributes = 
            Collections.singleton(
                     new UIFactoryTypes(
                              Collections.singleton(JDialogFactory.TYPE_NAME)));
        desc.factory = new MarshalledObject<JDialogFactory>(factory);
        return (desc);
    }

    /**
     * Get a UIDescriptor for a JFrameFactory
     * @param role The role
     * @param factory A JFrameFactory
     * 
     * @return A UIDescriptor
     * 
     * @throws IOException if the MarshalledObject cannot be created
     */
    public static UIDescriptor getUIDescriptor(String role,
                                               JFrameFactory factory)
        throws IOException {
        if(role==null)
            throw new NullPointerException("role is null");
        if(factory==null)
            throw new NullPointerException("factory is null");
        UIDescriptor desc = new UIDescriptor();
        desc.role = role;
        desc.toolkit = JFrameFactory.TOOLKIT;
        desc.attributes = 
           Collections.singleton(
                    new UIFactoryTypes(
                             Collections.singleton(JFrameFactory.TYPE_NAME)));
        desc.factory = new MarshalledObject<JFrameFactory>(factory);
        return (desc);
    }

    public static UIDescriptor getUIDescriptor(String role,
                                               JWindowFactory factory)
        throws IOException {
        if(role==null)
            throw new NullPointerException("role is null");
        if(factory==null)
            throw new NullPointerException("factory is null");
        UIDescriptor desc = new UIDescriptor();
        desc.role = role;
        desc.toolkit = JWindowFactory.TOOLKIT;
        desc.attributes = 
            Collections.singleton(
                     new UIFactoryTypes(
                              Collections.singleton(JWindowFactory.TYPE_NAME)));
        desc.factory = new MarshalledObject<JWindowFactory>(factory);
        return (desc);
    }
}
