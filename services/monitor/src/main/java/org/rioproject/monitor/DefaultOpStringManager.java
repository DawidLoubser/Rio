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
package org.rioproject.monitor;

import com.sun.jini.proxy.BasicProxyTrustVerifier;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.EmptyConfiguration;
import net.jini.export.Exporter;
import net.jini.id.Uuid;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.security.BasicProxyPreparer;
import net.jini.security.ProxyPreparer;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;
import org.rioproject.config.ExporterConfig;
import org.rioproject.core.*;
import org.rioproject.core.provision.DeployedService;
import org.rioproject.core.provision.DeploymentMap;
import org.rioproject.core.provision.ServiceBeanInstantiator;
import org.rioproject.core.provision.ServiceStatement;
import org.rioproject.monitor.persistence.StateManager;
import org.rioproject.monitor.tasks.*;
import org.rioproject.opstring.OAR;
import org.rioproject.opstring.OpString;
import org.rioproject.resolver.RemoteRepository;
import org.rioproject.resolver.ResolverHelper;
import org.rioproject.resources.servicecore.ServiceResource;
import org.rioproject.resources.util.ThrowableUtil;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The DefaultOpStringManager provides the management for an OperationalString that
 * has been deployed to the ProvisionMonitor
 */
public class DefaultOpStringManager implements OperationalStringManager, OpStringManager, ServerProxyTrust {
    /** Component name we use to find items in the configuration */
    static final String CONFIG_COMPONENT = "org.rioproject.monitor";
    /** Logger */
    static final String LOGGER = "org.rioproject.monitor";
    /** ProvisionMonitor logger. */
    static Logger logger = Logger.getLogger(LOGGER);

    private OperationalString opString;
    /**
     * Collection of ServiceElementManager instances
     */
    private final List<ServiceElementManager> svcElemMgrs = new ArrayList<ServiceElementManager>();
    /**
     * Collection of nested DefaultOpStringManager instances
     */
    private final List<OpStringManager> nestedManagers = new ArrayList<OpStringManager>();
    /**
     * The DefaultOpStringManager parents for this DefaultOpStringManager
     */
    private final List<OpStringManager> parents = new ArrayList<OpStringManager>();
    /**
     * Property that indicates the mode of the DefaultOpStringManager. If active is
     * true, the DefaultOpStringManager will inform it's ServiceElementmanager
     * instances to actively provision services. If active is false, the
     * DefaultOpStringManager will inform its ServiceElementManager instances to
     * keep track of the service described by it's ServiceElement object but
     * not issue provision requests
     */
    private Boolean active = true;
    /**
     * The Exporter for the OperationalStringManager
     */
    private Exporter exporter;
    /**
     * Object supporting remote semantics required for an
     * OperationalStringManager
     */
    private OperationalStringManager proxy;
    /**
     * A List of scheduled TimerTasks
     */
    private final List<TimerTask> scheduledTaskList = Collections.synchronizedList(new ArrayList<TimerTask>());
    /**
     * A list a deployed Dates
     */
    private final List<Date> deployDateList = Collections.synchronizedList(new ArrayList<Date>());
    /**
     * Local copy of the deployment status of the OperationalString
     */
    private int deployStatus;
    /**
     * ProxyPreparer for ServiceProvisionListener proxies
     */
    private ProxyPreparer serviceProvisionListenerPreparer;
    /** The associated OperationalString archive (if any) */
    private OAR oar;
    /** The service proxy for the ProvisionMonitor */
    private ProvisionMonitor serviceProxy;
    private Configuration config;
    private ProvisionMonitorEventProcessor eventProcessor;
    private OpStringMangerController opStringMangerController;
    private StateManager stateManager;
    private DeploymentVerifier deploymentVerifier = new DeploymentVerifier();
    private ServiceProvisioner provisioner;
    private Uuid uuid;

    /**
     * Create an DefaultOpStringManager, making it available to receive incoming
     * calls supporting the OperationalStringManager interface
     *
     * @param opString The OperationalString to manage
     * @param parent   The DefaultOpStringManager parent. May be null
     * @param mode     Whether the OperationalStringManager is the active manager
     * @param config   Configuration object
     * @param opStringMangerController The managing entity for OpStringManagers
     * @throws java.rmi.RemoteException if the DefaultOpStringManager cannot export itself
     */
    public DefaultOpStringManager(OperationalString opString,
                                  OpStringManager parent,
                                  boolean mode,
                                  Configuration config,
                                  OpStringMangerController opStringMangerController) throws RemoteException {

        this.config = config;
        this.opStringMangerController = opStringMangerController;
        Exporter defaultExporter = new BasicJeriExporter(TcpServerEndpoint.getInstance(0), new BasicILFactory());
        config = (config == null ? EmptyConfiguration.INSTANCE : config);
        try {
            exporter = ExporterConfig.getExporter(config, CONFIG_COMPONENT, "opStringManagerExporter", defaultExporter);
            if (logger.isLoggable(Level.FINER))
                logger.finer("Deployment [" + opString.getName() + "] using exporter " + exporter);

            /* Get the ProxyPreparer for ServiceProvisionListener instances */
            serviceProvisionListenerPreparer = (ProxyPreparer) config.getEntry(CONFIG_COMPONENT,
                                                                               "serviceProvisionListenerPreparer",
                                                                               ProxyPreparer.class,
                                                                               new BasicProxyPreparer());
        } catch (ConfigurationException e) {
            logger.log(Level.WARNING, "Getting opStringManager Exporter", e);
        }

        proxy = (OperationalStringManager) exporter.export(this);
        this.opString = opString;
        this.active = mode;
        if (parent != null) {
            addParent(parent);
            parent.addNested(this);
        }
        if (opString.loadedFrom() != null &&
            opString.loadedFrom().toExternalForm().startsWith("file") &&
            opString.loadedFrom().toExternalForm().endsWith(".oar")) {
            File f = new File(opString.loadedFrom().getFile());
            try {
                oar = new OAR(f);
                oar.setDeployDir(f.getParent());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Could no create OAR", e);
            }
        }
    }

    void setServiceProxy(ProvisionMonitor serviceProxy) {
        this.serviceProxy = serviceProxy;
    }

    void setEventProcessor(ProvisionMonitorEventProcessor eventProcessor) {
        this.eventProcessor = eventProcessor;
    }

    void setStateManager(StateManager stateManager) {
        this.stateManager = stateManager;
    }

    /**
     * @see org.rioproject.monitor.OpStringManager#getProxy()
     */
    public OperationalStringManager getProxy() {
        return (proxy);
    }

