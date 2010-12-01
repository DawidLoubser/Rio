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
package org.rioproject.resources.servicecore;

import net.jini.core.constraint.RemoteMethodControl;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.id.ReferentUuid;
import net.jini.id.ReferentUuids;
import net.jini.id.Uuid;
import org.rioproject.jsb.JSBControlException;
import org.rioproject.core.ServiceElement;

import java.io.*;
import java.rmi.RemoteException;

/**
 * A <code>ServiceAdminProxy</code> is a proxy for the ServiceAdmin server. This is 
 * the object passed to clients of the ServiceAdmin.
 *
 * @author Dennis Reedy
 */
public class ServiceAdminProxy implements ServiceAdmin, 
                                          ReferentUuid,
                                          Serializable {

    private static final long serialVersionUID = 2L;  
    /** The backend */
    final protected ServiceAdmin serviceAdmin;
    /** The unique identifier for this proxy */
    final protected Uuid uuid;
    
    /**
     * Creates a ServiceAdmin proxy, returning an instance that implements 
     * RemoteMethodControl if the server does too.
     *
     * @param serviceAdmin The ServiceAdmin server
     * @param id The Uuid of the ServiceAdmin
     *
     * @return An instance of the ServiceAdminProxy
     */
    static ServiceAdminProxy getInstance(ServiceAdmin serviceAdmin, Uuid id) {
        if(serviceAdmin instanceof RemoteMethodControl) {
            return new ConstrainableServiceAdminProxy(serviceAdmin, id, null);
        } else {
            return(new ServiceAdminProxy(serviceAdmin, id));
        }
    }

    /*
     * Private constructor
     */
    protected ServiceAdminProxy(ServiceAdmin serviceAdmin, Uuid uuid) {
        if(serviceAdmin == null) {
            throw new NullPointerException("serviceAdmin cannot be null");
        } else if(uuid == null) {
            throw new NullPointerException("uuid cannot be null");
        }
        this.serviceAdmin = serviceAdmin;
        this.uuid = uuid;
    }    

    /* -------- Implement ServiceAdmin methods -------- */

    /** @see org.rioproject.resources.servicecore.ServiceAdmin#getJoinSet */
    public ServiceRegistrar[] getJoinSet() throws RemoteException {
        return(serviceAdmin.getJoinSet());
    }

    /* -------- Implement org.rioproject.core.jsb.ServiceBeanAdmin methods -------- */

    /** @see org.rioproject.core.jsb.ServiceBeanAdmin#getServiceElement */
    public ServiceElement getServiceElement() throws RemoteException {
        return(serviceAdmin.getServiceElement());
    }
    
    /** @see org.rioproject.core.jsb.ServiceBeanAdmin#setServiceElement */
    public void setServiceElement(ServiceElement sElem) throws RemoteException {
        serviceAdmin.setServiceElement(sElem);
    }
        
    /** @see org.rioproject.core.jsb.ServiceBeanAdmin#getUpTime */
    public long getUpTime() throws RemoteException {
        return(serviceAdmin.getUpTime());
    }

    public Uuid getServiceBeanInstantiatorUuid() throws RemoteException {
        return serviceAdmin.getServiceBeanInstantiatorUuid();
    }

    /* -------- Implement org.rioproject.jsb.ServiceBeanControl methods -------- */

    /** @see org.rioproject.jsb.ServiceBeanControl#start */
    public Object start() throws JSBControlException, RemoteException {
        return(serviceAdmin.start());
    }

    /** @see org.rioproject.jsb.ServiceBeanControl#stop */
    public void stop(boolean force) throws JSBControlException, RemoteException {
        serviceAdmin.stop(force);
    }

    /** @see org.rioproject.jsb.ServiceBeanControl#advertise */
    public void advertise() throws JSBControlException, RemoteException {
        serviceAdmin.advertise();
    }

    /** @see org.rioproject.jsb.ServiceBeanControl#unadvertise */
    public void unadvertise() throws JSBControlException, RemoteException {
        serviceAdmin.unadvertise();
    }

    /* -------- Implement net.jini.admin.JoinAdmin methods -------- */
    
    /** @see net.jini.admin.JoinAdmin#getLookupAttributes */
    public Entry[] getLookupAttributes() throws RemoteException {
        return(serviceAdmin.getLookupAttributes());
    }
    
    /** @see net.jini.admin.JoinAdmin#addLookupAttributes */
    public void addLookupAttributes(Entry[] attrSets) throws RemoteException {
        serviceAdmin.addLookupAttributes(attrSets);
    }
    
    /** @see net.jini.admin.JoinAdmin#modifyLookupAttributes */
    public void modifyLookupAttributes(Entry[] attrSetTemplates, 
            Entry[] attrSets) throws RemoteException {
        serviceAdmin.modifyLookupAttributes(attrSetTemplates, attrSets);
    }
    
    /** @see net.jini.admin.JoinAdmin#getLookupGroups */
    public String[] getLookupGroups() throws RemoteException {
        return(serviceAdmin.getLookupGroups());
    }
    
    /** @see net.jini.admin.JoinAdmin#addLookupGroups */
    public void addLookupGroups(String[] groups) throws RemoteException {
        serviceAdmin.addLookupGroups(groups);
    }
    
    /** @see net.jini.admin.JoinAdmin#removeLookupGroups */
    public void removeLookupGroups(String[] groups) throws RemoteException {
        serviceAdmin.removeLookupGroups(groups);
    }
    
    /** @see net.jini.admin.JoinAdmin#setLookupGroups */
    public void setLookupGroups(String[] groups) throws RemoteException {
        serviceAdmin.setLookupGroups(groups);
    }
    
    /** @see net.jini.admin.JoinAdmin#getLookupLocators */
    public LookupLocator[] getLookupLocators() throws RemoteException {
        return(serviceAdmin.getLookupLocators());
    }
    
    /** @see net.jini.admin.JoinAdmin#addLookupLocators */
    public void addLookupLocators(LookupLocator[] locators)throws RemoteException {
        serviceAdmin.addLookupLocators(locators);
    }
    
    /** @see net.jini.admin.JoinAdmin#removeLookupLocators */
    public void removeLookupLocators(LookupLocator[] locators) throws RemoteException {
        serviceAdmin.removeLookupLocators(locators);
    }
    
    /** @see net.jini.admin.JoinAdmin#setLookupLocators */
    public void setLookupLocators(LookupLocator[] locators) throws RemoteException {
        serviceAdmin.setLookupLocators(locators);
    }
    
    /* -- Implement com.sun.jini.admin.DestroyAdmin -- */
    
    /** @see com.sun.jini.admin.DestroyAdmin#destroy */
    public void destroy() throws RemoteException {
        serviceAdmin.destroy();
    }
    
    /**
     * Proxies for servers with the same uuid have the same hash code.
     */
    public int hashCode() {
        return (uuid.hashCode());
    }

    /**
     * Proxies for servers with the same <code>uuid</code> are considered
     * equal.
     */
    public boolean equals(Object o) {        
        return (ReferentUuids.compare(this, o));
    }        
    
    /* -------- Implement net.jini.id.ReferentUuid methods -------- */
    /** @see net.jini.id.ReferentUuid#getReferentUuid */
    public Uuid getReferentUuid() {
        return (uuid);
    }
    
    /**
     * When an instance of this class is deserialized, this method is
     * atomatically invoked. This implementation of this method validates the
     * state of the deserialized instance.
     * @param s The ObjectInputSTream to use
     * 
     * @throws InvalidObjectException if the state of the
     * deserialized instance of this class is found to be invalid.
     * @throws ClassNotFoundException if a class is not found during
     * deserialization
     */
    private void readObject(ObjectInputStream s) throws IOException,
            ClassNotFoundException {
        s.defaultReadObject();
        /* Verify server */
        if(serviceAdmin == null) {
            throw new InvalidObjectException(
                    "ServiceAdminProxy.readObject failure - "
                            + "serviceAdmin field is null");
        }
        /* Verify uuid */
        if(uuid == null) {
            throw new InvalidObjectException("ServiceAdminProxy.uuid failure - "
                    + "uuid field is null");
        }
    }

    /**
     * During deserialization of an instance of this class, if it is found that
     * the stream contains no data, this method is automatically invoked.
     * Because it is expected that the stream should always contain data, this
     * implementation of this method simply declares that something must be
     * wrong.
     * 
     * @throws InvalidObjectException to indicate that there was
     * no data in the stream during deserialization of an instance of this
     * class; declaring that something is wrong.
     */
    private void readObjectNoData() throws ObjectStreamException {
        throw new InvalidObjectException("No data found when attempting to "
                + "deserialize ServiceAdminProxy instance");
    }
}
