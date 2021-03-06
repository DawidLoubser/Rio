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
package org.rioproject.gnostic;

import org.rioproject.core.OperationalString;
import org.rioproject.core.OperationalStringManager;
import org.rioproject.core.ServiceBeanInstance;
import org.rioproject.core.ServiceElement;
import org.rioproject.monitor.DeployAdmin;
import org.rioproject.monitor.ProvisionMonitor;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Context for operating with deployed services
 */
public class DeployedServiceContext {
    private ProvisionMonitor monitor;
    private final static Map<ServiceElement, OperationalStringManager> deployed =
        new HashMap<ServiceElement, OperationalStringManager>();
    private static final Logger logger = Logger.getLogger(DeployedServiceContext.class.getName());

    void addDeployedService(ServiceElement serviceElement,
                            OperationalStringManager opMgr) {
        deployed.put(serviceElement, opMgr);
    }

    void setProvisionMonitor(ProvisionMonitor monitor) {
        this.monitor = monitor;
    }

    public Map<ServiceElement, OperationalStringManager> getDeployedServiceMap() {
        Map<ServiceElement, OperationalStringManager> map =
            new HashMap<ServiceElement, OperationalStringManager>();
        map.putAll(deployed);
        return map;
    }

    public void increment(String serviceName, String opstring) {
        Map.Entry<ServiceElement, OperationalStringManager> entry =
            getMapEntry(serviceName, opstring);
        if(entry==null) {
            entry = doLookupServiceEntry(serviceName, opstring);
            if(entry==null) {
                logger.warning("Unable to obtain ServiceElement for " +
                               "service name: "+serviceName+", opstring: "+opstring+". "+deployed);
                return;
            } else {
                deployed.put(entry.getKey(), entry.getValue());
            }
        }
        ServiceElement serviceElement = entry.getKey();
        OperationalStringManager opMgr = entry.getValue();
        try {
            opMgr.increment(serviceElement, true, null);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static Integer getServiceCount(String serviceName, String opstring) {
        Map.Entry<ServiceElement, OperationalStringManager> entry =
            getMapEntry(serviceName, opstring);
        if(entry==null)
            return 0;
        ServiceElement serviceElement = entry.getKey();
        OperationalStringManager opMgr = entry.getValue();
        int count = 0;
        try {
            count = opMgr.getServiceBeanInstances(serviceElement).length;
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return count;
    }

    public void decrement(String serviceName, String opstring) {
        /* decrement the number of services by 1 */
        Map.Entry<ServiceElement, OperationalStringManager> entry =
            getMapEntry(serviceName, opstring);
        if(entry==null)
            return;
        ServiceElement serviceElement = entry.getKey();
        OperationalStringManager opMgr = entry.getValue();
        try {
            ServiceBeanInstance[] instances = opMgr.getServiceBeanInstances(serviceElement);
            if(instances.length>0) {
                ServiceBeanInstance instance = instances[instances.length-1];
                opMgr.decrement(instance, true, true);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public <T> T getService(String serviceName, Class<T> type) {
        T service = null;
        Map.Entry<ServiceElement, OperationalStringManager> entry = getMapEntry(serviceName);
        if(entry!=null)
            service = doGetService(entry, type);
        return service;
    }

    public <T> T getService(String serviceName, String opstring, Class<T> type) {
        T service;
        Map.Entry<ServiceElement, OperationalStringManager> entry =
            getMapEntry(serviceName, opstring);
        if(entry!=null) {
            service = doGetService(entry, type);
        } else {
            service = doLookupService(serviceName, opstring, type);
        }
        return service;
    }

    public <T> List<T> getServices(String serviceName, Class<T> type) {
        List<T> services;
        Map.Entry<ServiceElement, OperationalStringManager> entry =
            getMapEntry(serviceName);
        if(entry!=null) {
            services = doGetServices(entry, type);
        } else {
            services = new ArrayList<T>();
        }
        return services;
    }

    public <T> List<T> getServices(String serviceName, String opstring, Class<T> type) {
        List<T> services;
        Map.Entry<ServiceElement, OperationalStringManager> entry =
            getMapEntry(serviceName, opstring);
        if(entry!=null) {
            services = doGetServices(entry, type);
        } else {
            services = doLookupServices(serviceName, opstring, type);
        }
        return services;
    }

    @SuppressWarnings("unchecked")
    private <T> T doGetService(Map.Entry<ServiceElement, OperationalStringManager> entry,
                               Class<T> type) {
        T service = null;
        ServiceElement serviceElement = entry.getKey();
        OperationalStringManager opMgr = entry.getValue();
        try {
            ServiceBeanInstance[] instances = opMgr.getServiceBeanInstances(serviceElement);
            if(instances.length>0)
                service = (T)instances[0].getService();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return service;
    }

    @SuppressWarnings("unchecked")
    private <T> T doLookupService(String serviceName, String opstringName, Class<T> type) {
        T service = null;
        try {
            DeployAdmin dAdmin = (DeployAdmin)monitor.getAdmin();
            OperationalStringManager opMgr = dAdmin.getOperationalStringManager(opstringName);
            OperationalString opstring = opMgr.getOperationalString();
            ServiceElement serviceElement = null;
            for(ServiceElement elem : opstring.getServices()) {
                if(elem.getName().equals(serviceName)) {
                    serviceElement = elem;
                    break;
                }
            }
            if(serviceElement!=null) {
                ServiceBeanInstance[] instances = opMgr.getServiceBeanInstances(serviceElement);
                if(instances.length>0)
                    service = (T)instances[0].getService();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return service;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> doGetServices(Map.Entry<ServiceElement, OperationalStringManager> entry,
                                      Class<T> type) {
        List<T> services = new ArrayList<T>();
        ServiceElement serviceElement = entry.getKey();
        OperationalStringManager opMgr = entry.getValue();
        try {
            ServiceBeanInstance[] instances = opMgr.getServiceBeanInstances(serviceElement);
            StringBuilder sb = new StringBuilder();
            for(ServiceBeanInstance instance : instances) {
                if(type.isAssignableFrom(instance.getService().getClass())) {
                    sb.append("\n===> ").append(type.getName()).append(" IS assignable from ").append(instance.getService().getClass().getName());
                } else {
                    sb.append("\n===> ").append(type).append(" instance [").append(instance.getServiceBeanConfig().getInstanceID()).append("]");
                    sb.append(" NOT assignable from ").append(instance.getService().getClass().getName()).append("\n");
                    for(Class c : instance.getService().getClass().getInterfaces()) {
                        sb.append("\t").append(c.getName()).append("\n");
                    }
                }
                if(logger.isLoggable(Level.FINER))
                    logger.finer(sb.toString());
                services.add((T)instance.getService());
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Getting service instances of type " + type.getName(), t);
        }
        return services;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> doLookupServices(String serviceName, String opstringName, Class<T> type) {
        List<T> services = new ArrayList<T>();
        try {
            DeployAdmin dAdmin = (DeployAdmin)monitor.getAdmin();
            OperationalStringManager opMgr = dAdmin.getOperationalStringManager(opstringName);
            OperationalString opstring = opMgr.getOperationalString();
            ServiceElement serviceElement = null;
            for(ServiceElement elem : opstring.getServices()) {
                if(elem.getName().equals(serviceName)) {
                    serviceElement = elem;
                    break;
                }
            }
            if(serviceElement!=null) {
                ServiceBeanInstance[] instances = opMgr.getServiceBeanInstances(serviceElement);
                for(ServiceBeanInstance instance : instances) {
                    services.add((T)instance.getService());
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return services;
    }

    @SuppressWarnings("unchecked")
    private Map.Entry<ServiceElement, OperationalStringManager> doLookupServiceEntry(String serviceName, String opstringName) {
        Map.Entry<ServiceElement, OperationalStringManager> entry = null;
        try {
            DeployAdmin dAdmin = (DeployAdmin)monitor.getAdmin();
            OperationalStringManager opMgr = dAdmin.getOperationalStringManager(opstringName);
            OperationalString opstring = opMgr.getOperationalString();
            ServiceElement serviceElement = null;
            for(ServiceElement elem : opstring.getServices()) {
                if(elem.getName().equals(serviceName)) {
                    serviceElement = elem;
                    break;
                }
            }
            if(serviceElement!=null) {
                entry = new AbstractMap.SimpleEntry(serviceElement, opMgr);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return entry;
    }

    private static Map.Entry<ServiceElement, OperationalStringManager> getMapEntry(String serviceName) {
        Map.Entry<ServiceElement, OperationalStringManager> entry = null;
        for(Map.Entry<ServiceElement, OperationalStringManager> e : deployed.entrySet()) {
            if(e.getKey().getName().equals(serviceName)) {
                entry = e;
                break;
            }
        }
        return entry;
    }

     private static Map.Entry<ServiceElement, OperationalStringManager> getMapEntry(String serviceName, String opstring) {
        Map.Entry<ServiceElement, OperationalStringManager> entry = null;
        for(Map.Entry<ServiceElement, OperationalStringManager> e : deployed.entrySet()) {
            if(e.getKey().getName().equals(serviceName) && e.getKey().getOperationalStringName().equals(opstring)) {
                entry = e;
                break;
            }
        }
        return entry;
    }
}