    /**
     * Set the active mode. If the new mode is not equal to the old mode,
     * iterate through the Collection of ServiceElementManager instances and
     * set their mode to be equal to the DefaultOpStringManager mode
     *
     * @param newActive the new mode
     */
    public void setActive(boolean newActive) {
        synchronized (this) {
            if (active != newActive) {
                active = newActive;
                List<ServiceElement> list = new ArrayList<ServiceElement>();
                ServiceElementManager[] mgrs = getServiceElementManagers();
                for (ServiceElementManager mgr : mgrs) {
                    mgr.setActive(active);
                    list.add(mgr.getServiceElement());
                }
                if (logger.isLoggable(Level.FINER))
                    logger.finer("OperationalStringManager for [" + getProxy().toString() + "] " +
                                 "set active [" + active + "] for OperationalString [" + getName() + "]");
                if (active) {
                    ServiceElement[] sElems = list.toArray(new ServiceElement[list.size()]);
                    updateServiceElements(sElems);
                }

                /* Trickle down effect : update all nested managers of the 
                 * new active state */
                OpStringManager[] nestedMgrs = nestedManagers.toArray(new OpStringManager[nestedManagers.size()]);
                for (OpStringManager nestedMgr : nestedMgrs) {
                    nestedMgr.setActive(newActive);
                }

            } else {
                if (logger.isLoggable(Level.FINEST))
                    logger.finest("OperationalStringManager for [" + opString.getName() + "] already " +
                                  "has active state of [" + active + "]");
            }
        }
    }

    /**
     * Get the active property
     *
     * @return The active property
     */
    public boolean isActive() {
        boolean mode;
        //synchronized(this) {
        mode = active;
        //}
        return (mode);
    }

    /**
     * @see org.rioproject.core.OperationalStringManager#setManaging(boolean)
     */
    public void setManaging(boolean newActive) {
        setActive(newActive);
    }

    /**
     * @see org.rioproject.core.OperationalStringManager#isManaging
     */
    public boolean isManaging() {
        return (isActive());
    }

    /**
     * @see org.rioproject.core.OperationalStringManager#getDeploymentDates
     */
    public Date[] getDeploymentDates() {
        return (deployDateList.toArray(new Date[deployDateList.size()]));
    }

    /**
     * @see OpStringManager#setDeploymentStatus(int)
     */
    public void setDeploymentStatus(int status) {
        opString.setDeployed(status);
        deployStatus = status;
        if (deployStatus == OperationalString.UNDEPLOYED) {
            if (nestedManagers.size() > 0) {
                OpStringManager[] nestedMgrs = nestedManagers.toArray(new OpStringManager[nestedManagers.size()]);
                for (OpStringManager nestedMgr : nestedMgrs) {
                    if (nestedMgr.getParentCount() == 1)
                        nestedMgr.setDeploymentStatus(OperationalString.UNDEPLOYED);
                }
            }
        }
    }

    /**
     * @see OpStringManager#addDeploymentDate(java.util.Date)
     */
    public void addDeploymentDate(Date date) {
        if (date != null)
            deployDateList.add(date);
    }

    /**
     * Initialize all ServiceElementManager instances
     *
     * @param mode Whether the ServiceElementManager should actively manage (allocate) services. This will
     * also set the DefaultOpStringManager active Property
     * @param provisioner The ServiceProvisioner
     * @param uuid The Uuid of the ProvisionMonitorImpl. If the uuid of a
     * discovered service matches our uuid, don't spend the overhead of creating
     * a FaultDetectionHandler
     * @param listener A ServiceProvisionListener that will be notified
     *                 of services are they are provisioned. This notification approach is
     *                 only valid at DefaultOpStringManager creation (deployment), when services are
     *                 provisioned at OperationalString deployment time
     * @return A map of reasons and corresponding exceptions from creating
     *         service element manager instances. If the map has no entries there
     *         are no errors
     * @throws Exception if there are unrecoverable errors
     */
    Map<String, Throwable> init(boolean mode,
                                ServiceProvisioner provisioner,
                                Uuid uuid,
                                ServiceProvisionListener listener) throws Exception {
        this.active = mode;
        this.provisioner = provisioner;
        this.uuid = uuid;
        Map<String, Throwable> map = new HashMap<String, Throwable>();
        ServiceElement[] sElems = opString.getServices();
        for (ServiceElement sElem : sElems) {
            try {
                if (sElem.getExportBundles().length > 0) {
                    createServiceElementManager(sElem, false, listener);
                } else {
                    String message = "Service [" + sElem.getName() + "] has no " +
                                     "declared interfaces, cannot deploy";
                    logger.warning(message);
                    map.put(sElem.getName(), new Exception(message));
                }
            } catch (Exception e) {
                Throwable cause = e.getCause();
                if (cause == null)
                    cause = e;
                logger.log(Level.WARNING,
                           "Creating ServiceElementManager for " +
                           "[" + sElem.getName() + "], " +
                           "deployment [" + sElem.getOperationalStringName() + "]",
                           e);
                map.put(sElem.getName(), cause);
                throw e;
            }
        }
        return (map);
    }

    /**
     * Start all ServiceElementManager instances
     *
     * @param listener A ServiceProvisionListener that will be notified
     *                 of services are they are provisioned. This notification approach is
     *                 only valid at DefaultOpStringManager creation (deployment), when services are
     *                 provisioned at OperationalString deployment time
     *                 
     * @throws java.rmi.RemoteException If the DeployAdmin cannot be obtained
     */
    void startManager(ServiceProvisionListener listener) throws RemoteException {
        startManager(listener, new HashMap());
    }

