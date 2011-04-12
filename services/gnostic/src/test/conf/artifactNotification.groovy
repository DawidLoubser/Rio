import org.rioproject.config.Constants

import org.rioproject.resources.servicecore.Service
import java.util.logging.Level
import org.rioproject.system.SystemWatchID

deployment(name: 'Notification Using Artifact Test') {
    groups System.getProperty(Constants.GROUPS_PROPERTY_NAME,
                              System.getProperty('user.name'))

    artifact id: 'service', 'org.rioproject:gnostic:4.2'
    artifact id: 'service-dl', 'org.rioproject:gnostic:dl:4.2'
    artifact id: 'test', 'org.rioproject.gnostic:test:1.0'

    logging {
        logger 'org.rioproject.gnostic', Level.FINE
    }
    
    service(name: 'Test') {
        interfaces {
            classes 'org.rioproject.gnostic.test.TestService'
            artifact ref: 'test'
        }

        implementation(class: 'org.rioproject.gnostic.test.TestServiceImpl') {
            artifact ref: 'test'
        }

        comment: 'Gnostic Test Service'

        maintain 1
    }

    rules {
        rule{
            resource 'file:src/test/resources/CounterNotification'
            ruleClassPath 'org.rioproject.gnostic:test:1.0'
            serviceFeed(name: "Test") {
                watches "notification"
            }
        }
    }

}
