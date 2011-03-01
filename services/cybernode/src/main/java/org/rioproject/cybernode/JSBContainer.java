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
package org.rioproject.cybernode;

import net.jini.config.Configuration;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import org.rioproject.core.JSBInstantiationException;
import org.rioproject.core.OperationalStringManager;
import org.rioproject.core.ServiceBeanInstance;
import org.rioproject.core.ServiceElement;
import org.rioproject.core.provision.ServiceRecord;
import org.rioproject.event.EventHandler;
import org.rioproject.system.ComputeResource;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The JSBContainer implements support for a ServiceBeanContainer
 *
 * @author Dennis Reedy
 */
public class JSBContainer implements ServiceBeanContainer {
    /** identifier token */
    static int token = 0;
    /** Whether or not we are in a shutdown mode */
    static boolean shutdownSequence = false;
    /** The ComputeResource attribute associated to this ServiceBeanContainer */
    ComputeResource computeResource;
    /** Collection of ServiceBeanDelegates */
    final Map<Object, ServiceBeanDelegate> controllerMap =
        new HashMap<Object, ServiceBeanDelegate>();
    /**
     * Count of activations that have registered with controllerMap but activate
     * routine has not finished.
     */
    private int activationInProcessCount = 0;
    /** Uuiid for the container */
    private Uuid uuid;
    /** Collection of ServiceBeanContainerListeners */
    final List<ServiceBeanContainerListener> listeners =
        Collections.synchronizedList(new ArrayList<ServiceBeanContainerListener>());
    /** Configuration object, which is also used as the shared configuration */
    Configuration config;
    /** Logger */
    static Logger logger = CybernodeImpl.logger;

    /**
     * Create a new ServiceBeanContainer
     *
     * @param config The Configuration to use
     */
    public JSBContainer(Configuration config) {
        this.config = config;
    }

    /**
     * @see org.rioproject.cybernode.ServiceBeanContainer#getSharedConfiguration()
     */
    public Configuration getSharedConfiguration() {
        return (config);
    }

    /**
     * Set the computeResource property.
     * 
     * @param computeResource The ComputeResource to use
     */
    public void setComputeResource(ComputeResource computeResource) {
        this.computeResource = computeResource;
    }

    /**
     * @see org.rioproject.cybernode.ServiceBeanContainer#getComputeResource()
     */
    public ComputeResource getComputeResource() {
        return (computeResource);
    }

    /**
     * Terminate the ServiceBeanContainer
     */
    public void terminate() {
        shutdownSequence = true;
        terminateServices();
    }
    
    /**
     * Terminate the ServiceBeanContainer
     */
    public void terminateServices() {
        ServiceBeanDelegate[] delegates;
        synchronized(controllerMap) {
            Collection<ServiceBeanDelegate> controllers = controllerMap.values();
            delegates = controllers.toArray(new ServiceBeanDelegate[controllers.size()]);
        }
        for (ServiceBeanDelegate delegate : delegates)
            delegate.terminate();
        synchronized(controllerMap) {
            controllerMap.clear();
        }
    }

    /**
     * @see org.rioproject.cybernode.ServiceBeanContainer#getServiceRecords
     */
    public ServiceRecord[] getServiceRecords() {
        List<ServiceRecord> list = new ArrayList<ServiceRecord>();
        ServiceBeanDelegate[] delegates;
        synchronized(controllerMap) {
            Collection<ServiceBeanDelegate> controllers = controllerMap.values();
            delegates = controllers.toArray(new ServiceBeanDelegate[controllers.size()]);
        }
        for (ServiceBeanDelegate delegate : delegates) {
            ServiceRecord record = delegate.getServiceRecord();
            if (record != null)
                list.add(record);
        }
        return (list.toArray(new ServiceRecord[list.size()]));
    }

    /**
     * @see org.rioproject.cybernode.ServiceBeanContainer#getServiceCounter()
     */
    public synchronized int getServiceCounter() {
        int size;
        synchronized(controllerMap) {
            size = controllerMap.size();
        }
        return (size);
    }

    /**
     * @see org.rioproject.cybernode.ServiceBeanContainer#getActivationInProcessCount()
     */
    public int getActivationInProcessCount() {
        synchronized(controllerMap) {
            return activationInProcessCount;
        }
    }
    
    /**
     * @see org.rioproject.cybernode.ServiceBeanContainer#activate
     */
    public ServiceBeanInstance activate(ServiceElement sElem,
                                        OperationalStringManager opStringMgr,                                                                                
                                        EventHandler slaEventHandler)
    throws JSBInstantiationException {
        Uuid serviceID = UuidFactory.generate();
        Object identifier;
        synchronized(JSBContainer.class) {
            identifier = token++;
        }
        JSBDelegate delegate = new JSBDelegate(identifier, serviceID, this);
        delegate.setOperationalStringManager(opStringMgr);
        delegate.setServiceElement(sElem);
        delegate.setEventHandler(slaEventHandler);

        synchronized(controllerMap) {
            controllerMap.put(identifier, delegate);
            activationInProcessCount++;
        }
        boolean started = false;
        ServiceBeanInstance loadedInstance = null;
        try {
            loadedInstance = delegate.load();
            started = true;
            /* notification to shutdown may have come in the middle of
             * service creation, if it did, terminate */
            if(shutdownSequence) {
                delegate.terminate();
                throw new JSBInstantiationException("Resource unavailable, "+
                                                    "shutting down");
            }
            if(sElem.getAutoAdvertise())
                delegate.advertise();

        } catch(JSBInstantiationException e) {
            if(started)
                discarded(identifier);
            /* rethrow JSBInstantiationException */
            throw e;
        } catch(Throwable t) {
            if(started)
                discarded(identifier);
            logger.log(Level.SEVERE,
                       "Activating ServiceBean ["+sElem.getName()+"]",
                       t);
            throw new JSBInstantiationException("ServiceBean ["
                                                + sElem.getName()
                                                + "] "
                                                + "load failed", t, true);
        } finally {
            synchronized(controllerMap) {
                activationInProcessCount--;
            }
        }
        return (loadedInstance);
    }
    