    /**
     * Start all ServiceElementManager instances
     *
     * @param listener         A ServiceProvisionListener that will be notified
     *                         of services are they are provisioned. This notification approach is
     *                         only valid at DefaultOpStringManager creation (deployment), when services are
     *                         provisioned at OperationalString deployment time
     * @param knownInstanceMap Known ServiceBeanInstance objects.
     * 
     * @throws java.rmi.RemoteException If the DeployAdmin cannot be obtained
     */
    void startManager(ServiceProvisionListener listener, Map knownInstanceMap) throws RemoteException {
        boolean scheduled = false;
        Schedule schedule = opString.getSchedule();
        Date startDate = schedule.getStartDate();
        long now = System.currentTimeMillis();
        long delay = startDate.getTime() - now;
        if (logger.isLoggable(Level.FINEST))
            logger.finest("OperationalString [" + getName() + "] " +
                          "Start Date=[" + startDate.toString() + "], " +
                          "Delay [" + delay + "]");
        if (delay > 0 || schedule.getDuration() > 0) {
            DeployAdmin deployAdmin = (DeployAdmin)serviceProxy.getAdmin();
            DeploymentTask deploymentTask = new DeploymentTask(this, deployAdmin);
            addTask(deploymentTask);
            if (schedule.getDuration() > 0)
                TaskTimer.getInstance().scheduleAtFixedRate(deploymentTask,
                                                            startDate,
                                                            (schedule.getDuration() +
                                                             schedule.getRepeatInterval()));
            else
                TaskTimer.getInstance().schedule(deploymentTask, startDate);
            scheduled = true;
            setDeploymentStatus(OperationalString.SCHEDULED);
        }

        if (!scheduled) {
            addDeploymentDate(new Date(System.currentTimeMillis()));
            setDeploymentStatus(OperationalString.DEPLOYED);
            ServiceElementManager[] mgrs = getServiceElementManagers();
            for (ServiceElementManager mgr : mgrs) {
                ServiceElement elem = mgr.getServiceElement();
                ServiceBeanInstance[] instances = (ServiceBeanInstance[]) knownInstanceMap.get(elem);
                try {
                    int alreadyRunning = mgr.startManager(listener, instances);
                    if (alreadyRunning > 0) {
                        updateServiceElements(new ServiceElement[]{
                                                                      mgr.getServiceElement()});
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Starting ServiceElementManager", e);
                }
            }
        }
    }

    /**
     * Create a specific ServiceElementManager, add it to the Collection of
     * ServiceElementManager instances, and start the manager
     *
     * @param sElem    The ServiceElement the ServiceElementManager will
     *                 manage
     * @param start    Whether to start the ServiceElementManager at creation
     * discovered service matches our uuid, don't spend the overhead of creating
     * a FaultDetectionHandler
     * @param listener A ServiceProvisionListener that will be notified
     *                 of services are they are provisioned. This notification approach is
     *                 only valid when the ServiceElementManager is created, and services are
     *                 provisioned at OperationalString deployment time
     * @throws Exception if the ServiceElementManager cannot be created
     */
    void createServiceElementManager(ServiceElement sElem,
                                     boolean start,
                                     ServiceProvisionListener listener)
        throws Exception {
        ServiceElementManager svcElemMgr =
            new ServiceElementManager(sElem, proxy, provisioner, uuid, isActive(), config);
        /* Set event attributes */
        svcElemMgr.setEventProcessor(eventProcessor);
        svcElemMgr.setEventSource(serviceProxy);
        svcElemMgrs.add(svcElemMgr);

        if (start) {
            int alreadyRunning = svcElemMgr.startManager(listener);
            if (alreadyRunning > 0) {
                updateServiceElements(new ServiceElement[]{sElem});
            }
        }
    }

    /**
     * @see org.rioproject.core.OperationalStringManager#update(OperationalString)
     */
    public Map<String, Throwable> update(OperationalString newOpString) throws OperationalStringException, RemoteException {

        if (!isActive()) {
            OperationalStringManager primary = opStringMangerController.getPrimary(getName());
            if (primary == null) {
                logger.warning("Primary testManager not located, force state to active for [" + getName() + "]");
                setActive(true);
            } else {
                logger.info("Forwarding update request to primary testManager for [" + getName() + "]");
                return (primary.update(newOpString));
            }
        }
        try {
            deploymentVerifier.verifyOperationalString(newOpString, getRemoteRepositories());
        } catch (Exception e) {
            throw new OperationalStringException("Verifying deployment for [" + newOpString.getName() + "]", e);
        }
        Map<String, Throwable> map = doUpdateOperationalString(newOpString);
        ProvisionMonitorEvent event =
            new ProvisionMonitorEvent(serviceProxy,
                                      ProvisionMonitorEvent.Action.OPSTRING_UPDATED,
                                      doGetOperationalString());
        eventProcessor.processEvent(event);
        return (map);
    }

    private RemoteRepository[] getRemoteRepositories() {
        RemoteRepository[] remoteRepositories = null;
        if (oar != null)
            remoteRepositories = oar.getRepositories().toArray(new RemoteRepository[oar.getRepositories().size()]);
        return remoteRepositories;
    }

    /**
     * @see OpStringManager#doUpdateOperationalString(org.rioproject.core.OperationalString)
     */
    public Map<String, Throwable> doUpdateOperationalString(OperationalString newOpString) {
        if (newOpString == null)
            throw new IllegalArgumentException("OperationalString cannot be null");

        if (logger.isLoggable(Level.INFO)) {
            logger.info("Updating " + newOpString.getName() + " deployment");
        }
        Map<String, Throwable> map = new HashMap<String, Throwable>();
        ServiceElement[] sElems = newOpString.getServices();
        List<ServiceElementManager> notRefreshed = new ArrayList<ServiceElementManager>(svcElemMgrs);
        /* Refresh ServiceElementManagers */
        for (ServiceElement sElem : sElems) {
            try {
                ServiceElementManager svcElemMgr = getServiceElementManager(sElem);
                if (svcElemMgr == null) {
                    createServiceElementManager(sElem, true,  null);
                } else {
                    svcElemMgr.setServiceElement(sElem);
                    notRefreshed.remove(svcElemMgr);
                }
            } catch (Exception e) {
                map.put(sElem.getName(), e);
                logger.log(Level.WARNING, "Refreshing ServiceElementManagers", e);
            }
        }
        /*
        * Process nested Operational Strings. If a nested
        * OperationalString does not exist it will be created.
        * If it does exist check the number of parents, if its only refernced 
        * by this testManager update it
        */
        OperationalString[] nested = newOpString.getNestedOperationalStrings();
        for (int i = 0; i < nested.length; i++) {
            if (!opStringMangerController.opStringExists(nested[i].getName())) {
                try {
                    opStringMangerController.addOperationalString(nested[i], map, this, null, null);
                } catch (Exception e) {
                    Throwable cause = e.getCause();
                    if (cause == null)
                        cause = e;
                    map.put(sElems[i].getName(), cause);
                    logger.log(Level.WARNING, "Adding nested OperationalString [" + nested[i].getName() + "]", e);
                }
            } else {
                OpStringManager nestedMgr = opStringMangerController.getOpStringManager(nested[i].getName());
                if (nestedMgr.getParentCount() == 1 &&
                    nestedMgr.getParents().contains(this)) {
                    Map<String, Throwable> nestedMap = nestedMgr.doUpdateOperationalString(nested[i]);
                    map.putAll(nestedMap);
                }
            }
        }
        /*
        * If there are ServiceElementManagers that are not needed
        * remove them
        */
        for (ServiceElementManager mgr : notRefreshed) {
            mgr.stopManager(true);
            svcElemMgrs.remove(mgr);
        }
        opString = newOpString;
        stateChanged(false);
        if (isActive())
            updateServiceElements(sElems);
        return (map);
    }

    /**
     * @see OpStringManager#getParents()
     */
    public Collection<OpStringManager> getParents() {
        Collection<OpStringManager> rents = new ArrayList<OpStringManager>();
        rents.addAll(parents);
        return rents;
    }

    private void stateChanged(boolean remove) {
        if(stateManager!=null)
            stateManager.stateChanged(this, remove);
    }

    /**
     * Verify all services are being monitored by iterating through the
     * Collection of ServiceElementManager instances and invoking each
     * instance's verify() method
     *
     * @param listener A ServiceProvisionListener that will be notified
     *                 of services if they are provisioned.
     */
    public void verify(ServiceProvisionListener listener) {
        for (ServiceElementManager mgr : svcElemMgrs) {
            mgr.verify(listener);
        }
        for (OpStringManager nestedMgr : nestedManagers) {
            nestedMgr.verify(listener);
        }
    }


    /**
     * Update ServiceElement instances to ServiceBeanInstantiators which are
     * hosting the ServiceElement instance(s). If the OperationalStringManager
     * is not active. do not perform this task
     *
     * @param elements Array of ServiceElement instances to update
     */
    void updateServiceElements(ServiceElement[] elements) {
        if (!isActive())
            return;
        ServiceResource[] resources = provisioner.getServiceResourceSelector().getServiceResources();
        Map<InstantiatorResource, List<ServiceElement>> map =
            new HashMap<InstantiatorResource, List<ServiceElement>>();
        for (ServiceResource resource : resources) {
            InstantiatorResource ir =
                (InstantiatorResource) resource.getResource();
            for (ServiceElement element : elements) {
                int count = ir.getServiceElementCount(element);
                if (logger.isLoggable(Level.FINEST))
                    logger.log(Level.FINEST,
                               ir.getName() + " at " +
                               "[" + ir.getHostAddress() + "] has " +
                               "[" + count + "] of " +
                               "[" + element.getName() + "]");
                if (count > 0) {
                    List<ServiceElement> list = map.get(ir);
                    if (list == null)
                        list = new ArrayList<ServiceElement>();
                    list.add(element);
                    map.put(ir, list);
                }
            }
        }

        for (Map.Entry<InstantiatorResource, List<ServiceElement>> entry : map.entrySet()) {
            InstantiatorResource ir = entry.getKey();
            List<ServiceElement> list = entry.getValue();

            ServiceElement[] elems = list.toArray(new ServiceElement[list.size()]);
            try {
                ServiceBeanInstantiator sbi = ir.getInstantiator();
                if (logger.isLoggable(Level.FINEST))
                    logger.log(Level.FINEST,
                               "Update " + ir.getName() + " at [" + ir.getHostAddress() + "] " +
                               "with [" + elems.length + "] elements");
                sbi.update(elems, getProxy());
            } catch (RemoteException e) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST,
                               "Updating ServiceElement for " + ir.getName() + " at [" + ir.getHostAddress() + "]",
                               e);
                } else {
                    logger.log(Level.INFO,
                               e.getClass().getName() + ": " + e.getLocalizedMessage() + " " +
                               "Updating ServiceElement for " +
                               ir.getName() + " at [" +
                               ir.getHostAddress() + "]");
                }
            }
        }
    }

    /**
     * @see OpStringManager#terminate(boolean)
     */
    public OperationalString[] terminate(boolean killServices) {
        List<OperationalString> terminated = new ArrayList<OperationalString>();
        terminated.add(doGetOperationalString());
        /* Cancel all scheduled Tasks */
        TimerTask[] tasks = getTasks();
        for (TimerTask task : tasks)
            task.cancel();

        /* Unexport the testManager */
        try {
            exporter.unexport(true);
        } catch (IllegalStateException e) {
            if (logger.isLoggable(Level.FINE))
                logger.log(Level.FINE, "OperationalStringManager not unexported");
        }
        /* Remove ourselves from the collection */
        opStringMangerController.remove(this);

        /* Stop all ServiceElementManager instances */
        for (ServiceElementManager mgr : svcElemMgrs) {
            mgr.stopManager(killServices);
        }
        /* Adjust parent/nested relationships */
        if (parents.size() > 0) {
            for (OpStringManager parent : parents) {
                parent.removeNested(this);
            }
            parents.clear();
        }

        OpStringManager[] nestedMgrs = nestedManagers.toArray(new OpStringManager[nestedManagers.size()]);
        for (OpStringManager nestedMgr : nestedMgrs) {
            /* If the nested DefaultOpStringManager has only 1 parent, then
             * terminate (undeploy) that DefaultOpStringManager as well */
            if (nestedMgr.getParentCount() == 1) {
                terminated.add(nestedMgr.doGetOperationalString());
                nestedMgr.terminate(killServices);
            } else {
                nestedMgr.removeParent(this);
            }
        }
        if (logger.isLoggable(Level.FINE))
            logger.fine("OpStringManager [" + getName() + "] terminated");
        return (terminated.toArray(new OperationalString[terminated.size()]));
    }

    /**
     * Get the name of this Operational String
     *
     * @return The name of this Operational String
     */
    public String getName() {
        return (opString.getName());
    }

    /*
    * @see org.rioproject.core.OperationalStringManager#addServiceElement
    */
    public void addServiceElement(ServiceElement sElem)
        throws OperationalStringException {
        addServiceElement(sElem, null);
    }

    /*
    * @see org.rioproject.core.OperationalStringManager#addServiceElement
    */
    public void addServiceElement(ServiceElement sElem,
                                  ServiceProvisionListener listener)
        throws OperationalStringException {
        if (sElem == null)
            throw new IllegalArgumentException("ServiceElement is null");
        if (sElem.getOperationalStringName() == null)
            throw new IllegalArgumentException("ServiceElement must have an " +
                                               "OperationalString name");
        if (!sElem.getOperationalStringName().equals(opString.getName()))
            throw new IllegalArgumentException("ServiceElement has wrong " +
                                               "OperationalString name. " +
                                               "Provided " +
                                               "[" + sElem.getOperationalStringName() + "], " +
                                               "should be " +
                                               "[" + opString.getName() + "]");
        if (!isActive())
            throw new OperationalStringException("not the primary OperationalStringManager");
        try {
            doAddServiceElement(sElem, listener);
            stateChanged(false);
            ProvisionMonitorEvent event =
                new ProvisionMonitorEvent(serviceProxy,
                                          ProvisionMonitorEvent.Action.SERVICE_ELEMENT_ADDED,
                                          sElem);
            eventProcessor.processEvent(event);

        } catch (Throwable t) {
            throw new OperationalStringException("Adding ServiceElement", t);
        }
        if (logger.isLoggable(Level.FINE))
            logger.log(Level.FINE, "Added service [" + sElem.getOperationalStringName() + "/" + sElem.getName() + "]");
    }

    /**
    * @see OpStringManager#doAddServiceElement(ServiceElement, ServiceProvisionListener)
    */
    public void doAddServiceElement(ServiceElement sElem, ServiceProvisionListener listener) throws Exception {
        if (sElem.getExportBundles().length > 0) {

            /*File pomFile = null;
            if(oar!=null) {
                File dir = new File(oar.getDeployDir());
                pomFile = OARUtil.find("pom.xml", dir);
            }*/
            deploymentVerifier.verifyOperationalStringService(sElem,
                                                              ResolverHelper.getInstance(),
                                                              getRemoteRepositories());
            createServiceElementManager(sElem, true, listener);
        } else {
            throw new OperationalStringException("Interfaces are null");
        }
        stateChanged(false);
    }

    /*
    * @see org.rioproject.core.OperationalStringManager#removeServiceElement
    */
    public void removeServiceElement(ServiceElement sElem, boolean destroy)
        throws OperationalStringException {
        if (sElem == null)
            throw new IllegalArgumentException("ServiceElement is null");
        if (!isActive())
            throw new OperationalStringException("not the primary OperationalStringManager");
        try {
            doRemoveServiceElement(sElem, destroy);
            ProvisionMonitorEvent event =
                new ProvisionMonitorEvent(serviceProxy,
                                          ProvisionMonitorEvent.Action.SERVICE_ELEMENT_REMOVED,
                                          sElem);
            eventProcessor.processEvent(event);
        } catch (Throwable t) {
            if (t instanceof OperationalStringException)
                throw (OperationalStringException) t;
            throw new OperationalStringException("Removing ServiceElement", t);
        }
        if (logger.isLoggable(Level.FINE))
            logger.log(Level.FINE, "Removed service [" +sElem.getOperationalStringName() + "/" + sElem.getName() + "]");
    }

    /**
     * @see OpStringManager#doRemoveServiceElement(ServiceElement, boolean)
     */
    public void doRemoveServiceElement(ServiceElement sElem, boolean destroy)throws OperationalStringException {
        ServiceElementManager svcElemMgr = getServiceElementManager(sElem);
        if (svcElemMgr == null)
            throw new OperationalStringException("OperationalStringManager for [" + opString.getName() + "], " +
                                                 "is not managing service " +
                                                 "[" +sElem.getOperationalStringName() +"/" +sElem.getName() +"]",
                                                 false);
        svcElemMgr.stopManager(destroy);
        if (!svcElemMgrs.remove(svcElemMgr))
            logger.warning("UNABLE to remove ServiceElementManager for " +
                           "[" + sElem.getOperationalStringName() +
                           "/" + sElem.getName() + "]");
        stateChanged(false);
    }

    /*
    * @see org.rioproject.core.OperationalStringManager#update
    */
    public void update(ServiceElement sElem)
        throws OperationalStringException {
        if (sElem == null)
            throw new IllegalArgumentException("ServiceElement is null");
        if (!isActive())
            throw new OperationalStringException("not the primary OperationalStringManager");
        try {
            doUpdateServiceElement(sElem);
            ProvisionMonitorEvent.Action action = ProvisionMonitorEvent.Action.SERVICE_ELEMENT_UPDATED;
            ProvisionMonitorEvent event = new ProvisionMonitorEvent(serviceProxy, action, sElem);
            eventProcessor.processEvent(event);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Updating ServiceElement [" + sElem.getName() + "]", t);
            throw new OperationalStringException("Updating ServiceElement [" + sElem.getName() + "]", t);
        }
    }

    /**
     * @see OpStringManager#doUpdateServiceElement(ServiceElement)
     */
    public void doUpdateServiceElement(ServiceElement sElem) throws Exception {

        ServiceElementManager svcElemMgr = getServiceElementManager(sElem);
        if (svcElemMgr == null) {
            doAddServiceElement(sElem, null);
        } else {
            svcElemMgr.setServiceElement(sElem);
            svcElemMgr.verify(null);
            stateChanged(false);
            updateServiceElements(new ServiceElement[]{sElem});
        }
    }

    /**
     * @see org.rioproject.core.OperationalStringManager#relocate
     */
    public void relocate(ServiceBeanInstance instance, ServiceProvisionListener listener, Uuid uuid)
        throws OperationalStringException, RemoteException {
        if (instance == null)
            throw new IllegalArgumentException("instance is null");
        if (!isActive())
            throw new OperationalStringException("not the primary OperationalStringManager");
        if (listener != null)
            listener = (ServiceProvisionListener) serviceProvisionListenerPreparer.prepareProxy(listener);
        try {
            ServiceElementManager svcElemMgr = getServiceElementManager(instance);
            if (svcElemMgr == null)
                throw new OperationalStringException("Unmanaged ServiceBeanInstance " +
                                                     "[" + instance.getServiceBeanConfig().getName() + "], " +
                                                     "[" + instance.toString() + "]", false);
            if (svcElemMgr.getServiceElement().getProvisionType() != ServiceElement.ProvisionType.DYNAMIC)
                throw new OperationalStringException("Service must be dynamic to be relocated");
            svcElemMgr.relocate(instance, listener, uuid);
        } catch (Throwable t) {
            logger.warning("Relocating ServiceBeanInstance [" +
                           t.getClass().getName() + ":" + t.getMessage() + "]");
            if (t instanceof OperationalStringException)
                throw (OperationalStringException) t;

            throw new OperationalStringException("Relocating ServiceBeanInstance", t);
        }
    }

    /**
     * @see org.rioproject.core.OperationalStringManager#update
     */
    public void update(ServiceBeanInstance instance)
        throws OperationalStringException {
        if (instance == null)
            throw new IllegalArgumentException("instance is null");
        try {
            ServiceElement sElem = doUpdateServiceBeanInstance(instance);
            ProvisionMonitorEvent event = new ProvisionMonitorEvent(serviceProxy,
                                                                    sElem.getOperationalStringName(),
                                                                    instance);
            eventProcessor.processEvent(event);
        } catch (Throwable t) {
            logger.warning("Updating ServiceBeanInstance [" +
                           "[" + instance.getServiceBeanConfig().getName() + "] [" +
                           t.getClass().getName() + ":" + t.getMessage() + "]");
            throw new OperationalStringException("Updating ServiceBeanInstance", t);
        }
    }

    /**
     * @see OpStringManager#doUpdateServiceBeanInstance(ServiceBeanInstance)
     */
    public ServiceElement doUpdateServiceBeanInstance(ServiceBeanInstance instance)
        throws OperationalStringException {

        ServiceElementManager svcElemMgr = getServiceElementManager(instance);
        if (svcElemMgr == null)
            throw new OperationalStringException("Unmanaged ServiceBeanInstance " +
                                                 "[" + instance.toString() + "]",
                                                 false);
        svcElemMgr.update(instance);
        return (svcElemMgr.getServiceElement());
    }

    /*
    * @see org.rioproject.core.OperationalStringManager#increment
    */
    public synchronized void increment(ServiceElement sElem, boolean permanent, ServiceProvisionListener listener)
        throws OperationalStringException, RemoteException {
        if (sElem == null)
            throw new IllegalArgumentException("ServiceElement is null");
        if (!isActive())
            throw new OperationalStringException("not the primary OperationalStringManager");
        if (listener != null)
            listener = (ServiceProvisionListener) serviceProvisionListenerPreparer.prepareProxy(listener);
        try {
            ServiceElementManager svcElemMgr = getServiceElementManager(sElem);
            if (svcElemMgr == null)
                throw new OperationalStringException("Unmanaged ServiceElement [" + sElem.getName() + "]", false);
            ServiceElement changed = svcElemMgr.increment(permanent, listener);
            if (changed == null)
                return;
            stateChanged(false);
            updateServiceElements(new ServiceElement[]{changed});
            ProvisionMonitorEvent event = new ProvisionMonitorEvent(serviceProxy,
                                                                    ProvisionMonitorEvent.Action.SERVICE_BEAN_INCREMENTED,
                                                                    changed);
            eventProcessor.processEvent(event);
        } catch (Throwable t) {
            logger.warning("Incrementing ServiceElement " +
                           "[" + sElem.getName() + "] [" +
                           t.getClass().getName() + ":" + t.getMessage() + "]");
            throw new OperationalStringException("Incrementing " +
                                                 "ServiceElement " +
                                                 "[" + sElem.getName() + "]",
                                                 t);
        }
    }

    /*
    * @see org.rioproject.core.OperationalStringManager#getPendingCount
    */
    public int getPendingCount(ServiceElement sElem) {
        if (sElem == null)
            throw new IllegalArgumentException("ServiceElement is null");
        int numPending = -1;
        ServiceElementManager svcElemMgr = getServiceElementManager(sElem);
        if (svcElemMgr != null)
            numPending = svcElemMgr.getPendingCount();
        return (numPending);
    }

    /*
    * @see org.rioproject.core.OperationalStringManager#trim
    */
    public int trim(ServiceElement sElem, int trimUp)
        throws OperationalStringException {
        if (sElem == null)
            throw new IllegalArgumentException("ServiceElement is null");
        if (!isActive())
            throw new OperationalStringException("not the primary OperationalStringManager");
        if (sElem.getProvisionType() != ServiceElement.ProvisionType.DYNAMIC)
            return (-1);
        int numTrimmed;
        try {
            ServiceElementManager svcElemMgr = getServiceElementManager(sElem);
            if (svcElemMgr == null)
                throw new OperationalStringException("Unmanaged ServiceElement [" + sElem.getName() + "]", false);
            numTrimmed = svcElemMgr.trim(trimUp);
            if (numTrimmed > 0) {
                stateChanged(false);
                ServiceElement updatedElement = svcElemMgr.getServiceElement();
                updateServiceElements(new ServiceElement[]{updatedElement});
                ProvisionMonitorEvent event =
                    new ProvisionMonitorEvent(serviceProxy,
                                              ProvisionMonitorEvent.Action.SERVICE_BEAN_DECREMENTED,
                                              updatedElement);
                eventProcessor.processEvent(event);
            }
            return (numTrimmed);
        } catch (Throwable t) {
            logger.warning("Trimming ServiceElement [" + sElem.getName() + "] " +
                           t.getClass().getName() + ":" + t.getMessage() + "]");
            throw new OperationalStringException("Trimming ServiceElement ["+sElem.getName()+"]", t);
        }
    }

    /*
    * @see org.rioproject.core.OperationalStringManager#decrement
    */
    public void decrement(ServiceBeanInstance instance,
                          boolean recommended,
                          boolean destroy) throws OperationalStringException {
        if (instance == null)
            throw new IllegalArgumentException("instance is null");
        if (!isActive())
            throw new OperationalStringException(
                                                    "not the primary OperationalStringManager");
        ServiceElementManager svcElemMgr =
            getServiceElementManager(instance);
        if (svcElemMgr == null)
            throw new OperationalStringException("Unmanaged " +
                                                 "ServiceBeanInstance " +
                                                 "[" + instance.toString() + "]",
                                                 false);
        ServiceElement sElem = svcElemMgr.decrement(instance,
                                                    recommended,
                                                    destroy);
        stateChanged(false);
        updateServiceElements(new ServiceElement[]{sElem});
        ProvisionMonitorEvent event =
            new ProvisionMonitorEvent(serviceProxy,
                                      ProvisionMonitorEvent.Action.SERVICE_BEAN_DECREMENTED,
                                      sElem.getOperationalStringName(),
                                      sElem,
                                      instance);
        eventProcessor.processEvent(event);
    }

    /**
     * Determine of this DefaultOpStringManager has any parents
     *
     * @return If true, this DefaultOpStringManager is top-level (has no
     *         parents)
     */
    public boolean isTopLevel() {
        return parents.size() == 0;
    }

    /*
    * @see org.rioproject.core.OperationalStringManager#getOperationalString
    */
    public OperationalString getOperationalString() {
        return (doGetOperationalString());
    }

    /*
    * @see org.rioproject.core.OperationalStringManager#getServiceElement
    */
    public ServiceElement getServiceElement(Object proxy) {
        if (proxy == null)
            throw new IllegalArgumentException("proxy is null");
        ServiceElementManager mgr = null;
        try {
            mgr = getServiceElementManager(proxy);
        } catch (IOException e) {
            if (logger.isLoggable(Level.FINEST))
                logger.log(Level.FINEST, "Getting ServiceElementManager for proxy", e);
        }
        if (mgr == null)
            logger.warning("No ServiceElementManager found for proxy " + proxy);
        ServiceElement element = null;
        if (mgr != null) {
            element = mgr.getServiceElement();
        }
        return (element);
    }

    /*
    * @see org.rioproject.core.OperationalStringManager#getServiceBeanInstances
    */
    public ServiceBeanInstance[] getServiceBeanInstances(ServiceElement sElem) throws OperationalStringException {
        if (sElem == null)
            throw new IllegalArgumentException("ServiceElement is null");
        try {
            ServiceElementManager mgr = getServiceElementManager(sElem);
            if (mgr == null)
                throw new OperationalStringException("Unmanaged ServiceElement [" + sElem.getName() + "]", false);
            return (mgr.getServiceBeanInstances());
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Getting ServiceBeanInstances for ServiceElement [" + sElem.getName() + "]", t);
            if (t instanceof OperationalStringException)
                throw (OperationalStringException) t;
            else
                throw new OperationalStringException("Getting ServiceBeanInstances for ServiceElement " +
                                                     "["+sElem.getName() + "]", t);
        }
    }

    /*
    * @see org.rioproject.core.OperationalStringManager#getServiceElement
    */
    public ServiceElement getServiceElement(String[] interfaces,
                                            String name) {
        if (interfaces == null)
            throw new IllegalArgumentException("interfaces cannot be null");
        for (ServiceElementManager mgr : svcElemMgrs) {
            ServiceElement sElem = mgr.getServiceElement();
            boolean found = false;
            ClassBundle[] exports = sElem.getExportBundles();
            for (ClassBundle export : exports) {
                boolean matched = false;
                for (String anInterface : interfaces) {
                    if (export.getClassName().equals(anInterface))
                        matched = true;
                }
                if (matched) {
                    found = true;
                    break;
                }
            }

            if (found) {
                if (name == null)
                    return (sElem);
                if (name.equals(sElem.getName()))
                    return (sElem);
            }
        }
        return (null);
    }

    /**
     * Get the OperationalString the DefaultOpStringManager is managing
     *
     * @return The OperationalString the DefaultOpStringManager is managing
     */
    public OperationalString doGetOperationalString() {
        OpString opstr = new OpString(opString.getName(), opString.loadedFrom());
        opstr.setDeployed(deployStatus);
        opstr.setSchedule(opString.getSchedule());
        for (ServiceElementManager mgr : svcElemMgrs) {
            opstr.addService(mgr.getServiceElement());
        }
        for (OpStringManager nestedMgr : nestedManagers) {
            opstr.addOperationalString(nestedMgr.doGetOperationalString());
        }
        opString = opstr;
        return (opString);
    }

    /**
     * @see org.rioproject.core.OperationalStringManager#redeploy(ServiceElement,
     *      ServiceBeanInstance, boolean, boolean, long, ServiceProvisionListener)
     */
    public void redeploy(ServiceElement sElem,
                         ServiceBeanInstance instance,
                         boolean clean,
                         long delay,
                         ServiceProvisionListener listener)
        throws OperationalStringException {
        redeploy(sElem, instance, clean, true, delay, listener);
    }

    /**
     * @see org.rioproject.core.OperationalStringManager#redeploy(ServiceElement,
     *      ServiceBeanInstance, boolean, long, ServiceProvisionListener)
     */
    public void redeploy(ServiceElement sElem,
                         ServiceBeanInstance instance,
                         boolean clean,
                         boolean sticky,
                         long delay,
                         ServiceProvisionListener listener)
        throws OperationalStringException {

        if (!isActive())
            throw new OperationalStringException("not the primary OperationalStringManager");
        if (listener != null) {
            try {
                listener = (ServiceProvisionListener) serviceProvisionListenerPreparer.prepareProxy(listener);
            } catch (RemoteException e) {
                Throwable cause = ThrowableUtil.getRootCause(e);
                if (logger.isLoggable(Level.FINER))
                    logger.log(Level.FINER,
                               "Notifying ServiceProvisionListener of " +
                               "redeployment, continue with redeployment. " +
                               cause.getClass().getName() + ": " +
                               cause.getLocalizedMessage());
            }
        }

        if (delay > 0) {
            doScheduleRedeploymentTask(delay,
                                       sElem,     /* ServiceElement */
                                       instance,  /* ServiceBeanInstance */
                                       clean,
                                       sticky,
                                       listener);
        } else {
            if (sElem == null && instance == null)
                doRedeploy(clean, sticky, listener);
            else
                doRedeploy(sElem, instance, clean, sticky, listener);
        }
    }

    /**
     * @see org.rioproject.core.OperationalStringManager#getServiceStatements
     */
    public ServiceStatement[] getServiceStatements() {
        List<ServiceStatement> statements = new ArrayList<ServiceStatement>();
        for (ServiceElementManager mgr : getServiceElementManagers()) {
            statements.add(mgr.getServiceStatement());
        }
        return statements.toArray(new ServiceStatement[statements.size()]);
    }


    /**
     * @see org.rioproject.core.OperationalStringManager#getDeploymentMap
     */
    public DeploymentMap getDeploymentMap() {
        Map<ServiceElement, List<DeployedService>> map = new HashMap<ServiceElement, List<DeployedService>>();
        for (ServiceElementManager mgr : getServiceElementManagers()) {
            map.put(mgr.getServiceElement(), mgr.getServiceDeploymentList());
        }
        return new DeploymentMap(map);
    }

    /*
    * Redeploy the OperationalString  
    */
    public void doRedeploy(boolean clean, boolean sticky, ServiceProvisionListener listener) throws OperationalStringException {
        if (!isActive())
            throw new OperationalStringException("not the primary OperationalStringManager");
        for (ServiceElementManager mgr : svcElemMgrs.toArray(new ServiceElementManager[svcElemMgrs.size()]))
            doRedeploy(mgr.getServiceElement(), null, clean, sticky, listener);
    }

    /*
    * Redeploy a ServiceElement or a ServiceBeanInstance  
    */
    public void doRedeploy(ServiceElement sElem,
                           ServiceBeanInstance instance,
                           boolean clean,
                           boolean sticky,
                           ServiceProvisionListener listener)
        throws OperationalStringException {

        if (!isActive())
            throw new OperationalStringException("not the primary OperationalStringManager");
        ServiceElementManager svcElemMgr = null;
        RedeploymentTask scheduledTask = null;
        if (sElem != null) {
            svcElemMgr = getServiceElementManager(sElem);
            scheduledTask = getScheduledRedeploymentTask(sElem,
                                                         null);
        } else if (instance != null) {
            svcElemMgr = getServiceElementManager(instance);
            scheduledTask = getScheduledRedeploymentTask(null,
                                                         instance);
        }

        if (svcElemMgr == null) {
            String message;
            if (sElem == null)
                message = "Unmanaged ServiceElement";
            else
                message = "Unmanaged ServiceElement [" + sElem.getName() + "]";
            throw new OperationalStringException(message);
        }

        if (scheduledTask != null) {
            long exec = (scheduledTask.scheduledExecutionTime() - System.currentTimeMillis()) / 1000;
            if (exec > 0) {
                String item = (sElem == null ? "ServiceBeanInstance" : "ServiceElement");
                throw new OperationalStringException(item+" already scheduled for redeployment in "+exec+" seconds");
            }
        }

        /* Redeployment is for a ServiceElement */
        if (sElem != null) {
            ServiceBeanInstance[] instances = svcElemMgr.getServiceBeanInstances();
            if (instances.length > 0) {
                for (ServiceBeanInstance inst : instances)
                    svcElemMgr.redeploy(inst, clean, sticky, listener);
            } else {
                svcElemMgr.redeploy(listener);
            }
            /* Redeployment is for a ServiceBeanInstance */
        } else {
            svcElemMgr.redeploy(instance, clean, sticky, listener);
        }
    }

    /**
     * @see OpStringManager#doScheduleRedeploymentTask(long, ServiceElement, ServiceBeanInstance, boolean, boolean, ServiceProvisionListener)
     */
    public void doScheduleRedeploymentTask(long delay,
                                           ServiceElement sElem,
                                           ServiceBeanInstance instance,
                                           boolean clean,
                                           boolean sticky,
                                           ServiceProvisionListener listener)
        throws OperationalStringException {
        int lastIndex = deployDateList.size() - 1;
        if (lastIndex < 0 || opString.getStatus() == OperationalString.SCHEDULED)
            throw new OperationalStringException("Cannot redeploy an " +
                                                 "OperationalString with a " +
                                                 "status of Scheduled");
        Date lastDeployDate = deployDateList.get(lastIndex);
        if (opString.getSchedule().getDuration() != Schedule.INDEFINITE &&
            (delay >
             (lastDeployDate.getTime() + opString.getSchedule().getDuration())))
            throw new OperationalStringException("delay is too long");

        RedeploymentTask scheduledTask = getScheduledRedeploymentTask(sElem, instance);
        if (scheduledTask != null) {
            long exec = (scheduledTask.scheduledExecutionTime() - System.currentTimeMillis()) / 1000;
            throw new OperationalStringException("Already " +
                                                 "scheduled " +
                                                 "for redeployment " +
                                                 "in " +
                                                 exec + " seconds");
        }
        RedeploymentTask task = new RedeploymentTask(this, sElem, instance, clean, sticky, listener);
        addTask(task);
        TaskTimer.getInstance().schedule(task, delay);
        Date redeployDate = new Date(System.currentTimeMillis() + delay);
        Object[] parms = new Object[]{redeployDate, clean, sticky, listener};
        ProvisionMonitorEvent event = new ProvisionMonitorEvent(serviceProxy,
                                                                opString.getName(),
                                                                sElem,
                                                                instance,
                                                                parms);
        eventProcessor.processEvent(event);

        if (logger.isLoggable(Level.FINEST)) {
            String name = (instance == null ?
                           sElem.getName() :
                           instance.getServiceBeanConfig().getName());
            String item = (instance == null ?
                           "ServiceElement" : "ServiceBeanInstance");
            logger.finest("Schedule [" + name + "] " + item + " " +
                          "redeploy in [" + delay + "] millis");
        }
    }

    /**
     * Check for a scheduled RedeploymentTask for either the ServiceElement or
     * theServiceBeanInstance.
     *
     * @param sElem    the ServiceElement to check
     * @param instance The coresponding ServiceBeanInstance
     * @return The scheduled RedeploymentTask, or null if not found
     */
    RedeploymentTask getScheduledRedeploymentTask(ServiceElement sElem,
                                                  ServiceBeanInstance instance) {
        TimerTask[] tasks = getTasks();
        RedeploymentTask scheduledTask = null;
        for (TimerTask task : tasks) {
            if (task instanceof RedeploymentTask) {
                RedeploymentTask rTask = (RedeploymentTask) task;
                if (sElem != null && rTask.getServiceElement() != null) {
                    if (rTask.getServiceElement().equals(sElem)) {
                        scheduledTask = rTask;
                        break;
                    }
                    if (instance != null && rTask.getInstance() != null) {
                        if (rTask.getInstance().equals(instance)) {
                            scheduledTask = rTask;
                            break;
                        }
                    }
                }
            }
        }
        return (scheduledTask);
    }

    /**
     * Get all ServiceElementManager instances
     *
     * @return All ServiceElementManager instances as an array
     */
    public ServiceElementManager[] getServiceElementManagers() {
        return (svcElemMgrs.toArray(new ServiceElementManager[svcElemMgrs.size()]));
    }

    /**
     * @see OpStringManager#getServiceElementManager(ServiceElement)
     */
    public ServiceElementManager getServiceElementManager(ServiceElement sElem) {
        for (ServiceElementManager mgr : svcElemMgrs) {
            ServiceElement sElem1 = mgr.getServiceElement();
            if (sElem.equals(sElem1)) {
                return (mgr);
            }
        }
        return (null);
    }

    /**
     * Get the ServiceElementManager for a ServiceBeanInstance instance
     *
     * @param instance The ServiceBeanInstance instance
     * @return The ServiceElementManager that is
     *         managing the ServiceElement. If no ServiceElementManager is found,
     *         null is returned
     */
    ServiceElementManager getServiceElementManager(ServiceBeanInstance instance) {
        for (ServiceElementManager mgr : svcElemMgrs) {
            if (mgr.hasServiceBeanInstance(instance))
                return (mgr);
        }
        return (null);
    }

    /**
     * Get the ServiceElementManager from a service proxy
     *
     * @param proxy The service proxy
     * @return The ServiceElementManager that is
     *         managing the ServiceElement. If no ServiceElementManager is found,
     *         null is returned
     * @throws IOException If the service proxy from a ServiceBeanInstance
     *                     returned from a ServiceElementManager cannot be unmarshalled
     */
    ServiceElementManager getServiceElementManager(Object proxy) throws IOException {
        for (ServiceElementManager mgr : svcElemMgrs) {
            ServiceBeanInstance[] instances = mgr.getServiceBeanInstances();
            for (ServiceBeanInstance instance : instances) {
                try {
                    if (instance.getService().equals(proxy))
                        return (mgr);
                } catch (ClassNotFoundException e) {
                    logger.log(Level.WARNING, "Unable to obtain proxy", e);
                }
            }
        }
        return (null);
    }

    /**
     * @see OpStringManager#addNested(OpStringManager)
     */
    public void addNested(OpStringManager nestedMgr) {
        nestedManagers.add(nestedMgr);
        nestedMgr.addParent(this);
    }

    /**
     * Remove a nested OpStringManager
     *
     * @param nestedMgr The nested OpStringManager to remove
     */
    public void removeNested(OpStringManager nestedMgr) {
        nestedManagers.remove(nestedMgr);
    }

    /**
     * Add a parent for this OpStringManager. This OpStringManager will
     * now be a nested OpStringManager
     *
     * @param parent The parent for this OpStringManager.
     */
    public void addParent(OpStringManager parent) {
        if (parents.contains(parent))
            return;
        parents.add(parent);
    }

    /**
     * Remove a parent from this OpStringManager.
     *
     * @param parent The parent to remove
     */
    public void removeParent(OpStringManager parent) {
        parents.remove(parent);
    }

    /**
     * Get the number of parents the OpStringManager has
     *
     * @return The number of parents the OpStringManager has
     */
    public int getParentCount() {
        return (parents.size());
    }

    /**
     * Returns a <code>TrustVerifier</code> which can be used to verify that a
     * given proxy to this policy handler can be trusted
     */
    public TrustVerifier getProxyVerifier() {
        if (logger.isLoggable(Level.FINEST))
            logger.entering(this.getClass().getName(), "getProxyVerifier");
        return (new BasicProxyTrustVerifier(proxy));
    }

    /**
     * Get all in process TimerTask instances
     *
     * @return Array of in process TimerTask instances. If there are no
     *         TimerTask instances return a zero-length array
     */
    TimerTask[] getTasks() {
        return (scheduledTaskList.toArray(new TimerTask[scheduledTaskList.size()]));
    }

    /**
     * Add a TimerTask to the Collection of TimerTasks
     *
     * @param task The TimerTask to add
     */
    public void addTask(TimerTask task) {
        if (task != null)
            scheduledTaskList.add(task);
    }

    /**
     * Remove a TimerTask from Collection of scheduled TimerTask
     * instances
     *
     * @param task The TimerTask to remove
     */
    public void removeTask(TimerTask task) {
        if (task != null)
            scheduledTaskList.remove(task);
    }

} // End DefaultOpStringManager

