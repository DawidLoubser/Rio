/*
 * Copyright 2008 to the original author or authors.
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
package org.rioproject.system.measurable.cpu;

import org.rioproject.system.OperatingSystemType;
import org.rioproject.system.measurable.MeasurableMonitor;
import org.rioproject.system.measurable.SigarHelper;
import org.rioproject.watch.ThresholdValues;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CPU monitor that obtains system CPU utilization. This utility uses either
 * Hyperic SIGAR, JDK 1.6 or operating system utilities to obtain CPU
 * utilization for the machine. Hyperic SIGAR is preferred. If not available
 * JDK 1.6 facilities will be used. If neither of these approaches work
 * operating system utilities will be used (depending on the OS) to obtain
 * CPU utilization.
 *
 * <p><b>Note:</b>
 * <a href="http://www.hyperic.com/products/sigar.html">Hyperic SIGAR</a>
 * is licensed under the GPL with a FLOSS license exception, allowing it to be
 * included with the Rio Apache License v2 distribution. If for some reason the
 * GPL cannot be used with your distribution of Rio,
 * remove the <tt>RIO_HOME/lib/hyperic</tt> directory.
 *
 * @author Dennis Reedy
 */
public class SystemCPUHandler implements MeasurableMonitor<CpuUtilization> {
    private String id;
    private ThresholdValues tVals;
    private SigarHelper sigar;
    private OperatingSystemMXBean opSysMBean = null;
    private Method jmxCPUUtilization;
    private MeasurableMonitor<CpuUtilization> altMonitor;
    static Logger logger =
        Logger.getLogger(SystemCPUHandler.class.getPackage().getName());

    public SystemCPUHandler() {
        sigar = SigarHelper.getInstance();
        if(sigar==null) {
            String jvmVersion = System.getProperty("java.version");
            if(!jvmVersion.contains("1.5")) {
                opSysMBean = ManagementFactory.getOperatingSystemMXBean();
                try {
                    jmxCPUUtilization =
                        OperatingSystemMXBean.class.getMethod("getSystemLoadAverage");
                } catch (NoSuchMethodException e) {
                    logger.log(Level.WARNING,
                               "Unable to obtain " +
                               "OperatingSystemMXBean.getSystemLoadAverage " +
                               "method",
                               e);
                }
            }
            if(jmxCPUUtilization==null) {
                if(OperatingSystemType.isLinux()) {
                    if(logger.isLoggable(Level.FINE))
                        logger.fine("Create LinuxHandler");
                    altMonitor = new LinuxHandler();
                } else if(OperatingSystemType.isSolaris()) {
                    if(logger.isLoggable(Level.FINE))
                        logger.fine("Create MpstatOutputParser");
                    altMonitor = new MpstatOutputParser();
                } else if(OperatingSystemType.isMac()) {
                    if(logger.isLoggable(Level.FINE))
                        logger.fine("Create MacTopOutputParser");
                    altMonitor = new MacTopOutputParser();
                } else {
                    if(logger.isLoggable(Level.FINE))
                        logger.fine("Create GenericCPUMeasurer");
                    altMonitor = new GenericCPUMeasurer();
                }
            }
        }
    }

    public void setID(String id) {
        this.id = id;
        if(altMonitor!=null)
            altMonitor.setID(id);
    }

    public void setThresholdValues(ThresholdValues tVals) {
        this.tVals = tVals;
        if(altMonitor!=null)
            altMonitor.setThresholdValues(tVals);
    }

    public CpuUtilization getMeasuredResource() {
        CpuUtilization util;
        if(sigar!=null) {
            util = getSigarCpuUtilization();
        } else {
            if(jmxCPUUtilization!=null) {
                util = getJmxCpuUtilization();
            } else {
                util = altMonitor.getMeasuredResource();
            }
        }

        return util;
    }

    public void terminate() {
        if(altMonitor!=null)
            altMonitor.terminate();
    }

    private CpuUtilization getSigarCpuUtilization() {
        return new CpuUtilization(id,
                                   sigar.getSystemCpuPercentage(),
                                   sigar.getUserCpuPercentage(),
                                   sigar.getLoadAverage(),
                                   Runtime.getRuntime().availableProcessors(),
                                   tVals);
    }

    private CpuUtilization getJmxCpuUtilization() {
        double cpuUtilization = -1;
        try {
            cpuUtilization = (Double)jmxCPUUtilization.invoke(opSysMBean);
            cpuUtilization = (cpuUtilization>0?cpuUtilization/100:cpuUtilization);
        } catch (Exception e) {
            logger.log(Level.WARNING,
                       "Could not get SystemloadAverage using reflection on " +
                       "OperatingSystemMXBean.getSystemLoadAverage()",
                       e);
        }
        return new CpuUtilization(id, cpuUtilization, tVals);
    }
}

