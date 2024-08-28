/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2023
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.mediation.npam.handlers

import com.ericsson.cds.cdi.support.rule.MockedImplementation
import com.ericsson.oss.itpf.common.config.Configuration
import com.ericsson.oss.itpf.common.event.ComponentEvent
import com.ericsson.oss.itpf.common.event.ControlEvent
import com.ericsson.oss.itpf.common.event.handler.EventHandlerContext
import com.ericsson.oss.itpf.common.event.handler.EventSubscriber
import com.ericsson.oss.itpf.sdk.eventbus.Channel
import com.ericsson.oss.itpf.sdk.eventbus.Event
import com.ericsson.oss.mediation.adapter.netconf.jca.xa.com.provider.operation.ComNetconfOperationRequest
import com.ericsson.oss.mediation.adapter.netconf.jca.xa.com.provider.util.RpcBuilderHelper
import com.ericsson.oss.mediation.cba.netconf.exception.RpcBuilderException
import com.ericsson.oss.mediation.npam.BaseSetupForTestSpecs
import com.ericsson.oss.mediation.npam.testutil.DpsQueryUtil
import com.ericsson.oss.mediation.npam.util.DpsHelper
import com.ericsson.oss.mediation.util.netconf.api.Datastore
import com.ericsson.oss.mediation.util.netconf.api.NetconfConnectionStatus
import com.ericsson.oss.mediation.util.netconf.api.NetconfManager
import com.ericsson.oss.mediation.util.netconf.api.NetconfResponse
import com.ericsson.oss.mediation.util.netconf.api.exception.NetconfManagerException
import com.ericsson.oss.services.security.npam.api.job.modelentities.JobState
import com.ericsson.oss.services.security.npam.api.neaccount.modelentities.NetworkElementAccountUpdateStatus
import spock.lang.Unroll
import javax.inject.Inject

class NodePamUpdateMaintenanceUserHandlerSpec extends BaseSetupForTestSpecs {
    private static final String NETCONF_NOT_AVAILABLE = "Mediation error: the Netconf Manager is not available";

    @Inject
    NodePamUpdateMaintenanceUserHandler objUnderTest

    @MockedImplementation
    private NetconfManager netconfManager

    @Inject
    private Channel mockedNodePamTopic

    @Inject
    DpsQueryUtil dpsQueryUtil

    @Inject
    DpsHelper dpsHelper

    @MockedImplementation
    private RpcHelperGetter myRpcHelperMock

    def mockedEventToSend = Mock(Event)
    def rpcBuilderHelperMock = Mock(RpcBuilderHelper.class)

    def requestId = 'nodepam:b1089441_from_handler'
    def NULL_VALUE = null

    def setup() {
        addSubnetWork(SUBNETWORK_NAME)
        addNodeTree(SUBNETWORK_NAME, "RadioNode1", "UNSYNCHRONIZED", false)
        addNeAccount("RadioNode1", 1, NetworkElementAccountUpdateStatus.ONGOING.name())
        myRpcHelperMock.getRpcBuilderHelper() >> rpcBuilderHelperMock
        addNpamNeJob(1L, JobState.RUNNING, null, "RadioNode1")
        }

    def init() {
        netconfManager.getStatus() >> NetconfConnectionStatus.CONNECTED
        rpcBuilderHelperMock.buildRpcMessage(_ as ComNetconfOperationRequest) >> "myRpcBody"
    }

    def createMockedEvent() {
        def mockedEvent0 = Mock(ComponentEvent.class)
        final Map<String, Object> eventsMap = new HashMap<>();
        eventsMap.put("nodePamRequestId", "nodepam:b1089441")
        eventsMap.put("nodePamFdn", "ManagedElement=RadioNode1,SystemFunctions=1,SecM=1,UserManagement=1,UserIdentity=1,MaintenanceUser=1")
        eventsMap.put("nodePamNameSpace", "RcsUser")
        eventsMap.put("nodePamNameSpaceVersion", "6.2.2")
        eventsMap.put("nodePamUsername", "nextUsername")
        eventsMap.put("nodePamPassword", "nextPassword")
        eventsMap.put("nodePamSubjectName", "nPamSubjName")
        eventsMap.put("nodePamMoType", "MaintenanceUser")
        eventsMap.put("nodePamRemoteManagement", null)
        eventsMap.put("nodePamRestrictMaintenanceUser", null)
        eventsMap.put("nodeAddress", "RadioNode1")
        eventsMap.put("netconfManager", netconfManager)
        mockedEvent0.getHeaders() >> eventsMap
        return mockedEvent0
    }

