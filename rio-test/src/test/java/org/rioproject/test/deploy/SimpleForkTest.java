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
package org.rioproject.test.deploy;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.rioproject.core.OperationalString;
import org.rioproject.core.OperationalStringManager;
import org.rioproject.core.ServiceBeanInstance;
import org.rioproject.core.ServiceElement;
import org.rioproject.cybernode.Cybernode;
import org.rioproject.exec.ExecDescriptor;
import org.rioproject.jmx.JMXConnectionUtil;
import org.rioproject.jmx.JMXUtil;
import org.rioproject.test.IfPropertySet;
import org.rioproject.test.RioTestRunner;
import org.rioproject.test.SetTestManager;
import org.rioproject.test.TestManager;
import org.rioproject.test.simple.Fork;

import javax.management.MBeanServerConnection;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tests simple deploy scenario that forks a service
 */
@RunWith (RioTestRunner.class)
@IfPropertySet (name = "os.name", notvalue = "Windows*")
public class SimpleForkTest {
    @SetTestManager
    static TestManager testManager;
    Cybernode cybernode;
    static Logger logger = Logger.getLogger(RioTestRunner.class.getName());

    @Before
    public void setup() {
        cybernode = (Cybernode)testManager.waitForService(Cybernode.class);
    }

    @Test
    public void testFork() {
        Assert.assertNotNull(testManager);
        Assert.assertNotNull(cybernode);
        Throwable thrown = null;
        try {
            OperationalStringManager mgr = testManager.getOperationalStringManager();
            Assert.assertNotNull("Expected non-null OperationalStringManager", mgr);
            OperationalString opstring = mgr.getOperationalString();
            Assert.assertNotNull(opstring);
            Assert.assertEquals(1, opstring.getServices().length);
            ServiceElement elem = opstring.getServices()[0];
            Assert.assertNotNull("Expected a non-null ExecDescriptor",
                                 elem.getExecDescriptor());
            testManager.waitForDeployment(mgr);
            String jvmVersion = System.getProperty("java.version");
            if(jvmVersion.contains("1.5")) {
                logger.info("The JMX Attach APIs require Java 6 or above. " +
                            "You are running Java "+jvmVersion);
            } else {
                MBeanServerConnection mbsc = attach();
                Assert.assertNotNull("Expected a MBeanServerConnection", mbsc);
                verifyJVMArgs(mbsc, elem.getExecDescriptor());
            }
            ServiceBeanInstance[] instances =
                cybernode.getServiceBeanInstances(opstring.getServices()[0]);
            Assert.assertEquals(1, instances.length);
            Fork fork = (Fork)instances[0].getService();
            Assert.assertTrue("Expected verify() to " +
                              "return true, check service log for details",
                              fork.verify());
            testManager.undeploy(opstring.getName());
        } catch(Exception e) {
            thrown = e;
            e.printStackTrace();
        }
        Assert.assertNull("Should not have thrown an exception", thrown);
    }

    private void verifyJVMArgs(MBeanServerConnection mbsc,
                               ExecDescriptor exDesc) {
        RuntimeMXBean runtime =
            JMXUtil.getPlatformMXBeanProxy(mbsc,
                                           ManagementFactory.RUNTIME_MXBEAN_NAME,
                                           RuntimeMXBean.class);
        String[] declaredArgs = toArray(exDesc.getInputArgs());
        List<String> jvmArgs = runtime.getInputArguments();
        logger.info("Runtime JVM Args ["+flatten(jvmArgs)+"]");
        for(String arg : declaredArgs) {
            boolean matched = false;
            for(String jvmArg : jvmArgs) {
                if(arg.equals(jvmArg)) {
                    matched = true;
                    break;
                }
            }
            Assert.assertTrue("Expected to match ["+arg+"]", matched);
        }
    }

    private MBeanServerConnection attach() {
        MBeanServerConnection mbsc = null;
        long forkedPID = -1;
        String[] managedVMs = JMXConnectionUtil.listManagedVMs();
        for(String managedVM : managedVMs) {
            if(managedVM.indexOf("start-service-bean-exec")!=-1) {
                String pid = managedVM.substring(0, managedVM.indexOf(" "));
                forkedPID = Long.valueOf(pid);
                break;
            }
        }

        if(forkedPID!=-1) {
            logger.info("PID of exec'd process obtained: "+forkedPID);
            try {
                mbsc = JMXConnectionUtil.attach(Long.toString(forkedPID));
                logger.info("JMX Attach succeeded to exec'd JVM with pid: "+forkedPID);
            } catch(Exception e) {
                logger.log(Level.WARNING,
                           "Could not attach to the exec'd " +
                           "JVM with pid: "+forkedPID+", " +
                           "continue service execution",
                           e);
            }
        } else {
            logger.info("Could not obtain actual pid of " +
                        "exec'd process, process cpu and " +
                        "java memory utilization are not available");
        }

        return mbsc;
    }

    private String[] toArray(String s) {
        StringTokenizer tok = new StringTokenizer(s);
        String[] array = new String[tok.countTokens()];
        int i=0;
        while(tok.hasMoreTokens()) {
            array[i] = tok.nextToken();
            i++;
        }
        return(array);
    }

    private String flatten(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) {
            sb.append(s).append(" ");
        }
        return sb.toString();
    }
}
