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
package org.rioproject.system.capability;

import org.rioproject.core.provision.DownloadRecord;
import org.rioproject.core.provision.StagedSoftware;
import org.rioproject.core.provision.SystemRequirements.SystemComponent;
import org.rioproject.costmodel.ResourceCost;
import org.rioproject.costmodel.ResourceCostModel;
import org.rioproject.costmodel.ResourceCostProducer;
import org.rioproject.costmodel.ZeroCostModel;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 A PlatformCapability defines a specific type of mechanism or quality
 associated with a compute resource, and is used to define base platform
 capabilities and resources. These capabilities allow better control
 over resources and better provisioning behaviour. In turn, this
 leads to optimal ServiceBean compute ability.<br>
 <br>
 PlatformCapability attributes will be stored as (key,&nbsp;value) pairs
 in a HashMap and tested for supportability against {@link
 org.rioproject.core.provision.SystemRequirements.SystemComponent} attributes.
 Attributes obtained from the <code>SystemRequirement</code>
 class will be tested for supportability using regular expression
 matching. <br>
 <br>
 If the <code>SystemRequirement</code> class contains a&nbsp;
 {@link PlatformCapability#VERSION} requirement, the required version
 value is processed as follows:<br>
 <br>
 The {@link PlatformCapability#VERSION} determines which version of the
 PlatformCapability is required. Without this key set, your service may
 not launch with the desired version of the PlatformCapability. The
 "Version" value can use the asterisk (*) or plus (+) symbols to
 determine version requirements.&nbsp; <br>
 <br>
 <table cellpadding="2" cellspacing="2" border="1"
 style="text-align: left; width: 100%;">
 <tbody>
 <tr>
 <th style="vertical-align: top;">Requirement<br>
 </th>
 <th style="vertical-align: top;">Support Criteria<br>
 </th>
 </tr>
 <tr>
 <td style="vertical-align: top;">1.2.7<br>
 </td>
 <td style="vertical-align: top;">Specifies an exact version<br>
 </td>
 </tr>
 <tr>
 <td style="vertical-align: top;">2*<br>
 </td>
 <td style="vertical-align: top;">Supported for all minor versions
 of 2 <br>
 </td>
 </tr>
 <tr>
 <td style="vertical-align: top;">3.4*<br>
 </td>
 <td style="vertical-align: top;">Supported for all minor versions
 of 3.4, including 3.4<br>
 </td>
 </tr>
 <tr>
 <td style="vertical-align: top;">1.2+<br>
 </td>
 <td style="vertical-align: top;">Supported for version 1.2 or
 above&nbsp; </td>
 </tr>
 </tbody>
 </table>
 <br>
 Version requirements are expected to be a "." separated String of
 integers. Character values are ignored. For example;&nbsp; a version
 declaration of "2.0-M3" will be processed as "2.0.0.3"<br>

 * @author Dennis Reedy
 */
public class PlatformCapability implements PlatformCapabilityMBean,
                                           ResourceCostProducer,
                                           Serializable {
    static final long serialVersionUID = 1L;
    /** Manufacturer information for the capability */
    public final static String MANUFACTURER = "Manufacturer";
    /** Model information for the capability  */
    public final static String MODEL = "Model";
    /** Name information for the capability */
    public final static String NAME = "Name";
    /** Vender information for the capability */
    public final static String VENDOR = "Vendor";
    /** Version information for the capability */
    public final static String VERSION = "Version";
    /** Description information for the capability */
    public final static String DESCRIPTION = "Description";
    /** Native Libraries Key. If value(s) are provided for this key they will
     * be loaded when the loadResources method is invoked.
     *
     * If this key is provided value(s) must be separated by a ":" delimeter.
     * For example <code>lib1:lib2:lib3</code>
     */
    public final static String NATIVE_LIBS = "NativeLibs";
    /** Indicates that the PlatformCapability must be installed */
    public static final int STATIC = 1;
    /** Indicates that the PlatformCapability may be provisioned */
    public static final int PROVISIONABLE = 2;
    /** The type of this PlatformCapability, STATIC or PROVISIONABLE. 
     * Defaults to STATIC */
    private int type = STATIC;
    /** Map of platform capability key and values */
    protected final Hashtable<String, Object> capabilities =
        new Hashtable<String, Object>();
    /** A description of the PlatformCapability */
    protected String description;
    /** StagedSoftware defining where the software for this
     * PlatformCapability can be downloaded from */
    private final List<StagedSoftware> stagedSoftware =
        new ArrayList<StagedSoftware>();
    /** List DownloadRecord instances indicating that software for
     * this PlatformCapability has been downloaded */
    private final List<DownloadRecord> downloadRecords =
        new ArrayList<DownloadRecord>();
    /** The path on an accessible filesystem where the PlatformCapability is 
     * installed*/
    private String path;
    /** The ResourceCostModel, determining how to charge for use of the 
     * PlatformCapability */
    private ResourceCostModel costModel;
    /** Classpath for the PlatformCapability */
    private String[] classpath;
    private static final Map<Integer, AtomicInteger> usageMap = new HashMap<Integer, AtomicInteger>();
    /** Meta chars for regex matching */
    private static final String[] META_CHARS = {"*", "(", "[", "\\", "^",
                                                "$", "|", ")", "?", "+"};
    private String configurationFile;
    /** A suitable Logger */
    protected static final Logger logger =
        Logger.getLogger("org.rioproject.system.capability");

    /**
     * Set the path of the PlatformCapability
     *
     * @param path The path on an accessible filesystem where the
     * PlatformCapability is installed
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Get the path of the PlatformCapability, where it is located on the
     * filesystem
     *
     * @return The path on an accessible filesystem where the
     * PlatformCapability is installed
     */
    public String getPath() {
        return(path);
    }

    /**
     * Set the classpath of the PlatformCapability
     *
     * @param classpath The classpath for the PlatformCapability
     */
    public void setClassPath(String[] classpath) {
        this.classpath = classpath;
    }

    /**
     * Get the classpath for the PlatformCapability
     *
     * @return The classpath as a String array. If no classpath has been set,
     * return null
     */
    public String[] getClassPath() {
        return classpath;
    }

    /**
     * Allows the PlatformCapability to load any required native libraries as 
     * determined by values in the <code>NativeLibsKey</code> using the
     * <code>System.loadLibrary</code> method.
     *
     * Libraries are loaded from the location pointed to by the
     * <code>java.library.path</code>
     */
    public void loadResources() {
        String values = (String)capabilities.get(NATIVE_LIBS);
        if(values==null)
            return;
        StringTokenizer sTok = new StringTokenizer(values, " \t\n\r\f:");
        while(sTok.hasMoreTokens()) {
            String libName = sTok.nextToken();
            if(logger.isLoggable(Level.FINE))
                logger.fine("Loading ["+libName+"] for "+
                            getClass().getName());
            System.out.println("Loading ["+libName+"] for "+
                               getClass().getName());
            System.loadLibrary(libName);
            if(logger.isLoggable(Level.FINE))
                logger.fine("Loaded ["+libName+"] for "+
                            getClass().getName());
            System.out.println("Loaded ["+libName+"] for "+
                               getClass().getName());
        }
    }
    
    /**
     * Define a platform capability mapping
     *
     * @param key The key associated with the platform capability. Can be a name 
     * or another descriptive attribute used to uniquely identify the platform 
     * capability
     * @param value The value associated with the platform capability key
     */
    public void define(String key, Object value) {
        capabilities.put(key, value);
        if(key.equals(DESCRIPTION)) {
            description = value.toString();
        }
    }

    /**
     * Define all platform capability mappings to the platform capability Map
     *
     * @param map The Map containing platform capability mappings
     */
    public void defineAll(Map<String, Object> map) {
        capabilities.putAll(map);
        for(Enumeration<String> en=capabilities.keys(); en.hasMoreElements();) {
            String key = en.nextElement();
            if(key.equals(DESCRIPTION)) {
                description = capabilities.get(key).toString();
            }    
        }
    }

    /**
     * Get the value associated with the key in the platform capability mapping.
     * If there is no value then this method will return null
     * 
     * @param key The platform capability key
     * @return The value associated with the key in the platform
     * capability mapping
     */
    public Object getValue(String key) {
        return(capabilities.get(key));
    }
    
    /**
     * Remove a defined platform capability mapping
     *
     * @param key The platform capability key to remove
     *
     * @return True if removed, false otherwise
     */
    public boolean remove(String key) {
        Object o = capabilities.remove(key);
        return o != null;
    }

    /**
     * Clear all platform capability mappings
     */
    public void clearAll() {
        capabilities.clear();
    }

    /**
     * Determine if the provided PlatformCapability be supported. A
     * PlatformCapability can be supported if this PlatformCapability is the
     * same classname as the input PlatformCapability and all key,value
     * parameters provided in the input PlatformCapability are found and equal
     * to mappings in this PlatformCapability
     * 
     * @param requirement The PlatformCapability to requirement
     * @return True if supported, false otherwise
     * 
     * @deprecated
     */
    public boolean supports(PlatformCapability requirement) {
        boolean supports = 
            getClass().getName().equals(requirement.getClass().getName());
        if(supports) {
            String[] keys = requirement.getPlatformKeys();
            for (String key : keys) {
                if (capabilities.containsKey(key)) {
                    Object myMapping = capabilities.get(key);
                    Object theirMapping = requirement.getValue(key);
                    if (!(myMapping.equals(theirMapping))) {
                        supports = false;
                        break;
                    }
                } else {
                    supports = false;
                    break;
                }
            }
        }
        return(supports);
    }


    /**
     * Determine if the provided 
     * {@link org.rioproject.core.provision.SystemRequirements.SystemComponent} can
     * be supported. A SystemRequirement can be supported if this
     * PlatformCapability is the same class name (sans the package name) as
     * the input <code>SystemRequirement.getClassName()</code> or the same
     * fully qualified classname as provided by the
     * <code>SystemRequirement.getClassName()</code> property and all
     * key,value parameters provided in the input SystemRequirement
     * are found and equal to mappings in this PlatformCapability
     * 
     * @param requirement The SystemRequirement to test for supportability.
     *  
     * @return True if supported, false otherwise
     */
    public boolean supports(SystemComponent requirement) {
        boolean supports = hasBasicSupport(requirement.getName(),
                                           requirement.getClassName());                
        if(supports) {
            Map<String, Object> attributes = requirement.getAttributes();
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                String key = entry.getKey();
                if (capabilities.containsKey(key)) {
                    Object myMapping = capabilities.get(key);
                    Object theirMapping = entry.getValue();
                    if (myMapping instanceof String &&
                        theirMapping instanceof String) {
                        if (key.equals(VERSION)) {
                            if (!(versionSupported((String) theirMapping,
                                                   (String) myMapping))) {
                                supports = false;
                                break;
                            }
                        } else {
                            if (!(matches((String) theirMapping,
                                          (String) myMapping))) {
                                supports = false;
                                break;
                            }
                        }
                    } else {
                        if (!(myMapping.equals(theirMapping))) {
                            supports = false;
                            break;
                        }
                    }

                } else {
                    supports = false;
                    break;
                }
            }
        }
        return(supports);
    }

    /**
     * Determine basic supportability
     * 
     * @param name The name of the class, sans the package name. If this
     * parameter is not null, this will be checked
     * @param className The fully qualified className to match if the name
     * is null
     *
     * @return true if basic support is provided
     */
   protected boolean hasBasicSupport(String name, String className) {
       if(name==null && className==null)
           throw new IllegalArgumentException("className and name cannot " +
                                              "both be null");
       boolean supports = false;
       String myClassName = getClass().getName();
       if(name!=null) {
           String myName = myClassName;
           int ndx = myClassName.lastIndexOf(".");
           if(ndx>0)
               myName = myClassName.substring(ndx+1);
           supports = myName.equals(name);
       }
       if(!supports && className!=null)                     
           supports = myClassName.equals(className);
       return(supports);
    }


    /**
     * Matching semantics are accomplished using pattern matching with
     * regular expressions
     *
     * @param input The regular expression, specified as a string. Must
     * not be <code>null</code>
     * @param value Match the given input against this pattern.
     * @return <code>true</code> if, and only if, a subsequence of the
     * regex matches this matcher's pattern
     */
    protected boolean matches(String input, String value) {
        boolean useFind = false;
        for (String meta_char : META_CHARS) {
            if (input.indexOf(meta_char) != -1) {
                useFind = true;
                break;
            }
        }
        Pattern pattern = Pattern.compile(input);
        Matcher matcher = pattern.matcher(value);
        boolean matches;
        if(useFind)
            matches = matcher.find();
        else
            matches = matcher.matches();
        return (matches);
    }

    /**
     * Determine if versions are supported
     *
     * @param requiredVersion The required version, specified as a string. Must
     * not be <code>null</code>
     * @param configuredVersion  The configured version value
     * @return <code>true</code> if, and only if, the request version can be
     * supported
     */
    protected boolean versionSupported(String requiredVersion,
                                       String configuredVersion) {
        if(requiredVersion == null)
            throw new IllegalArgumentException("requiredVersion is null");
        if(configuredVersion == null)
            throw new IllegalArgumentException("configuredVersion is null");
        boolean supported;

        if(requiredVersion.endsWith("*") || requiredVersion.endsWith("+")) {
            boolean minorVersionSupport = requiredVersion.endsWith("*");
            requiredVersion =
                requiredVersion.substring(0, requiredVersion.length()-1);
            int[] required;
            int[] configured;
            try {
                required = toIntArray(requiredVersion.split("\\D"));
                configured = toIntArray(configuredVersion.split("\\D"));
            } catch(NumberFormatException e) {
                return (false);
            }
            if(required.length == 0)
                return (true);

            /* Minor version support */
            if(minorVersionSupport) {
                supported = true;
                for(int i = 0; i < required.length; i++) {
                    if(configured.length >= (i+1)) {
                        if(configured[i] != required[i]) {
                            supported = false;
                            break;
                        }
                    }
                }
            } else {
                supported = true;
                /* Major version support */
                for(int i = 0; i < required.length; i++) {
                    if(configured.length >= (i+1)) {
                        if(configured[i] > required[i]) {
                            break;
                        }
                        if(configured[i] == required[i]) {
                            continue;
                        }
                        if(configured[i] < required[i]) {
                            supported = false;
                            break;
                        }
                    }
                }
            }
        } else {
            supported = configuredVersion.equals(requiredVersion);
        }
        return (supported);
    }

    /**
     * Convert a String array into an int array
     * @param a String array
     * @return An int array
     * @throws NumberFormatException if the value in the array is not a number
     */
    private int[] toIntArray(String[] a) throws NumberFormatException {
        int[] array = new int[a.length];
        for(int i = 0; i < array.length; i++) {
            if(a[i].length() == 0)
                array[i] = Integer.parseInt("0");
            else
                array[i] = Integer.parseInt(a[i]);
        }
        return (array);
    }

    /**
     * Get the keys for the platform capability
     *
     * @return An array of the defined keys in the platform capability map
     */
    public String[] getPlatformKeys() {
        Set<String> set = capabilities.keySet();
        return(set.toArray(new String[set.size()]));
    }

    /**
     * Set the description associated with the PlatformCapability
     *
     * @param description Description of the PlatformCapability
     */
    public void setDescription(String description) {
        if(description!=null)
            this.description = description;
    }
    
    /**
     * Get the description associated with the PlatformCapability
     *
     * @return Description of the PlatformCapability
     */
    public String getDescription() {
        return(description);
    }

    /**
     * Get the name of this PlatformCapability
     *
     * @return The name of the PlatformCapability
     */
    public String getName() {
        String s = (String)getValue(PlatformCapability.NAME);
        return ((s == null?"":s));
    }

    /**
     * @see org.rioproject.system.capability.PlatformCapabilityMBean#getCapabilities
     */
    public Map<String, Object> getCapabilities() {
        Hashtable<String, Object> h = new Hashtable<String, Object>();
        h.putAll(capabilities);
        return (h);
    }

    /**
     * Set the {@link org.rioproject.costmodel.ResourceCostModel}
     * for the PlatformCapability
     *
     * @param costModel The ResourceCostModel that will determine the cost
     * of using this PlatformCapability
     */
    public void setResourceCostModel(ResourceCostModel costModel) {
        if(costModel==null)
            throw new IllegalArgumentException("costModel is null");
        this.costModel = costModel;
    }

    /**
     * Get the {@link org.rioproject.costmodel.ResourceCostModel}
     * for the PlatformCapability
     *
     * @return The {@link org.rioproject.costmodel.ResourceCostModel} 
     * for the PlatformCapability
     */
    public ResourceCostModel getResourceCostModel() {
        return costModel;
    }

    /**
     * @see org.rioproject.costmodel.ResourceCostProducer#calculateResourceCost
     */
    public ResourceCost calculateResourceCost(double units, long duration) {
        if(costModel==null)
            costModel = new ZeroCostModel();
        double cost = costModel.getCostPerUnit(duration)*units;
        return(new ResourceCost(getDescription(),
                                cost, 
                                units, 
                                costModel.getDescription(), 
                                new Date(System.currentTimeMillis())));
    }

    /**
     * Add StagedSoftware for the PlatformCapability. Adding
     * StagedSoftware will also set the type of the PlatformCapability to be
     * PROVISIONABLE
     *
     * @param software The StagedSoftware defining where the software for
     * the PlatformCapability can be downloaded from
     */
    public void addStagedSoftware(StagedSoftware... software) {
        if(software ==null)
            throw new IllegalArgumentException("StagedSoftware is null");
        stagedSoftware.addAll(Arrays.asList(software));
        setType(PROVISIONABLE);
    }
    
    /**
     * Get the StagedSoftware for the PlatformCapability
     *
     * @return Array of StagedSoftware objects defining provisionable software for
     * the PlatformCapability. If there is no StagedSoftware return a
     * zero-length array
     */
    public StagedSoftware[] getStagedSoftware() {
        return(stagedSoftware.toArray(new StagedSoftware[stagedSoftware.size()]));
    }

    /**
     * Add a DownloadRecord instance for the PlatformCapability
     *
     * @param record A DownloadRecord instance defining where
     * software has been installed
     */
    public void addDownloadRecord(DownloadRecord record) {
        if(record==null)
            throw new IllegalArgumentException("record is null");
        synchronized(downloadRecords) {
            downloadRecords.add(record);
        }
    }

    /**
     * Get the DownloadRecord instances for the PlatformCapability
     * 
     * @return Array of DownloadRecord instances recording where the
     * software for the PlatformCapability has been downloaded to. If there
     * are no DownloadRecord instances recorded, a zero-length array
     * will be returned
     */
    public DownloadRecord[] getDownloadRecords() {
        DownloadRecord[] records;
        synchronized(downloadRecords) {
            records =
                downloadRecords.toArray(
                    new DownloadRecord[downloadRecords.size()]);
        }
        return(records);
    }

    /**
     * Get the type of PlatformCapability
     *
     * @return The type of the PlatformCapability
     */
    public int getType() {
        return(type);
    }

    /**
     * Set the type of this record to be either STATIC or PROVISIONABLE
     *
     * @param type The type of the PlatformCapability
     *
     * @throws IllegalArgumentException if the supplied type is neither
     * STATIC or PROVISIONABLE
     */
    protected void setType(int type) {
        if(type != STATIC && type != PROVISIONABLE)
            throw new IllegalArgumentException("bad type : "+type);
        this.type = type;
    }

    public void incrementUsage() {
        synchronized(usageMap) {
            AtomicInteger count = usageMap.get(hashCode());
            if(count==null) {
                count = new AtomicInteger();
            }
            int val = count.incrementAndGet();
            logger.info("PlatformCapability ["+getName()+"], count="+val);
            usageMap.put(hashCode(), count);
        }
    }

    public void decrementUsage() {
        synchronized(usageMap) {
            AtomicInteger count = usageMap.get(hashCode());
            if(count==null) {
                logger.info("PlatformCapability ["+getName()+"], unknown count, " +
                            "returning");
                return;
            }
            if(count.intValue()==0) {
                logger.info("PlatformCapability ["+getName()+"], count already at 0, " +
                            "returning");
                return;
            }
            int val = count.decrementAndGet();
            logger.info("PlatformCapability ["+getName()+"], count="+val);
            usageMap.put(hashCode(), count);
        }
    }

    public int getUsageCount() {
        int val;
        synchronized(usageMap) {
            AtomicInteger count = usageMap.get(hashCode());
            if(count==null) {
                val = 0;
            } else {
                val = count.intValue();
            }
        }
        return val;
    }

    public String getConfigurationFile() {
        return configurationFile;
    }

    public void setConfigurationFile(String configurationFile) {
        this.configurationFile = configurationFile;
    }

    /**
     * Override hashCode to return the hashCode of the capabilities Map
     */
    public int hashCode() {
        int hc = 17;
        hc = 37*hc+this.getClass().hashCode();
        hc = 37*hc+capabilities.hashCode();
        return(hc);
    }

    /**
     * A PlatformCapability is equal to another PlatformCapability if they
     * are the same class and their capabilities maps are equal
     */
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass())
            return false;
        if(this == obj)
            return(true);
        if(this.getClass().isInstance(obj)) {
            PlatformCapability that = (PlatformCapability)obj;
            return(this.capabilities.equals(that.capabilities));
        }
        return(false);
    }
    
    /**
     * String representation of a PlatformCapability
     */
    public String toString() {
        return((description==null?"<no description>":description)+": "+
            capabilities.toString());
    }
}
