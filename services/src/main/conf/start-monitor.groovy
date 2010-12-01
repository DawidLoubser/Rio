/*
 * This configuration is used by the com.sun.jini.start utility to start a
 * ProvisionMonitor, including an embedded Webster and
 * a Jini Lookup Service
 */

import org.rioproject.config.Component

import org.rioproject.boot.ServiceDescriptorUtil;
import com.sun.jini.start.ServiceDescriptor
import org.rioproject.config.maven2.Repository;

@Component('com.sun.jini.start')
class StartMonitorConfig {

    String[] getMonitorConfigArgs(String rioHome) {
        def configArgs = [rioHome+'/config/monitor.groovy']
        return configArgs as String[]
    }

    String[] getLookupConfigArgs(String rioHome) {
        def configArgs = [rioHome+'/config/reggie.groovy']
        return configArgs as String[]
    }

    ServiceDescriptor[] getServiceDescriptors() {
        String m2Repo = Repository.getLocalRepository().absolutePath
        String rioHome = System.getProperty('RIO_HOME')

        def websterRoots = [rioHome+'/lib-dl', ';',
                            rioHome+'/lib',    ';',
                            rioHome+'/deploy', ';',
                            m2Repo]

        String policyFile = rioHome+'/policy/policy.all'

        def serviceDescriptors = [
            ServiceDescriptorUtil.getWebster(policyFile, '0', (String[])websterRoots),
            ServiceDescriptorUtil.getLookup(policyFile, getLookupConfigArgs(rioHome)),
            ServiceDescriptorUtil.getMonitor(policyFile, getMonitorConfigArgs(rioHome))
        ]

        return (ServiceDescriptor[])serviceDescriptors
    }

}
