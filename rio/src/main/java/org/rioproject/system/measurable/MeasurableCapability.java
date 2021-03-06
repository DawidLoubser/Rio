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
package org.rioproject.system.measurable;

import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import org.rioproject.costmodel.ResourceCost;
import org.rioproject.costmodel.ResourceCostModel;
import org.rioproject.costmodel.ResourceCostProducer;
import org.rioproject.costmodel.ZeroCostModel;
import org.rioproject.sla.SLA;
import org.rioproject.system.MeasuredResource;
import org.rioproject.watch.Calculable;
import org.rioproject.watch.PeriodicWatch;
import org.rioproject.watch.ThresholdManager;
import org.rioproject.watch.WatchDataSource;

import java.util.*;
import java.util.logging.Level;

/**
 * A MeasurableCapability refers to a depletion oriented resource or capability on
 * a ComputeResource
 *
 * @author Dennis Reedy
 */
public abstract class MeasurableCapability
    extends PeriodicWatch implements ResourceCostProducer,
                                     MeasurableCapabilityMBean {
    /**
     * Observable object for reporting state change
     */
    private Observatory observatory;
    /**
     * Collection of secondary ThresholdManager instances
     */
    private final Collection<ThresholdManager> thresholdManagers = new ArrayList<ThresholdManager>();
    /**
     * The SLA for the MeasurableCapability
     */
    private SLA sla;
    /**
     * The ResourceCostModel, determining how to charge for use of the 
     * MeasurableCapability
     */
    private ResourceCostModel costModel;
    /**
     * The sampleSize property specifies the amount of samples the 
     * MeasurableCapability will accumulate in the period defined by the reportRate 
     * in order to produce a result.
     */
    protected int sampleSize = 1;
    /** Configuration object */
    private Configuration config;
    /** Whether or not this measurable capability is enabled */
    private boolean isEnabled = true;
    /** The {@link MeasurableMonitor} to use */
    protected MeasurableMonitor monitor;
    protected MeasuredResource lastMeasured;

    protected MeasurableCapability(String id,
                                   String componentName,
                                   Configuration config) {
        super(id, config);
        this.config = config;
        try {
            isEnabled = (Boolean) config.getEntry(componentName,
                                                  "enabled",
                                                  boolean.class,
                                                  Boolean.TRUE);

        } catch (ConfigurationException e) {
            logger.log(Level.SEVERE, "Getting WatchDataSource Size", e);
        }
        if(!isEnabled)
            return;

        try {
            WatchDataSource wds =
                   (WatchDataSource)config.getEntry(componentName,
                                                    "watchDataSource",
                                                    WatchDataSource.class,
                                                    null);
            if(wds!=null) {
                setWatchDataSource(wds);
            }
        } catch (ConfigurationException e) {
            logger.log(Level.SEVERE, "Getting WatchDataSource Size", e); 
        }
        observatory = new Observatory();
        if(localRef!=null) 
            localRef.setMaxSize(100);
    }

    protected void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
    }

    /**
     * Get is this measurable capability is enabled
     *
     * @return If the measurable capability is enabled return true, otherwise
     * false. If the measurable capability is not enabled, it will not be added
     */
    public boolean isEnabled() {
        return isEnabled;
    }

    /**
     * Get the Configuration property
     *
     * @return The configuration for the MeasurableCapability
     */
    public Configuration getConfiguration() {
        return(config);
    }
    
    /**
     * Override parent's setWatchDataSource to set the size
     */
    @Override
    public void setWatchDataSource(WatchDataSource watchDataSource) {
        if(!isEnabled)
            throw new IllegalStateException("The MeasurableCapability " +
                                            "["+getId()+"] is not enabled");
        super.setWatchDataSource(watchDataSource);
        if(watchDataSource!=null) {            
            try {
                watchDataSource.setMaxSize(100);
            } catch(Exception e) {
                logger.log(Level.SEVERE, "Setting WatchDataSource Size", e);
            }
        }
    }     

    /**
     * Set the SLA for ths MeasurableCapability. 
     * 
     * @param sla The SLA for this MeasurableCapability
     */
    public void setSLA(SLA sla) {
        if(!isEnabled)
            throw new IllegalStateException("The MeasurableCapability " +
                                            "["+getId()+"] is not enabled");
        if(sla == null)
            throw new IllegalArgumentException("sla is null");
        this.sla = sla;
        getThresholdManager().setThresholdValues(sla);
    }

    /**
     * Get the SLA for ths MeasurableCapability. 
     * 
     * @return The SLA for this MeasurableCapability
     */
    public SLA getSLA() {
        return(sla);
    }
    
    /**
     * @see org.rioproject.system.measurable.MeasurableCapabilityMBean#setSampleSize
     */
    public void setSampleSize(int sampleSize) {
        if(!isEnabled)
            throw new IllegalStateException("The MeasurableCapability " +
                                            "["+getId()+"] is not enabled");
        this.sampleSize = sampleSize;             
    }

    /**
     * @see org.rioproject.system.measurable.MeasurableCapabilityMBean#getSampleSize()
     */
    public int getSampleSize() {
        return(sampleSize);
    }    
    
    /**
     * Add a secondary ThresholdManager to the MeasurableCapability. The primary 
     * ThresholdManager and the ThresholdValues are set when this class is loaded. 
     * By offering secondary ThresholdManager instances, services can set their own 
     * ranges and be notified accordingly
     * 
     * @param thresholdManager The ThresholdManager
     */
    public void addSecondaryThresholdManager(ThresholdManager thresholdManager) {
        if(!isEnabled)
            throw new IllegalStateException("The MeasurableCapability " +
                                            "["+getId()+"] is not enabled");
        if(thresholdManager==null)
            throw new IllegalArgumentException("thresholdManager is null");
        thresholdManagers.add(thresholdManager);
    }

    /**
     * Remove a secondary ThresholdManager from the MeasurableCapability
     * 
     * @param thresholdManager The ThresholdManager
     */
    public void removeSecondaryThresholdManager(ThresholdManager thresholdManager) {
        if(thresholdManager!=null)
            thresholdManagers.remove(thresholdManager);
    }

    /**
     * Set the ResourceCostModel for the MeasurableCapability
     * 
     * @param costModel The ResourceCostModel which will determine the cost of 
     * using this MeasurableCapability
     */
    public void setResourceCostModel(ResourceCostModel costModel) {
        if(!isEnabled)
            throw new IllegalStateException("The MeasurableCapability " +
                                            "["+getId()+"] is not enabled");
        if(costModel==null)
            throw new IllegalArgumentException("costModel is null");
        this.costModel = costModel;
    }

    /**
     * @see org.rioproject.costmodel.ResourceCostProducer#calculateResourceCost
     */
    public ResourceCost calculateResourceCost(double units, long duration) {
        if(!isEnabled)
            throw new IllegalStateException("The MeasurableCapability " +
                                            "["+getId()+"] is not enabled");
        if(costModel==null)
            costModel = new ZeroCostModel();
        double cost = costModel.getCostPerUnit(duration)*units;
        return(new ResourceCost(getId(), 
                                cost, 
                                units, 
                                costModel.getDescription(), 
                                new Date(System.currentTimeMillis())));
    }

    /**
     * Get the MeasuredResource object, which represents this object's measured 
     * capability
     *
     * @return This object's measured capability
     */
    public MeasuredResource getMeasuredResource() {
        if(!isEnabled)
            throw new IllegalStateException("The MeasurableCapability " +
                                            "["+getId()+"] is not enabled");
        if(lastMeasured==null)
            checkValue();

        return lastMeasured;
    }

    protected void setLastMeasuredResource(MeasuredResource lastMeasured) {
        this.lastMeasured = lastMeasured;
    }

    /**
     * Set the {@link MeasurableMonitor}
     *
     * @param monitor The MeasurableMonitor
     */
    public void setMeasurableMonitor(MeasurableMonitor monitor) {
        this.monitor = monitor;
        this.monitor.setID(getId());
        this.monitor.setThresholdValues(getThresholdValues());
    }    

    /**
     * Add a Calculable to the watch and update state
     * 
     * @param record A Calculable record
     */
    public void addWatchRecord(Calculable record) {
        if(!isEnabled)
            throw new IllegalStateException("The MeasurableCapability " +
                                            "["+getId()+"] is not enabled");
        super.addWatchRecord(record);
        observatory.stateChange(record);
        for (ThresholdManager tManager : getThresholdManagers())
            tManager.checkThreshold(record);
    }

    /**
     * Get the registered {@link ThresholdManager}s
     *
     * @return The registered {@link ThresholdManager}s. A new unmodifiable collection is allocated each time
     */
    protected Collection<ThresholdManager> getThresholdManagers() {
        Collection<ThresholdManager> tMgrs;
        synchronized(thresholdManagers) {
            tMgrs = Collections.unmodifiableCollection(thresholdManagers);
        }
        return tMgrs;
    }

    /**
     * Get the Observable instance
     * 
     * @return The object to subscribe for changes in MeasurableCapability 
     * state
     */
    public Observable getObservable() {
        if(!isEnabled)
            throw new IllegalStateException("The MeasurableCapability " +
                                            "["+getId()+"] is not enabled");
        return(observatory);
    }

    /**
     * Internal class for managing state change
     */
    static class Observatory extends Observable {
        Observatory() {
            super();
        }

        /**
         * Indicates that Observers need to be notified of the state change
         *
         * @param record The Calculable record representing the change
         */
        void stateChange(Calculable record) {
            setChanged();
            notifyObservers(record);
        }
    }
}
