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
package org.rioproject.monitor.handlers;

import org.rioproject.core.OperationalString;
import org.rioproject.opstring.OAR;
import org.rioproject.opstring.OARException;
import org.rioproject.opstring.OARUtil;
import org.rioproject.resources.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * A {@link DeployHandler} that handles OAR files. This <tt>DeployHandler</tt>
 * uses two directories; a <tt>dropDirectory</tt>, and a
 * <tt>installDirectory</tt>.
 *
 * <p>The <tt>dropDirectory</tt> is polled for files ending in ".oar". If found,
 * the OAR files are installed to the <tt>installDirectory</tt>, where
 * {@link org.rioproject.core.OperationalString}s are created and returned.
 *
 */
public class FileSystemOARDeployHandler extends AbstractOARDeployHandler {
    private File dropDirectory;
    private File installDirectory;
    private final Map<String, Date> badOARs = new HashMap<String, Date>();

    /**
     * Create a FileSystemOARDeployHandler with the same drop and install
     * directory
     *
     * @param dir The drop and installation directory for OAR files
     */
    public FileSystemOARDeployHandler(File dir) {
        this(dir, dir);        
    }

    /**
     * Create a FileSystemOARDeployHandler with drop and install
     * directories
     *
     * @param dropDirectory The directory where OAR files will be dropped
     * @param installDirectory The directory to install OARs into
     */
    public FileSystemOARDeployHandler(File dropDirectory,
                                      File installDirectory) {
        super();
        this.dropDirectory = dropDirectory;
        this.installDirectory = installDirectory;
        if(!dropDirectory.exists()) {
            if(dropDirectory.mkdirs()) {
                logger.config("Created dropDeployDir " +
                              FileUtils.getFilePath(dropDirectory));
            }
        }
        if(!installDirectory.exists()) {
            if(installDirectory.mkdirs())
                logger.config("Created installDir " +
                              FileUtils.getFilePath(installDirectory));
        }
    }

    protected List<OperationalString> look(Date from) {
        List<OperationalString> list = new ArrayList<OperationalString>();
        File[] files = dropDirectory.listFiles();
        for (File file : files) {
            if (!file.isDirectory()) {
                if (file.getName().endsWith("oar") &&!isBad(file)) {
                    try {
                        install(file, installDirectory);
                    } catch (IOException e) {
                        logger.log(Level.WARNING,
                                   "Could not install ["+file.getName()+"] " +
                                   "to ["+installDirectory.getName()+"]",
                                   e);
                        badOARs.put(file.getName(), new Date(file.lastModified()));
                    } catch (Exception e) {
                        logger.warning("The ["+file.getName()+"] is an " +
                                   "invalid OAR and cannot be installed, "+
                                   e.getClass().getName()+": "+e.getMessage());
                        badOARs.put(file.getName(), new Date(file.lastModified()));
                    }
                }
            }
        }

        files = installDirectory.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                try {
                    OAR oar = OARUtil.getOAR(file);
                    if (oar!=null && oar.getActivationType().equals(OAR.AUTOMATIC)) {
                        list.addAll(parseOAR(oar, from));
                    }
                } catch (IOException e) {
                    logger.log(Level.WARNING,
                               "Loading [" + file.getName() + "]",
                               e);
                } catch (OARException e) {
                    logger.log(Level.WARNING,
                               "Unable to install [" + file.getName() + "]",
                               e);
                }
            }
        }

        return list;
    }

    private boolean isBad(File oar) {
        Date badOar = null;
        for(Map.Entry<String, Date> entry : badOARs.entrySet()) {
            if(entry.getKey().equals(oar.getName())) {
                badOar = entry.getValue();
                break;
            }
        }
        boolean isBad = false;
        if(badOar!=null) {
            Date oarDate = new Date(oar.lastModified());
            if (oarDate.after(badOar)) {
                badOARs.remove(oar.getName());
            } else {
                isBad = true;
            }
        }
        return isBad;
    }
}