    /**
     * @see org.rioproject.cybernode.ServiceBeanContainer#update
     */
    public void update(ServiceElement[] sElems, 
                       OperationalStringManager opStringMgr) {
        if(sElems==null)
            throw new NullPointerException("sElems is null");
        if(opStringMgr==null)
            throw new NullPointerException("opStringMgr is null");

        for (ServiceElement sElem : sElems) {
            ServiceBeanDelegate[] delegates = getDelegates(sElem);
            for (ServiceBeanDelegate delegate : delegates)
                delegate.update(sElem, opStringMgr);
        }
    }

    /**
     * @see org.rioproject.cybernode.ServiceBeanContainer#getServiceBeanInstances
     */
    public ServiceBeanInstance[] getServiceBeanInstances(ServiceElement element) {
        List<ServiceBeanInstance> list = new ArrayList<ServiceBeanInstance>();
        ServiceBeanDelegate[] delegates = getDelegates(element);
        for (ServiceBeanDelegate delegate : delegates) {
            ServiceBeanInstance instance =
                delegate.getServiceBeanInstance();
            if (instance != null)
                list.add(instance);
        }
        return (list.toArray(new ServiceBeanInstance[list.size()]));
    }

    public void setUuid(Uuid uuid) {
        this.uuid = uuid;
    }

    public Uuid getUuid() {
        return uuid;
    }

    /*
    * Get all ServiceBeanDelegate instances for a ServiceElement
    */
    public ServiceBeanDelegate[] getDelegates(ServiceElement sElem) {
        ServiceBeanDelegate[] delegates = getDelegates();
        if(sElem!=null) {
            ArrayList<ServiceBeanDelegate> list = new ArrayList<ServiceBeanDelegate>();
            for (ServiceBeanDelegate delegate : delegates) {
                if (delegate.getServiceElement().equals(sElem))
                    list.add(delegate);
            }
            delegates = list.toArray(new ServiceBeanDelegate[list.size()]);
        }
        return(delegates);
    }

    /**
     * @see org.rioproject.cybernode.ServiceBeanContainer#started(Object)
     */
    public void started(Object identifier) {
        ServiceBeanDelegate delegate;
        synchronized(controllerMap) {
            delegate = controllerMap.get(identifier);
        }
        if(delegate == null)
            return;
        ServiceRecord record = delegate.getServiceRecord();
        if(record==null) {
            logger.warning("ServiceRecord for " +
                           "["+delegate.getServiceElement().getName()+"] " +
                           "is null, no way to notify container of " +
                           "instantiation");
            return;
        }
        notifyOnInstantiation(delegate.getServiceRecord());
    }

    /**
     * @see org.rioproject.cybernode.ServiceBeanContainer#discarded(Object)
     */
    public void discarded(Object identifier) {
        ServiceBeanDelegate delegate;
        synchronized(controllerMap) {
            delegate = controllerMap.get(identifier);
        }
        if(delegate == null)
            return;
        notifyOnDiscard(delegate.getServiceRecord());
    }

    /**
     * @see org.rioproject.cybernode.ServiceBeanContainer#remove(Object)
     */
    public void remove(Object identifier) {
        if(shutdownSequence)
            return;
        synchronized(controllerMap) {
            controllerMap.remove(identifier);
        }
    }

    /**
     * @see org.rioproject.cybernode.ServiceBeanContainer#addListener
     */
    public synchronized void addListener(ServiceBeanContainerListener l) {
        if(!listeners.contains(l))
            listeners.add(l);
    }

    /**
     * @see org.rioproject.cybernode.ServiceBeanContainer#addListener
     */
    public synchronized void removeListener(ServiceBeanContainerListener l) {
        listeners.remove(l);
    }

    public ServiceBeanDelegate getServiceBeanDelegate(Uuid serviceUuid) {
        ServiceBeanDelegate delegate = null;
        ServiceBeanDelegate[] delegates = getDelegates();
        for(ServiceBeanDelegate d : delegates) {
            if(d.getServiceBeanInstance()!=null &&
               d.getServiceBeanInstance().getServiceBeanID().equals(serviceUuid)) {
                delegate = d;
                break;
            }
        }
        return delegate;
    }

    /*
     * Notify all <code>ServiceBeanContainerListener</code> objects that a
     * ServiceBean has just been instantiatied
     */
    void notifyOnInstantiation(ServiceRecord serviceRecord) {
        ServiceBeanContainerListener[] scl;
        synchronized(this) {
            scl = listeners.toArray(new ServiceBeanContainerListener[listeners.size()]);
        }
        for(ServiceBeanContainerListener l : scl)
            l.serviceInstantiated(serviceRecord);
    }

    /*
     * Notify all <code>ServiceBeanContainerListener</code> objects that a
     * ServiceBean has just been discarded
     */
    void notifyOnDiscard(ServiceRecord serviceRecord) {
        Object[] arrLocal = listeners.toArray();
        for(int i = arrLocal.length - 1; i >= 0; i--)
            ((ServiceBeanContainerListener)arrLocal[i]).
                                               serviceDiscarded(serviceRecord);
    }

    ServiceBeanDelegate[] getDelegates() {
        ServiceBeanDelegate[] delegates;
        synchronized(controllerMap) {
            Collection<ServiceBeanDelegate> controllers = controllerMap.values();
            delegates = controllers.toArray(new ServiceBeanDelegate[controllers.size()]);
        }

        return delegates;
    }

}
