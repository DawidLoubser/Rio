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
package org.rioproject.monitor.tasks;

import net.jini.core.event.UnknownEventException;
import org.rioproject.core.JSBInstantiationException;
import org.rioproject.core.ServiceBeanInstance;
import org.rioproject.core.provision.DeployedService;
import org.rioproject.core.provision.ServiceProvisionEvent;
import org.rioproject.monitor.*;
import org.rioproject.monitor.managers.PendingManager;
import org.rioproject.monitor.util.LoggingUtil;
import org.rioproject.resources.servicecore.ServiceResource;
import org.rioproject.resources.util.ThrowableUtil;

import java.rmi.RemoteException;
import java.rmi.server.RMIClassLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The ProvisionTask is created to process and provision dispatch request
 */
public class ProvisionTask implements Runnable {
    private long index;
    private ServiceBeanInstance jsbInstance = null;
    private Throwable thrown = null;
    private String failureReason = null;
    private ServiceProvisionContext context;
    private PendingManager pendingManager;
    private final Logger logger = Logger.getLogger("org.rioproject.monitor.provision");

    /**
     * Create a ProvisionTask
     *
     * @param context The ServiceProvisionContext
     * @param pendingManager The manager for pending service provisioning requests
     */
    public ProvisionTask(ServiceProvisionContext context, PendingManager pendingManager) {
        this.context = context;
        this.pendingManager = pendingManager;
    }

    /**
     * Create a ProvisionTask
     *
     * @param context The ServiceProvisionContext
     * @param pendingManager The manager for pending service provisioning requests
     * @param index The index of the ServiceElement in the PendingServiceElementManager
     */
    public ProvisionTask(ServiceProvisionContext context, PendingManager pendingManager, long index) {
        this.context = context;
        this.pendingManager = pendingManager;
        this.index = index;
    }

