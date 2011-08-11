/*
 * Copyright to the original author or authors.
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
package org.rioproject.cybernode;

import net.jini.config.Configuration;
import net.jini.config.EmptyConfiguration;
import net.jini.id.UuidFactory;
import org.rioproject.boot.BootUtil;
import org.rioproject.deploy.ServiceBeanInstance;
import org.rioproject.deploy.ServiceBeanInstantiationException;
import org.rioproject.opstring.*;
import org.rioproject.resources.client.LookupCachePool;
import org.rioproject.system.ComputeResource;

import java.io.File;
import java.rmi.RMISecurityManager;
import java.security.*;
import java.util.*;

/**
 * A simple container for instantiating service beans. This utility can be used
 * to create a service bean either from a classname, or from an
 * <tt>OperationalString</tt> document. If the latter is used, the service bean
 * is created using the attributes contained within the <tt>OperationalString</tt>
 * document.
 *
 * <p>The <tt>StaticCybernode</tt> returns the implementation (back-end) object
 * for the service bean(s) that have been created. This allows direct
 * manipulation of the implementation, allowing debugging and unit testing to occur
 * easily.
 * 
 * <p>Notes:<br>
 * <ul>
 * <li>This utility expects that all necessary classes are in the
 * classpath of the JVM in order to activate the service bean(s).</li>
 * <li>If associations have been declared (with setter properties) the
 * injection of associated services is undefined at this time.</li>
 * <li><tt>OperationalString</tt> attributes relating to provisioning and
 * SLAs are undefined at this time
 * <li>The StaticCybernode does not support the instantiation of forked services.</li>
 * </ul>
 *
 * @author Dennis Reedy
 */
public class StaticCybernode {
    private JSBContainer instantiator;
    private final Map<Object, Object> serviceMap = new HashMap<Object, Object>();

    static {
        Policy.setPolicy(
            new Policy() {
                public PermissionCollection getPermissions(CodeSource codesource) {
                    Permissions perms = new Permissions();
                    perms.add(new AllPermission());
                    return(perms);
                }
                public void refresh() {
                }

            });
        System.setSecurityManager(new RMISecurityManager());
        System.setProperty("StaticCybernode", "true");
    }

    public StaticCybernode()  {
        try {
            Configuration config = EmptyConfiguration.INSTANCE;
            instantiator = new JSBContainer(config);
            ComputeResource cr = new ComputeResource();
            String provisionRoot = Environment.setupProvisionRoot(true, config);
            cr.setPersistentProvisioningRoot(provisionRoot);
            cr.boot();
            instantiator.setComputeResource(cr);
        } catch (Exception e) {
            e.printStackTrace();
        }
        instantiator.setUuid(UuidFactory.generate());
        LookupCachePool.getInstance().setServiceBeanContainer(instantiator);
    }

    /**
     * Shutdown & terminate the StaticCybernode
     */
    public void destroy() {
        instantiator.terminateServices();
    }

    /**
     * Activate a service bean.
     *  
     * @param classname The service bean class to create. 
     * @return The service bean implementation, initialized by the
     * <tt>Cybernode</tt>. The service bean will have been instantiated with
     * an empty configuration.
     * 
     * @throws org.rioproject.deploy.ServiceBeanInstantiationException If the service bean cannot be created
     */
    public Object activate(String classname) throws ServiceBeanInstantiationException {
        if(classname==null)
            throw new IllegalArgumentException("classname must not be null");
        ServiceBeanInstance instance =
            instantiator.activate(makeServiceElement(classname),
                                  null,  // OperationalStringManager
                                  null); // EventHandler (slas)
        JSBDelegate delegate =
            (JSBDelegate) instantiator.getServiceBeanDelegate(instance.getServiceBeanID());
        return delegate.getImpl();
    }

    /**
     * Activate service beans defined in an <tt>OperationalString</tt> document,
     * scoping the beans to be activated by providing the bean names.
     * 
     * @param opstring The <tt>OperationalString</tt> document
     * @param beans The bean names to create. If not provided all
     * services in the opstring will be created
     * @return A map of bean name keys and service implementation object values
     * 
     * @throws Exception If the <tt>OperationalString</tt> document results in
     * parsing errors or the bean(s) cannot be created.
     */
    public Map<String, Object> activate(File opstring, String... beans) throws Exception {
         if(opstring==null)
            throw new IllegalArgumentException("opstring file must not be null");
        Map<String, Object> map = new HashMap<String, Object>();
        OpStringLoader opl = new OpStringLoader();
        OperationalString[] opstrings = opl.parseOperationalString(opstring);
        for(OperationalString ops : opstrings) {
            if(beans!=null && beans.length>0)
                map.putAll(activate(ops, beans));
            else
                map.putAll(activate(ops));
        }
        return map;
    }

    /** 
     * Activate service beans defined in an {@link org.rioproject.opstring.OperationalString},
     * scoping the beans to be activated by providing the bean names.
     *
     * @param opstring The <tt>OperationalString</tt> document
     * @param beans The bean names to create. If not provided all
     * services in the opstring will be created
     * @return A map of bean name keys and service implementation object values
     *
     * @throws org.rioproject.deploy.ServiceBeanInstantiationException If the bean(s) cannot be created.
     */
    public Map<String, Object> activate(OperationalString opstring, String... beans)
        throws ServiceBeanInstantiationException {
        if(opstring==null)
            throw new IllegalArgumentException("opstring must not be null");
        Map<String, Object> map = new HashMap<String, Object>();
        for(ServiceElement elem : opstring.getServices()) {
            for(String bean : beans) {
                if(elem.getName().equals(bean)) {
                    map.put(elem.getName(), instantiateBean(elem));
                }
            }
        }
        for(OperationalString nested : opstring.getNestedOperationalStrings()) {
            activate(nested, beans);
        }
        return map;
    }

