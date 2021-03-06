/*
 * Copyright 2008 the original author or authors.
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
package org.rioproject.system.measurable.disk;

import org.rioproject.exec.Util;
import org.rioproject.system.OperatingSystemType;
import org.rioproject.system.measurable.MeasurableMonitor;
import org.rioproject.system.measurable.SigarHelper;
import org.rioproject.watch.ThresholdValues;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The <code>DiskSpaceMonitor</code> monitors disk space usage. This class
 * uses either Hyperic SIGAR, or operations system specific utilities (like df)
 * to obtain this information. The use of SIGAR is preferred, and if not
 * available will use external <tt>df</t> exec by forking a process and parsing
 * it's results.
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
public class DiskSpaceMonitor implements MeasurableMonitor<DiskSpaceUtilization> {
    static Logger logger = Logger.getLogger(
        "org.rioproject.system.measurable.disk");
    private String id;
    private ThresholdValues tVals;
    private SigarHelper sigar;
    private String fileSystem = File.separator;
    private final Object updateLock = new Object();

    public DiskSpaceMonitor() {
        sigar = SigarHelper.getInstance();
    }

    public void setID(String id) {
        this.id = id;
    }

    public void setThresholdValues(ThresholdValues tVals) {
        this.tVals = tVals;
    }

    public void setFileSystemToMonitor(String fileSystem) {
        this.fileSystem = fileSystem;
    }
    
    public DiskSpaceUtilization getMeasuredResource() {
        DiskSpaceUtilization dsu;
        if(sigar==null) {
            dsu = getDiskSpaceUtilization();
        } else {
             dsu = getDiskSpaceUtilizationUsingSigar();
        }
        return dsu;
    }

    /* (non-Javadoc)
    * @see org.rioproject.system.measurable.MeasurableMonitor#terminate()
    */
    public void terminate() {
        /* implemented for interface compliance */
    }

    private DiskSpaceUtilization getDiskSpaceUtilizationUsingSigar() {
        DiskSpaceUtilization dsu;
        try {
            /*
            FileSystemUsage fUse = sigar.getFileSystemUsage(File.separator);
            double available = fUse.getFree()*1024;
            double used = fUse.getUsed()*1024;
            double total = fUse.getTotal()*1024;
            */
            double available = sigar.getFileSystemFree(fileSystem)*1024;
            double used = sigar.getFileSystemUsed(fileSystem)*1024;
            double total = sigar.getFileSystemTotal(fileSystem)*1024;
            dsu = new DiskSpaceUtilization(id,
                                           used,
                                           available,
                                           total,
                                           sigar.getFileSystemUsedPercent(fileSystem),
                                           tVals);
        } catch (Exception e) {
            logger.log(Level.WARNING,
                       "SIGAR exception getting FileSystemUsage",
                       e);
            dsu = new DiskSpaceUtilization(id, -1, tVals);
        }
        return dsu;
    }

    private DiskSpaceUtilization getDiskSpaceUtilization() {
        double used = 0;
        double available = 0;
        if (!OperatingSystemType.isWindows()) {
            Process process = null;
            DFOutputParser outputParser = null;
            try {
                synchronized (updateLock) {
                    try {
                        process = Runtime.getRuntime().exec("df -k");
                        outputParser =
                            new DFOutputParser(process.getInputStream());
                        outputParser.start();
                        updateLock.wait();
                    } catch (InterruptedException e) {
                        logger.log(Level.WARNING, "Waiting on updateLock", e);
                    }
                }
                used = outputParser.getUsed() * 1024;
                available = outputParser.getAvailable() * 1024;
            } catch (IOException e) {
                logger.log(Level.WARNING, "Executing or spawning [df-k]", e);
            } finally {
                if (outputParser != null)
                    outputParser.interrupt();
                if (process != null) {
                    Util.close(process.getOutputStream());
                    Util.close(process.getInputStream());
                    Util.close(process.getErrorStream());
                    process.destroy();
                }
            }
        }
        double capacity = used + available;
        return (new DiskSpaceUtilization(id,
                                         used,
                                         available,
                                         capacity,
                                         used / capacity,
                                         tVals));
    }

    /**
     * Class to parse output from the df -k command
     */
    class DFOutputParser extends Thread {
        InputStream in;
        double used;
        double available;

        public DFOutputParser(InputStream in) {
            this.in = in;
        }

        double getAvailable() {
            return (available);
        }

        double getUsed() {
            return (used);
        }

        public void run() {
            BufferedReader br = null;
            try {
                String fileSep = System.getProperty("file.separator");
                File root = new File(fileSep);
                List<String> list = new ArrayList<String>();
                InputStreamReader isr = new InputStreamReader(in);
                br = new BufferedReader(isr);
                String line;
                while ((line = br.readLine()) != null && !isInterrupted()) {
                    if (line.startsWith("Filesystem"))
                        continue;
                    StringTokenizer st = new StringTokenizer(line, " ");
                    while (st.hasMoreTokens())
                        list.add(st.nextToken());
                    /*
                     * The following 2 lines address the problem when the
                     * DiskSpaceMonitor parses the result of a 'df -k' and the
                     * local system has NFS mounted filesystems. On Linux (and
                     * possibly other OS's), if the name of filesystem (the
                     * first column) would run into the second column, then the
                     * df command prints the filesystem on the first line (by
                     * itself) and the remaining fields are printed on the
                     * second line. After tokenizing the df output, the run()
                     * method does a 'list.get(5)'. Unfortunately, list
                     * sometimes only contains 1 element.
                     */
                    if (list.size() == 1)
                        continue;

                    /*
                    * Get the mount point
                    */
                    String mountPoint = list.get(5);
                    if (root.getCanonicalPath().startsWith(mountPoint)) {
                        used = Double.parseDouble(list.get(2));
                        available = Double.parseDouble(list.get(3));
                    }
                    list.clear();
                }
            } catch (IOException e) {
                Logger.getAnonymousLogger().log(Level.INFO,
                                                "Grabbing output of df -k",
                                                e);
            } finally {
                try {
                    if (br != null)
                        br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                synchronized (updateLock) {
                    updateLock.notifyAll();
                }
            }
        }
    }
}