    public void run() {
        try {
            jsbInstance = null;
            int result = doProvision(context.getProvisionRequest(), context.getServiceResource());
            if ((result & ServiceProvisioner.PROVISION_FAILURE) != 0) {
                boolean resubmitted = true;
                if (logger.isLoggable(Level.FINE))
                    logger.fine("Provision attempt failed for [" +
                                LoggingUtil.getLoggingName(context.getProvisionRequest()) + "]");
                if ((result & ServiceProvisioner.UNINSTANTIABLE_JSB) != 0) {
                    /* Notify ServiceProvisionListener of failure */
                    context.getProvisionRequest().getListener().uninstantiable(context.getProvisionRequest());
                    resubmitted = false;
                    if (logger.isLoggable(Level.FINE))
                        logger.fine("Service [" +LoggingUtil.getLoggingName(context.getProvisionRequest()) + "] " +
                                    "is un-instantiable, do not resubmit");
                } else if ((result & ServiceProvisioner.BAD_CYBERNODE) != 0) {
                    /* Provision request encountered a bad Cybernode,
                     * dispatch another request immediately. Note the
                     * subsequent provision request occurs in a separate
                     * thread */
                    context.getDispatcher().dispatch(context.getProvisionRequest());
                    return;
                } else {
                    if (pendingManager != null) {
                        if (context.getProvisionRequest().getType() == ProvisionRequest.Type.PROVISION) {
                            pendingManager.addProvisionRequest(context.getProvisionRequest(), index);
                            if (logger.isLoggable(Level.FINE))
                                logger.fine("Re-submitted [" +LoggingUtil.getLoggingName(context.getProvisionRequest()) +
                                            "] to " +pendingManager.getType());
                        }
                    }
                }
                /* Send a ProvisionFailureEvent */
                if (thrown != null || result != 0) {
                    processProvisionFailure(new ProvisionFailureEvent(context.getEventSource(),
                                                                      context.getProvisionRequest().getServiceElement(),
                                                                      failureReason,
                                                                      thrown));
                }
                /* If we have a ServiceProvisionListener,
                 * notify the listener */
                if (context.getProvisionRequest().getServiceProvisionListener() != null) {
                    try {
                        context.getProvisionRequest().getServiceProvisionListener()
                            .failed(context.getProvisionRequest().getServiceElement(), resubmitted);
                    } catch (Exception e) {
                        Throwable t = e;
                        if (e.getCause() != null)
                            t = e.getCause();
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST,
                                       "Error Notifying ServiceProvisionListeners on failure " +
                                       "[" + t.getClass().getName() + ":" +t.getLocalizedMessage() + "], " +
                                       "this is usually a benign error and indicates " +
                                       "that the ServiceProvisionListener gave up listening");
                        }
                    }
                }
            } else {
                if (jsbInstance == null) {
                    if (logger.isLoggable(Level.FINER)) {
                        String addr = ((InstantiatorResource) context.getServiceResource().getResource()).getHostAddress();
                        String name = ((InstantiatorResource) context.getServiceResource().getResource()).getName();
                        logger.log(Level.FINER,
                                   name + " at [" + addr + "] " +
                                   "did not allocate [" +
                                   LoggingUtil.getLoggingName(context.getProvisionRequest()) + "], " +
                                   "service limit assumed to " +
                                   "have been met");
                    }
                    return;
                }
                /* Notify ServiceProvisionListener of success */
                context.getProvisionRequest().getListener()
                    .serviceProvisioned(jsbInstance,
                                        (InstantiatorResource) context.getServiceResource().getResource());
                /* If we have an ServiceBeanInstantiatorListener, 
                 * notify the listener */
                if (context.getProvisionRequest().getServiceProvisionListener() != null) {
                    try {
                        context.getProvisionRequest().getServiceProvisionListener().succeeded(jsbInstance);
                    } catch (Exception e) {
                        Throwable cause = ThrowableUtil.getRootCause(e);
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.finest("Notifying ServiceProvisionListeners on success. " +
                                          "[" + cause.getClass().getName() + ":" +
                                          cause.getLocalizedMessage() + "]");
                        }
                    }
                }
            }
        } finally {
            context.getInProcess().remove(context.getProvisionRequest().getServiceElement());
        }
    }

    int doProvision(ProvisionRequest request,
                    ServiceResource serviceResource) {
        long start = System.currentTimeMillis();
        int result = 0;
        InstantiatorResource ir = (InstantiatorResource) serviceResource.getResource();
        try {
            try {
                ServiceProvisionEvent event = new ServiceProvisionEvent(context.getEventSource(),
                                                                        request.getOpStringManager(),
                                                                        request.getServiceElement());
                event.setSequenceNumber(context.getServiceProvisionEventSequenceNumber().incrementAndGet());
                event.setHandback(ir.getHandback());
                /*
                * Put the instantiate invocation in a for loop, the
                * Cybernode may return null if there is a race-condition
                * where a service is terminated, the FDH notifies the
                * ServiceElementManager of the failure, the
                * ProvisionRequest is dispatched (we get to this point),
                * and the Cybernode is in the process of doing
                * housekeeping related to service termination. If we get
                * a null returned, wait the specified time and retry
                */
                int numProvisionRetries = 3;
                for (int i = 0; i < numProvisionRetries; i++) {
                    if (logger.isLoggable(Level.FINER)) {
                        String retry = (i == 0 ? "" : ", retry (" + i + ") ");
                        logger.finer("Allocating " + retry + "[" +LoggingUtil.getLoggingName(request) +"] ...");
                    }
                    DeployedService deployedService = ir.getInstantiator().instantiate(event);
                    if (deployedService != null) {
                        jsbInstance = deployedService.getServiceBeanInstance();
                        ir.addDeployedService(deployedService);
                        if (logger.isLoggable(Level.INFO))
                            logger.log(Level.INFO, "Allocated [" + LoggingUtil.getLoggingName(request) + "]");
                        if (logger.isLoggable(Level.FINEST)) {
                            Object service = jsbInstance.getService();
                            Class serviceClass = service.getClass();
                            logger.log(Level.FINEST,
                                       "{0} ServiceBeanInstance {1}, Annotation {2}",
                                       new Object[]{LoggingUtil.getLoggingName(request),
                                                    jsbInstance,
                                                    RMIClassLoader.getClassAnnotation(serviceClass)});
                        }
                        break;
                    } else {
                        if (logger.isLoggable(Level.FINER))
                            logger.finer(ir.getName() + " at [" + ir.getHostAddress() + "] " +
                                         "did not allocate [" + LoggingUtil.getLoggingName(request) + "], retry ...");
                        long retryWait = 1000;
                        try {
                            Thread.sleep(retryWait);
                        } catch (InterruptedException ie) {
                            if (logger.isLoggable(Level.FINEST))
                                logger.finest("Interrupted while sleeping [" + retryWait + "] " +
                                              "milliseconds for provision retry");
                        }
                    }
                }

            } catch (UnknownEventException e) {
                result = ServiceProvisioner.PROVISION_FAILURE;
                failureReason = e.getLocalizedMessage();
                logger.severe(failureReason);
                thrown = e;
                context.getSelector().dropServiceResource(serviceResource);
            } catch (RemoteException e) {
                result = ServiceProvisioner.PROVISION_FAILURE;
                Throwable t = ThrowableUtil.getRootCause(e);
                failureReason = t.getLocalizedMessage();
                thrown = e;
            } catch (JSBInstantiationException e) {
                if (e.isUninstantiable())
                    result = ServiceProvisioner.PROVISION_FAILURE | ServiceProvisioner.UNINSTANTIABLE_JSB;
                else
                    result = ServiceProvisioner.PROVISION_FAILURE;
                thrown = e;
                Throwable t = ThrowableUtil.getRootCause(e);
                failureReason = t.getLocalizedMessage();

            } catch (Throwable t) {
                result = ServiceProvisioner.PROVISION_FAILURE | ServiceProvisioner.UNINSTANTIABLE_JSB;
                thrown = t;
                t = ThrowableUtil.getRootCause(t);
                failureReason = t.getLocalizedMessage();
            }
        } finally {
            if (thrown != null) {
                if (!ThrowableUtil.isRetryable(thrown)) {
                    if (logger.isLoggable(Level.INFO))
                        logger.log(Level.INFO,
                                   "Drop {0} {1} from collection",
                                   new Object[]{ir.getName(), ir.getInstantiator()});
                    context.getSelector().dropServiceResource(serviceResource);
                    result = ServiceProvisioner.PROVISION_FAILURE | ServiceProvisioner.BAD_CYBERNODE;
                } else {
                    if (logger.isLoggable(Level.FINEST))
                        logger.log(Level.WARNING,
                                            "Provisioning ["+LoggingUtil.getLoggingName(request) + "] " +
                                            "to [" + ir.getHostAddress() + "]",
                                            thrown);
                    else
                        logger.warning("Provisioning [" +
                                                LoggingUtil.getLoggingName(request) + "] to " +
                                                "[" + ir.getHostAddress() + "], " +
                                                thrown.getClass().getName() + ": " +
                                                thrown.getLocalizedMessage());
                }

            }
            ir.decrementProvisionCounter(request.getServiceElement());
            long stop = System.currentTimeMillis();
            context.getWatch().addValue(stop - start);
        }
        return (result);
    }

    /*
     * Helper method to dispatch a ProvisionFailureEventTask and send a ProvisionFailureEvent
     */
    void processProvisionFailure(ProvisionFailureEvent event) {
        context.getProvisionFailurePool().execute(new ProvisionFailureEventTask(event, context.getFailureHandler()));
    }
}