    /**
     * Activate all service beans defined in an
     * {@link org.rioproject.opstring.OperationalString}
     *
     * @param opstring The <tt>OperationalString</tt> document
     * @return A map of bean name keys and service implementation object values
     *
     * @throws org.rioproject.deploy.ServiceBeanInstantiationException If the bean(s) cannot be created.
     */
    public Map<String, Object> activate(OperationalString opstring)
        throws ServiceBeanInstantiationException {
        Map<String, Object> map = new HashMap<String, Object>();
        for(ServiceElement elem : opstring.getServices()) {
            map.put(elem.getName(), instantiateBean(elem));
        }
        return map;
    }

    /**
     * Get the proxy for a service implementation created by the
     * <tt>StaticCybernode</tt>
     *
     * @param impl The service implementation, obtained through the
     * <tt>activate</tt> method
     * @return The proxy for the service, or null if the service implementation
     * has not been created by the <tt>StaticCybernode</tt>
     *
     * @throws IllegalArgumentException if the impl parameter is null
     */
    public Object getServiceProxy(Object impl)  {
        if(impl==null)
            throw new IllegalArgumentException("impl must not be null");
        return serviceMap.get(impl);
    }

    private Object instantiateBean(ServiceElement elem) throws
                                                        ServiceBeanInstantiationException {
        if(elem.forkService())
            throw new ServiceBeanInstantiationException("The StaticCybernode does not " +
                                                "support the instantiation of a " +
                                                "service declared to be forked");
        ServiceBeanInstance instance =
            instantiator.activate(elem,
                                  null,  // OperationalStringManager
                                  null); // EventHandler (slas)
        JSBDelegate delegate =
            (JSBDelegate) instantiator.getServiceBeanDelegate(instance.getServiceBeanID());
        Object impl = delegate.getImpl();
        serviceMap.put(impl, delegate.getProxy());
        return impl;
    }

    private static String[] parseBeans(String beans) {
        return BootUtil.toArray(beans);
    }

    private ServiceElement makeServiceElement(String implClass) {
        ServiceElement elem = new ServiceElement();
        ClassBundle main = new ClassBundle(implClass);
        elem.setComponentBundle(main);
        ServiceBeanConfig sbc = new ServiceBeanConfig();
        String name = implClass;
        int ndx = implClass.lastIndexOf(".");
        if(ndx>0)
            name = implClass.substring(ndx+1);
        sbc.setName(name);
        elem.setServiceBeanConfig(sbc);
        return elem;
    }

    /**
     * The <tt>StaticCybernode</tt> can be invoked directly from the command
     * line. The <tt>StaticCybernode</tt> expects that all necessary classes
     * are in the classpath of the JVM in order to activate the service
     * bean(s). This includes the Rio, River (Jini) and Groovy jars, as well as
     * any specific application classes.
     *
     * <p>Invoking the <tt>StaticCybernode</tt> is done as follows:
     * <pre>
     *  Usage:
     *      org.rioproject.cybernode.StaticCybernode service-class-name | opstring-file [bean-names]
     * </pre>
     * <p>The <tt>StaticCybernode</tt>  takes either a
     * <table style="text-align: left; width: 100%;" border="1" cellpadding="2"
     * cellspacing="2">
     * <tbody>
     * <tr>
     * <td style="vertical-align: top;"><b>Argument</b><br>
     * </td>
     * <td style="vertical-align: top;"><b>Description</b><br>
     * </td>
     * </tr>
     * <tr>
     * <td style="vertical-align: top;">service-class-name<br>
     * </td>
     * <td style="vertical-align: top;">The fully qualified class name
     * of the service bean to instantiate. The service bean will be created
     * with an <i>empty</i> configuration<br>
     * </td>
     * </tr>
     * <tr>
     * <td style="vertical-align: top;">opstring-file<br>
     * </td>
     * <td style="vertical-align: top;">The OperationalString document
     * (either .xml or .groovy) declaring service beans and service bean
     * attributes.<br>
     * </td>
     * </tr>
     * <tr>
     * <td style="vertical-align: top;">bean-names<br>
     * </td>
     * <td style="vertical-align: top;">Optional comma-separated list of
     * beans to create within the provided opstring-file<br>
     * </td>
     * </tr>
     * </tbody>
     * </table>
     * 
     * @param args Either a <tt>service-class-name</tt> or the location of an
     * <tt>opstring-file</tt> with optional <tt>bean-names</tt> 
     */
    public static void main(String... args) {
        if(args.length==0) {
            StringBuffer sb = new StringBuffer();
            sb.append("Usage: \n");
            sb.append("\t")
                .append(StaticCybernode.class.getName())
                .append(" service-class-name | opstring-file [bean-names]\n");
            System.out.println(sb.toString());
            System.exit(1);
        }
        try {
            StaticCybernode sbc = new StaticCybernode();
            List<String> options = new ArrayList<String>(Arrays.asList(args));
            String option = options.get(0);
            if(option.endsWith(".xml") || option.endsWith(".groovy")) {
                options.remove(option);
                String[] beans = null;
                if(options.size()>0)
                    beans = parseBeans(options.get(0));
                sbc.activate(new File(args[0]), beans);
            }
            else {
                sbc.activate(args[0]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