    /**
     * Other generic wrong error causes
     */
    def 'receive wrong NodePamEventOperation event and end with failure'() {
        given: 'environment set'
        init()
        and: 'an operation event comes'
        ComponentEvent mockedEvent = createMockedEvent()
        mockedEvent.getHeaders().put("nodePamOperation", "CREATION")

        when: 'the event is consumed'
        objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
        0 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
            assert nodePamEndUpdateOperation.requestId == requestId
            assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
            assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.FAILED.name()
            assert nodePamEndUpdateOperation.errorDetails == NULL_VALUE
            return true
        }, requestId) >> mockedEventToSend
        0 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is NOT modified'
        def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
        mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
        mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
        mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
        mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
        mo.allAttributes.get("lastPasswordChange") == null
        mo.allAttributes.get("errorDetails") == null
        mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.ONGOING.name()
    }

    /***
     NodePamEventOperation=CREATE
     ***/

    @Unroll
    def 'receive NodePamEventOperation=CREATE event with wrong nodePamFdn=#nodePamFdn  =>  FAIL'(nodePamFdn) {
        given: 'environment set'
            init()

        and: 'an operation event with wrong nodePamFdn comes'
            ComponentEvent mockedEvent = createMockedEvent()
            mockedEvent.getHeaders().put("nodePamOperation", "CREATE")
            mockedEvent.getHeaders().put("nodePamFdn", nodePamFdn)

        when: 'the event is consumed'
            objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
            1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
                assert nodePamEndUpdateOperation.requestId == requestId
                assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
                assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.FAILED.name()
                assert nodePamEndUpdateOperation.errorDetails == "Mediation error: no information available"
                return true
            }, requestId) >> mockedEventToSend
            1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is modified accordingly'
            def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
            mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
            mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
            mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
            mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
            mo.allAttributes.get("lastPasswordChange") == null
            mo.allAttributes.get("errorDetails") == "Mediation error: no information available"
            mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.FAILED.name()
        where:
        nodePamFdn                                                                                              || _
        "ManagedElement=RadioNode11,SystemFunctions=1,SecM=1,UserManagement=1,UserIdentity=1,MaintenanceUser=1" || _
    }

    def 'receive NodePamEventOperation=CREATE event with invalid nodePamFdn => FAIL'(nodePamFdn) {
        given: 'environment set'
            init()
        and: 'an operation event with null nodePamFdn comes'
            ComponentEvent mockedEvent = createMockedEvent()
            mockedEvent.getHeaders().put("nodePamOperation", "CREATE")
            mockedEvent.getHeaders().put("nodePamFdn", nodePamFdn)

        when: 'the event is consumed'
            objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
            0 * mockedNodePamTopic.createEvent(_, requestId) >> mockedEventToSend
            0 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is NOT modified'
            def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
            mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
            mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
            mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
            mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
            mo.allAttributes.get("lastPasswordChange") == null
            mo.allAttributes.get("errorDetails") == null
            mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.ONGOING.name()
        where:
        nodePamFdn          || _
        null                || _
        ""                  || _
    }

    def 'receive NodePamEventOperation=CREATE event while netconf not connected => FAIL'() {
        netconfManager.getStatus() >> NetconfConnectionStatus.NOT_CONNECTED

        given: 'an operation event comes'
        ComponentEvent mockedEvent = createMockedEvent()
        mockedEvent.getHeaders().put("nodePamOperation", "CREATE")

        when: 'the event is consumed'
        objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
        1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
            assert nodePamEndUpdateOperation.requestId == requestId
            assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
            assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.FAILED.name()
            assert nodePamEndUpdateOperation.errorDetails == "Mediation error: the Netconf Manager is not connected, current status: NOT_CONNECTED"
            return true
        }, requestId) >> mockedEventToSend
        1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is modified accordingly'
        def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
        mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
        mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
        mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
        mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
        mo.allAttributes.get("lastPasswordChange") == null
        mo.allAttributes.get("errorDetails") != null
        mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.FAILED.name()
    }

    def 'receive NodePamEventOperation=CREATE event while netconf null => FAIL'() {
        netconfManager = null

        given: 'an operation event comes'
        ComponentEvent mockedEvent = createMockedEvent()
        mockedEvent.getHeaders().put("nodePamOperation", "CREATE")

        when: 'the event is consumed'
        objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
        1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
            assert nodePamEndUpdateOperation.requestId == requestId
            assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
            assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.FAILED.name()
            assert nodePamEndUpdateOperation.errorDetails == NETCONF_NOT_AVAILABLE
            return true
        }, requestId) >> mockedEventToSend
        1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is modified accordingly'
        def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
        mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
        mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
        mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
        mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
        mo.allAttributes.get("lastPasswordChange") == null
        mo.allAttributes.get("errorDetails") != null
        mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.FAILED.name()
    }

    def 'receive NodePamEventOperation=CREATE event when editConfig throw NetconfManagerException => FAIL'() {
        netconfManager.getStatus() >> NetconfConnectionStatus.CONNECTED
        netconfManager.editConfig(Datastore.RUNNING, _ as String) >> { throw new NetconfManagerException("NetconfManagerException exception")}

        given: 'an operation event comes'
        ComponentEvent mockedEvent = createMockedEvent()
        mockedEvent.getHeaders().put("nodePamOperation", "CREATE")

        when: 'the event is consumed'
        objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
        1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
            assert nodePamEndUpdateOperation.requestId == requestId
            assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
            assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.FAILED.name()
            assert nodePamEndUpdateOperation.errorDetails == "Mediation error: NetconfManagerException exception"
            return true
        }, requestId) >> mockedEventToSend
        1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is modified accordingly'
        def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
        mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
        mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
        mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
        mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
        mo.allAttributes.get("lastPasswordChange") == null
        mo.allAttributes.get("errorDetails") != null
        mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.FAILED.name()
    }

    def 'receive NodePamEventOperation=CREATE event when editConfig throw NetconfExecutionException => FAIL'() {
        given: 'environment set'
        init()
        and:
        def netconfresponse = new NetconfResponse();
        netconfresponse.setError(true)
        netconfManager.editConfig(Datastore.RUNNING, _ as String) >> netconfresponse
        and: 'an operation event comes'
        ComponentEvent mockedEvent = createMockedEvent()
        mockedEvent.getHeaders().put("nodePamOperation", "CREATE")

        when: 'the event is consumed'
        objUnderTest.onEvent(mockedEvent)
        then: 'nodePamEndUpdateOperation sent to topic'
        1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
            assert nodePamEndUpdateOperation.requestId == requestId
            assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
            assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.FAILED.name()
            assert nodePamEndUpdateOperation.errorDetails == "Mediation error: " + netconfresponse.toString()
            return true
        }, requestId) >> mockedEventToSend
        1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is modified accordingly'
        def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
        mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
        mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
        mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
        mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
        mo.allAttributes.get("lastPasswordChange") == null
        mo.allAttributes.get("errorDetails") != null
        mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.FAILED.name()
    }

    def 'receive NodePamEventOperation=CREATE event with valid parameters => SUCCESS'() {
        given: 'environment set'
            init()

        and: 'a remoteManagement operation CREATE event comes'
            ComponentEvent mockedEvent = createMockedEvent()
            mockedEvent.getHeaders().put("nodePamOperation", "CREATE")

        when: 'the event is consumed'
            objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
        1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
            assert nodePamEndUpdateOperation.requestId == requestId
            assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
            assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.CONFIGURED.name()
            assert nodePamEndUpdateOperation.errorDetails == NULL_VALUE
            return true
        }, requestId) >> mockedEventToSend
        1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is modified'
        def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
        mo.allAttributes.get("currentUsername") == "nextUsernameEncrypted"
        mo.allAttributes.get("currentPassword") == "nextPasswordEncrypted"
        mo.allAttributes.get("nextUsername") == null
        mo.allAttributes.get("nextPassword") == null
        mo.allAttributes.get("lastPasswordChange") != null
        mo.allAttributes.get("errorDetails") == null
        mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.CONFIGURED.name()
    }

    def 'receive NodePamEventOperation=CREATE event with NEjob => SUCCESS'() {
        given: 'environment set'
            init()
        and: 'an operation CREATE event comes'
            ComponentEvent mockedEvent = createMockedEvent()
            mockedEvent.getHeaders().put("nodePamOperation", "CREATE")
            def long neJobId = 12L
            mockedEvent.getHeaders().put("neJobId", neJobId)

        when: 'the event is consumed'
            objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
        1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
            assert nodePamEndUpdateOperation.requestId == requestId
            assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
            assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.CONFIGURED.name()
            assert nodePamEndUpdateOperation.errorDetails == NULL_VALUE
            return true
        }, requestId) >> mockedEventToSend
        1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is modified'
        def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
        mo.allAttributes.get("currentUsername") == "nextUsernameEncrypted"
        mo.allAttributes.get("currentPassword") == "nextPasswordEncrypted"
        mo.allAttributes.get("nextUsername") == null
        mo.allAttributes.get("nextPassword") == null
        mo.allAttributes.get("lastPasswordChange") != null
        mo.allAttributes.get("errorDetails") == null
        mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.CONFIGURED.name()
        and: 'NEJob PO is modified'
        def po = dpsHelper.getLiveBucket().findPoById(neJobId)
        po.allAttributes.get("result") == "SUCCESS"
        po.allAttributes.get("state") == "COMPLETED"
    }

    def 'receive NodePamEventOperation=CREATE event with PO not existing without update NEjob => SUCCESS'() {
        given: 'environment set'
           init()
        and: 'an operation CREATE event comes'
            ComponentEvent mockedEvent = createMockedEvent()
            mockedEvent.getHeaders().put("nodePamOperation", "CREATE")
            def long neJobId = 15L
            mockedEvent.getHeaders().put("neJobId", neJobId)

        when: 'the event is consumed'
            objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
        1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
            assert nodePamEndUpdateOperation.requestId == requestId
            assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
            assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.CONFIGURED.name()
            assert nodePamEndUpdateOperation.errorDetails == NULL_VALUE
            return true
        }, requestId) >> mockedEventToSend
        1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is modified'
        def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
        mo.allAttributes.get("currentUsername") == "nextUsernameEncrypted"
        mo.allAttributes.get("currentPassword") == "nextPasswordEncrypted"
        mo.allAttributes.get("nextUsername") == null
        mo.allAttributes.get("nextPassword") == null
        mo.allAttributes.get("lastPasswordChange") != null
        mo.allAttributes.get("errorDetails") == null
        mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.CONFIGURED.name()
        and: 'NEJob PO is modified'
        def po = dpsHelper.getLiveBucket().findPoById(neJobId)
        po  == null
    }

    /***
     NodePamEventOperation=MODIFY
     ***/
    def 'receive NodePamEventOperation=MODIFY event with valid parameters => SUCCESS'() {
        given: 'environment set'
            init()
        and: 'an operation event comes'
            ComponentEvent mockedEvent = createMockedEvent()
            mockedEvent.getHeaders().put("nodePamOperation", "MODIFY")

        when: 'the event is consumed'
          objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
            1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
                assert nodePamEndUpdateOperation.requestId == requestId
                assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
                assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.CONFIGURED.name()
                assert nodePamEndUpdateOperation.errorDetails == NULL_VALUE
                return true
            }, requestId) >> mockedEventToSend
        1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is modified accordingly'
            def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
            mo.allAttributes.get("currentUsername") == "nextUsernameEncrypted"
            mo.allAttributes.get("currentPassword") == "nextPasswordEncrypted"
            mo.allAttributes.get("nextUsername") == null
            mo.allAttributes.get("nextPassword") == null
            mo.allAttributes.get("lastPasswordChange") != null
            mo.allAttributes.get("errorDetails") == null
            mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.CONFIGURED.name()
    }

    /***
     NodePamEventOperation=DELETE
     ***/

    def 'receive NodePamEventOperation=DELETE event when buildRpcMessage throw exception => FAIL'() {
        given:
            netconfManager.getStatus() >> NetconfConnectionStatus.CONNECTED
            rpcBuilderHelperMock.buildRpcMessage(_ as ComNetconfOperationRequest) >> { throw new RpcBuilderException("RpcBuilderException exception")}
        and: 'an operation event comes'
            ComponentEvent mockedEvent = createMockedEvent()
            mockedEvent.getHeaders().put("nodePamOperation", "DELETE")

        when: 'the event is consumed'
            objUnderTest.onEvent(mockedEvent)
        then: 'nodePamEndUpdateOperation sent to topic'
        1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
            assert nodePamEndUpdateOperation.requestId == requestId
            assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
            assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.FAILED.name()
            assert nodePamEndUpdateOperation.errorDetails == "Mediation error: RpcBuilderException exception"
            return true
        }, requestId) >> mockedEventToSend
        1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is NOT modified'
        def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
        mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
        mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
        mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
        mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
        mo.allAttributes.get("lastPasswordChange") == null
        mo.allAttributes.get("errorDetails") == null
        mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.ONGOING.name()
    }

    def 'receive NodePamEventOperation=DELETE event with valid parameters => SUCCESS'() {
        given: 'environment set'
            init()
        and: 'an operation event comes'
            ComponentEvent mockedEvent = createMockedEvent()
            mockedEvent.getHeaders().put("nodePamOperation", "DELETE")

        when: 'the event is consumed'
          objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
            1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
                assert nodePamEndUpdateOperation.requestId == requestId
                assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
                assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.CONFIGURED.name()
                assert nodePamEndUpdateOperation.errorDetails == NULL_VALUE
                return true
            }, requestId) >> mockedEventToSend
            1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is NOT modified'
            def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
            mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
            mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
            mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
            mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
            mo.allAttributes.get("lastPasswordChange") == null
            mo.allAttributes.get("errorDetails") == null
            mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.ONGOING.name()
    }

    /***
     NodePamEventOperation=MODIFY_REMOTE_MANAGEMENT
    ***/

    def 'receive NodePamEventOperation=MODIFY_REMOTE_MANAGEMENT event with remoteManagement=null and restrictMaintenanceUser=null => SUCCESS'() {
        given: 'environment set'
            init()
        and: 'an operation event with wrong nodePamFdn comes'
            ComponentEvent mockedEvent = createMockedEvent()
            mockedEvent.getHeaders().put("nodePamOperation", "MODIFY_REMOTE_MANAGEMENT")
            mockedEvent.getHeaders().put("nodePamRemoteManagement", null)
            mockedEvent.getHeaders().put("nodePamRestrictMaintenanceUser", null)

        when: 'the event is consumed'
            objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
        1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
            assert nodePamEndUpdateOperation.requestId == requestId
            assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
            assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.CONFIGURED.name()
            assert nodePamEndUpdateOperation.errorDetails == NULL_VALUE
            return true
        }, requestId) >> mockedEventToSend
        1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is NOT modified'
            def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
            mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
            mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
            mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
            mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
            mo.allAttributes.get("lastPasswordChange") == null
            mo.allAttributes.get("errorDetails") == null
            mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.ONGOING.name()
    }

    def 'receive NodePamEventOperation=MODIFY_REMOTE_MANAGEMENT with invalid nodePamFdn => FAIL'() {
        netconfManager.getStatus() >> NetconfConnectionStatus.CONNECTED

        given: 'an operation event with wrong nodePamFdn comes'
            ComponentEvent mockedEvent = createMockedEvent()
            mockedEvent.getHeaders().put("nodePamOperation", "MODIFY_REMOTE_MANAGEMENT")
            mockedEvent.getHeaders().put("nodePamFdn", "ManagedElement=RadioNode11,SystemFunctions=1,SecM=1,UserManagement=1,UserIdentity=1,MaintenanceUser=1")

        when: 'the event is consumed'
            objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
            1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
                assert nodePamEndUpdateOperation.requestId == requestId
                assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
                assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.FAILED.name()
                assert nodePamEndUpdateOperation.errorDetails == "Mediation error: no information available"
                return true
            }, requestId) >> mockedEventToSend
            1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is NOT modified'
            def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
            mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
            mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
            mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
            mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
            mo.allAttributes.get("lastPasswordChange") == null
            mo.allAttributes.get("errorDetails") == null
            mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.ONGOING.name()
    }

    def 'receive NodePamEventOperation=MODIFY_REMOTE_MANAGEMENT event when editConfig throw NetconfManagerException => FAIL'() {
        netconfManager.getStatus() >> NetconfConnectionStatus.CONNECTED

        given: 'an operation event with wrong nodePamFdn comes'
            ComponentEvent mockedEvent = createMockedEvent()
            mockedEvent.getHeaders().put("nodePamOperation", "MODIFY_REMOTE_MANAGEMENT")
        and:
            netconfManager.editConfig(_ as Datastore, _ as String) >> { throw new NetconfManagerException("NetconfManagerException exception") }

        when: 'the event is consumed'
            objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
            1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
                assert nodePamEndUpdateOperation.requestId == requestId
                assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
                assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.FAILED.name()
                assert nodePamEndUpdateOperation.errorDetails == "Mediation error: NetconfManagerException exception"
                return true
            }, requestId) >> mockedEventToSend
            1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is NOT modified'
            def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
            mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
            mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
            mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
            mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
            mo.allAttributes.get("lastPasswordChange") == null
            mo.allAttributes.get("errorDetails") == null
            mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.ONGOING.name()
    }

    def 'receive NodePamEventOperation=MODIFY_REMOTE_MANAGEMENT event when editConfig throw NetconfExecutionException => FAIL'() {
        given: 'environment set'
            init()
        and:
            def netconfresponse = new NetconfResponse();
            netconfresponse.setError(true)
            netconfManager.editConfig(Datastore.RUNNING, _ as String) >> netconfresponse

        and: 'an operation event comes'
            ComponentEvent mockedEvent = createMockedEvent()
            mockedEvent.getHeaders().put("nodePamOperation", "MODIFY_REMOTE_MANAGEMENT")

        when: 'the event is consumed'
            objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
            1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
                assert nodePamEndUpdateOperation.requestId == requestId
                assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
                assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.FAILED.name()
                assert nodePamEndUpdateOperation.errorDetails == "Mediation error: " + netconfresponse.toString()
                return true
            }, requestId) >> mockedEventToSend
            1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is NOT modified'
            def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
            mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
            mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
            mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
            mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
            mo.allAttributes.get("lastPasswordChange") == null
            mo.allAttributes.get("errorDetails") == null
            mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.ONGOING.name()
    }


    def 'receive NodePamEventOperation=MODIFY_REMOTE_MANAGEMENT event while netconf not connected => FAIL'() {
        netconfManager.getStatus() >> NetconfConnectionStatus.NOT_CONNECTED

        given: 'an operation event comes'
            ComponentEvent mockedEvent = createMockedEvent()
            mockedEvent.getHeaders().put("nodePamOperation", "MODIFY_REMOTE_MANAGEMENT")

        when: 'the event is consumed'
            objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
            1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
                assert nodePamEndUpdateOperation.requestId == requestId
                assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
                assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.FAILED.name()
                assert nodePamEndUpdateOperation.errorDetails == "Mediation error: the Netconf Manager is not connected, current status: NOT_CONNECTED"
                return true
            }, requestId) >> mockedEventToSend
            1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is NOT modified'
            def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
            mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
            mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
            mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
            mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
            mo.allAttributes.get("lastPasswordChange") == null
            mo.allAttributes.get("errorDetails") == null
            mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.ONGOING.name()
    }

    @Unroll
    def 'receive NodePamEventOperation=MODIFY_REMOTE_MANAGEMENT event when editConfig returns errored netconfresponse with expected errorMessage => SUCCESS'(remoteManagement, restrictMaintenanceUser) {
        given: 'environment set'
            init()
        and: 'errored netconfresponse with expected errorMessage'
            def netconfresponse = new NetconfResponse();
            netconfresponse.setError(true)
            netconfresponse.setErrorMessage("Forced logout")
            netconfManager.editConfig(Datastore.RUNNING, _ as String) >> netconfresponse
        and: 'an operation event comes'
            ComponentEvent mockedEvent = createMockedEvent()
            mockedEvent.getHeaders().put("nodePamOperation", "MODIFY_REMOTE_MANAGEMENT")
            mockedEvent.getHeaders().put("nodePamRemoteManagement", remoteManagement)
            mockedEvent.getHeaders().put("nodePamRestrictMaintenanceUser", restrictMaintenanceUser)

        when: 'the event is consumed'
            objUnderTest.onEvent(mockedEvent)
        then: 'nodePamEndUpdateOperation sent to topic'
        1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
            assert nodePamEndUpdateOperation.requestId == requestId
            assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
            assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.CONFIGURED.name()
            assert nodePamEndUpdateOperation.errorDetails == NULL_VALUE
            return true
        }, requestId) >> mockedEventToSend
        1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is NOT modified'
            def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
            mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
            mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
            mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
            mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
            mo.allAttributes.get("lastPasswordChange") == null
            mo.allAttributes.get("errorDetails") == null
            mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.ONGOING.name()
        where:
            remoteManagement | restrictMaintenanceUser || _
            Boolean.TRUE     |   Boolean.TRUE          || _
            Boolean.TRUE     |   Boolean.FALSE         || _
            Boolean.FALSE    |   Boolean.TRUE          || _
            Boolean.FALSE    |   Boolean.FALSE         || _
    }

    @Unroll
    def 'receive NodePamEventOperation=MODIFY_REMOTE_MANAGEMENT event with remoteManagement=#remoteManagement, restrictMaintenanceUser=#restrictMaintenanceUser => SUCCESS'(remoteManagement, restrictMaintenanceUser) {
        given: 'environment set'
            init()
        and: 'an operation event comes'
            ComponentEvent mockedEvent = createMockedEvent()
            mockedEvent.getHeaders().put("nodePamOperation", "MODIFY_REMOTE_MANAGEMENT")
            mockedEvent.getHeaders().put("nodePamRemoteManagement", remoteManagement)
            mockedEvent.getHeaders().put("nodePamRestrictMaintenanceUser", restrictMaintenanceUser)

        when: 'the event is consumed'
            objUnderTest.onEvent(mockedEvent)
        then: 'nodePamEndUpdateOperation sent to topic'
        1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
            assert nodePamEndUpdateOperation.requestId == requestId
            assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
            assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.CONFIGURED.name()
            assert nodePamEndUpdateOperation.errorDetails == NULL_VALUE
            return true
        }, requestId) >> mockedEventToSend
        1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is NOT modified'
            def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
            mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
            mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
            mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
            mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
            mo.allAttributes.get("lastPasswordChange") == null
            mo.allAttributes.get("errorDetails") == null
            mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.ONGOING.name()
        where:
            remoteManagement | restrictMaintenanceUser || _
            Boolean.TRUE     |   Boolean.TRUE          || _
            Boolean.TRUE     |   Boolean.FALSE         || _
            Boolean.FALSE    |   Boolean.TRUE          || _
            Boolean.FALSE    |   Boolean.FALSE         || _
    }

    /***
     NodePamEventOperation=MODIFY_REMOTE_MANAGEMENT with neJobId
     ***/

    def 'receive NodePamEventOperation=MODIFY_REMOTE_MANAGEMENT event with NEjobId and invalid nodePamFdn => FAIL'() {
        given:
            netconfManager.getStatus() >> NetconfConnectionStatus.CONNECTED

        and: 'an operation event with wrong nodePamFdn comes'
            ComponentEvent mockedEvent = createMockedEvent()
            mockedEvent.getHeaders().put("nodePamOperation", "MODIFY_REMOTE_MANAGEMENT")
            mockedEvent.getHeaders().put("nodePamFdn", "ManagedElement=RadioNode11,SystemFunctions=1,SecM=1,UserManagement=1,UserIdentity=1,MaintenanceUser=1")
            def long neJobId = 12L
            mockedEvent.getHeaders().put("neJobId", neJobId)

        when: 'the event is consumed'
            objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
        1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
            assert nodePamEndUpdateOperation.requestId == requestId
            assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
            assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.FAILED.name()
            assert nodePamEndUpdateOperation.errorDetails == "Mediation error: no information available"
            return true
        }, requestId) >> mockedEventToSend
        1 * mockedNodePamTopic.send(mockedEventToSend, _)
        def po = dpsHelper.getLiveBucket().findPoById(neJobId)
        and: 'NetworkElementAccount MO is not modified'
            def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
            mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
            mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
            mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
            mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
            mo.allAttributes.get("lastPasswordChange") == null
            mo.allAttributes.get("errorDetails") == null
            mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.ONGOING.name()
        and: 'NEJob PO is modified'
            po.allAttributes.get("result") == "FAILED"
            po.allAttributes.get("state") == "COMPLETED"
    }

    def 'receive NodePamEventOperation MODIFY_REMOTE_MANAGEMENT event with NEjobId when editConfig throw NetconfManagerException => FAIL'() {
        given:
            netconfManager.getStatus() >> NetconfConnectionStatus.CONNECTED
        and: 'an operation event with wrong nodePamFdn comes'
            ComponentEvent mockedEvent = createMockedEvent()
            mockedEvent.getHeaders().put("nodePamOperation", "MODIFY_REMOTE_MANAGEMENT")
        and:
            netconfManager.editConfig(_ as Datastore, _ as String) >> { throw new NetconfManagerException("NetconfManagerException exception") }
            def long neJobId = 12L
            mockedEvent.getHeaders().put("neJobId", neJobId)

        when: 'the event is consumed'
            objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
        1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
            assert nodePamEndUpdateOperation.requestId == requestId
            assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
            assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.FAILED.name()
            assert nodePamEndUpdateOperation.errorDetails == "Mediation error: NetconfManagerException exception"
            return true
        }, requestId) >> mockedEventToSend
        1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is not modified'
            def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
            mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
            mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
            mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
            mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
            mo.allAttributes.get("lastPasswordChange") == null
            mo.allAttributes.get("errorDetails") == null
            mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.ONGOING.name()
        and: 'NEJob PO is modified'
            def po = dpsHelper.getLiveBucket().findPoById(neJobId)
            po.allAttributes.get("result") == "FAILED"
            po.allAttributes.get("state") == "COMPLETED"
    }

    def 'receive NodePamEventOperation MODIFY_REMOTE_MANAGEMENT event with NEjobId when editConfig throw NetconfExecutionException => FAIL'() {
        given: 'environment set'
            init()
        and:
            def netconfresponse = new NetconfResponse();
            netconfresponse.setError(true)
            netconfManager.editConfig(Datastore.RUNNING, _ as String) >> netconfresponse
        and: 'an operation event comes'
            ComponentEvent mockedEvent = createMockedEvent()
            mockedEvent.getHeaders().put("nodePamOperation", "MODIFY_REMOTE_MANAGEMENT")
            def long neJobId = 12L
            mockedEvent.getHeaders().put("neJobId", neJobId)

        when: 'the event is consumed'
            objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
        1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
            assert nodePamEndUpdateOperation.requestId == requestId
            assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
            assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.FAILED.name()
            assert nodePamEndUpdateOperation.errorDetails == "Mediation error: " + netconfresponse.toString()
            return true
        }, requestId) >> mockedEventToSend
        1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is not modified'
            def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
            mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
            mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
            mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
            mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
            mo.allAttributes.get("lastPasswordChange") == null
            mo.allAttributes.get("errorDetails") == null
            mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.ONGOING.name()
        and: 'NEJob PO is modified'
            def po = dpsHelper.getLiveBucket().findPoById(neJobId)
            po.allAttributes.get("result") == "FAILED"
            po.allAttributes.get("state") == "COMPLETED"
    }

    @Unroll
    def 'receive NodePamEventOperation=MODIFY_REMOTE_MANAGEMENT event with NEjob when editConfig returns errored netconfresponse with expected errorMessage => SUCCESS'(remoteManagement, restrictMaintenanceUser) {
        given: 'environment set'
            init()
        and: 'errored netconfresponse with expected errorMessage'
            def netconfresponse = new NetconfResponse();
            netconfresponse.setError(true)
            netconfresponse.setErrorMessage("Forced logout")
            netconfManager.editConfig(Datastore.RUNNING, _ as String) >> netconfresponse
        and: 'an operation MODIFY_REMOTE_MANAGEMENT event comes'
            ComponentEvent mockedEvent = createMockedEvent()
            mockedEvent.getHeaders().put("nodePamOperation", "MODIFY_REMOTE_MANAGEMENT")
            mockedEvent.getHeaders().put("nodePamRemoteManagement", remoteManagement)
            mockedEvent.getHeaders().put("nodePamRestrictMaintenanceUser", restrictMaintenanceUser)
            def long neJobId = 12L
            mockedEvent.getHeaders().put("neJobId", neJobId)

        when: 'the event is consumed'
            objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
        1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
            assert nodePamEndUpdateOperation.requestId == requestId
            assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
            assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.CONFIGURED.name()
            assert nodePamEndUpdateOperation.errorDetails == NULL_VALUE
            return true
        }, requestId) >> mockedEventToSend
        1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is not modified'
            def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
            mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
            mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
            mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
            mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
            mo.allAttributes.get("lastPasswordChange") == null
            mo.allAttributes.get("errorDetails") == null
            mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.ONGOING.name()
        and: 'NEJob PO is modified'
            def po = dpsHelper.getLiveBucket().findPoById(neJobId)
            po.allAttributes.get("result") == null
            po.allAttributes.get("state") == "RUNNING"
            po.allAttributes.get("step")  == "WFE"

        where:
            remoteManagement | restrictMaintenanceUser || _
            Boolean.TRUE     |   Boolean.TRUE          || _
            Boolean.TRUE     |   Boolean.FALSE         || _
            Boolean.FALSE    |   Boolean.TRUE          || _
            Boolean.FALSE    |   Boolean.FALSE         || _
    }

    @Unroll
    def 'receive NodePamEventOperation=MODIFY_REMOTE_MANAGEMENT event with NEjob and with remoteManagement=#remoteManagement, restrictMaintenanceUser=#restrictMaintenanceUser => SUCCESS'(remoteManagement, restrictMaintenanceUser) {
        given: 'environment set'
            init()
        and: 'an operation MODIFY_REMOTE_MANAGEMENT event comes'
            ComponentEvent mockedEvent = createMockedEvent()
            mockedEvent.getHeaders().put("nodePamOperation", "MODIFY_REMOTE_MANAGEMENT")
            mockedEvent.getHeaders().put("nodePamRemoteManagement", remoteManagement)
            mockedEvent.getHeaders().put("nodePamRestrictMaintenanceUser", restrictMaintenanceUser)
            def long neJobId = 12L
            mockedEvent.getHeaders().put("neJobId", neJobId)

        when: 'the event is consumed'
            objUnderTest.onEvent(mockedEvent)

        then: 'nodePamEndUpdateOperation sent to topic'
        1 * mockedNodePamTopic.createEvent({ nodePamEndUpdateOperation ->
            assert nodePamEndUpdateOperation.requestId == requestId
            assert nodePamEndUpdateOperation.nodeName == "RadioNode1"
            assert nodePamEndUpdateOperation.status == NetworkElementAccountUpdateStatus.CONFIGURED.name()
            assert nodePamEndUpdateOperation.errorDetails == NULL_VALUE
            return true
        }, requestId) >> mockedEventToSend
        1 * mockedNodePamTopic.send(mockedEventToSend, _)
        and: 'NetworkElementAccount MO is not modified'
            def mo = dpsQueryUtil.getNetworkElementAccount("RadioNode1", 1)
            mo.allAttributes.get("currentUsername") == "currentUsernameEncrypted"
            mo.allAttributes.get("currentPassword") == "currentPasswordEncrypted"
            mo.allAttributes.get("nextUsername") == "nextUsernameEncrypted"
            mo.allAttributes.get("nextPassword") == "nextPasswordEncrypted"
            mo.allAttributes.get("lastPasswordChange") == null
            mo.allAttributes.get("errorDetails") == null
            mo.allAttributes.get("updateStatus") == NetworkElementAccountUpdateStatus.ONGOING.name()
        and: 'NEJob PO is modified'
            def po = dpsHelper.getLiveBucket().findPoById(neJobId)
            po.allAttributes.get("result") == null
            po.allAttributes.get("state") == "RUNNING"
             po.allAttributes.get("step")  == "WFE"

        where:
            remoteManagement | restrictMaintenanceUser || _
            Boolean.TRUE     |   Boolean.TRUE          || _
            Boolean.TRUE     |   Boolean.FALSE         || _
            Boolean.FALSE    |   Boolean.TRUE          || _
            Boolean.FALSE    |   Boolean.FALSE         || _
    }

    /***
     For coverage purpose
     ***/
    def 'receive NodePamEventOperation event and rpcChunk not null for coverage'() {
        given:
        def List<ComNetconfOperationRequest> netconfOperationRequests = new ArrayList<>()
        def ComNetconfOperationRequest comNetconfOperationRequest
        comNetconfOperationRequest = new ComNetconfOperationRequest.NetconfOperationRequestBuilder().requestBody("requestBody").build()
        netconfOperationRequests.add(comNetconfOperationRequest)
        when:
        def result = objUnderTest.getRpcBody(netconfOperationRequests)
        then:
        result == "requestBody"
    }

    def 'call init for coverage'() {
        given:
            def property = "property"
            def Map<String, Object> map = new HashMap<>()
            map.put("prop", property)

        def config = new Configuration() {
            @Override
            Integer getIntProperty(String s) { return null }
            @Override
            String getStringProperty(String s) { return null }
            @Override
            Boolean getBooleanProperty(String s) { return null }
            @Override
            Map<String, Object> getAllProperties() { return map }
        }
        def context = new EventHandlerContext() {
            @Override
            Configuration getEventHandlerConfiguration() { return config }
            @Override
            Collection<EventSubscriber> getEventSubscribers() { return null }
            @Override
            void sendControlEvent(ControlEvent controlEvent) { }
            @Override
            Object getContextualData(String s) { return null }
        }
        when:
            objUnderTest.init(context)

        then:
            true
    }

    def 'call createMaintenanceUserAttributesMap for coverage'(subjectName) {
        given:
        def String maintenanceUserId = "someMaintenanceUserId"
        def String username = "someUsername"
        def String password = "somePassword"

        when:
        objUnderTest.createMaintenanceUserAttributesMap(
                maintenanceUserId, subjectName, username, password)

        then:
        true

        where:
        subjectName         || _
        "someSubjectNAme"   || _
        null                || _
    }

    def 'call endRemoteManagementOperationWithFailure for coverage'(nodeName) {
        given:
        def String requestId = "someRequestId"
        def Long neJobId = 1
        def Exception e = null

        when:
        objUnderTest.endRemoteManagementOperationWithFailure(requestId, nodeName, neJobId, e)

        then:
        true

        where:
        nodeName         || _
        "someNodeName"   || _
        null             || _
    }

}
