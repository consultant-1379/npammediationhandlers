package com.ericsson.oss.mediation.npam;


import com.ericsson.cds.cdi.support.rule.custom.node.ManagedObjectData
import com.ericsson.cds.cdi.support.rule.custom.node.NodeDataProvider

import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.services.security.npam.api.constants.ModelsConstants
import com.ericsson.oss.services.security.npam.api.job.modelentities.JobProperty
import com.ericsson.oss.services.security.npam.api.job.modelentities.JobType
import com.ericsson.oss.services.security.npam.api.job.modelentities.JobResult
import com.ericsson.oss.services.security.npam.api.job.modelentities.JobState
import com.ericsson.oss.services.security.npam.api.job.modelentities.Step

class BaseSetupForTestSpecs extends BaseSpecWithModels implements NodeDataProvider {

    final static String SUBNETWORK_NAME = "Sample"

    def setup() {
        runtimeDps.withTransactionBoundaries()
    }

    def addSubnetWork(final String subnetworkName) {
        runtimeDps.addManagedObject().withFdn("SubNetwork="+subnetworkName)
                .addAttribute("SubNetworkId", subnetworkName)
                .namespace("OSS_TOP")
                .version("3.0.0")
                .build()
    }

    def addNodeTree(final String subnetworkName, final String nodeName, final String syncStatus, final Boolean remoteManagementValue) {
        String managedElementFdn;

        if (subnetworkName != null) {
            managedElementFdn = "SubNetwork="+subnetworkName + ",ManagedElement=" + nodeName;
        } else {
            managedElementFdn = "ManagedElement=" + nodeName;
        }

        // create NetworkElement
        ManagedObject nodeMo = runtimeDps.addManagedObject().withFdn("NetworkElement="+nodeName)
                .addAttribute('networkElementId', "nodeName")
                .addAttribute('neType','RadioNode')
                .addAttribute('ossPrefix', managedElementFdn)
                .addAttribute('ossModelIdentity', '22.Q4-R60A24')
                .namespace("OSS_NE_DEF")
                .version("2.0.0")
                .type("NetworkElement")
                .build()

        // create CmFunction
        runtimeDps.addManagedObject().withFdn("NetworkElement="+nodeName+',CmFunction=1')
                .addAttribute('CmFunctionId', "1")
                .addAttribute('syncStatus',syncStatus)
                .namespace("OSS_NE_CM_DEF")
                .version("1.0.1")
                .type("CmFunction")
                .build()

        // create CmFunction
        runtimeDps.addManagedObject().withFdn("NetworkElement="+nodeName+',SecurityFunction=1')
                .addAttribute('securityFunctionId', "1")
                .namespace("OSS_NE_SEC_DEF")
                .version("1.0.0")
                .type("SecurityFunction")
                .build()

        // create ManagedElement
        ManagedObject managedElementMo = runtimeDps.addManagedObject().withFdn(managedElementFdn)
                .addAttribute('managedElementId', "nodeName")
                .namespace("ComTop")
                .version("10.23.0")
                .type("ManagedElement")
                .build()

        nodeMo.addAssociation("nodeRootRef",managedElementMo)

        // create SystemFunction
        runtimeDps.addManagedObject().withFdn(managedElementFdn + ",SystemFunctions=1")
                .addAttribute('systemFunctionsId', "1")
                .namespace("ComTop")
                .version("10.23.0")
                .type("SystemFunctions")
                .build()

        // create SecM
        runtimeDps.addManagedObject().withFdn(managedElementFdn + ",SystemFunctions=1,SecM=1")
                .addAttribute('secMId', "1")
                .namespace("RcsSecM")
                .version("12.3.2")
                .type("SecM")
                .build()

        // create UserManagement
        runtimeDps.addManagedObject().withFdn(managedElementFdn + ",SystemFunctions=1,SecM=1,UserManagement=1")
                .addAttribute('userManagementId', "1")
                .namespace("RcsSecM")
                .version("12.3.2")
                .type("UserManagement")
                .build()

        // create UserIdentity
        runtimeDps.addManagedObject().withFdn(managedElementFdn + ",SystemFunctions=1,SecM=1,UserManagement=1,UserIdentity=1")
                .addAttribute('userIdentityId', "1")
                .namespace("RcsUser")
                .version("6.2.2")
                .type("UserIdentity")
                .build()

        // create UserIdentity
        runtimeDps.addManagedObject().withFdn(managedElementFdn + ",SystemFunctions=1,SecM=1,UserManagement=1,UserIdentity=1,MaintenanceUserSecurity=1")
                .addAttribute('maintenanceUserSecurityId', "1")
                .addAttribute('remoteManagement', remoteManagementValue)
                .addAttribute('loginDelay', '5')
                .addAttribute('loginDelayPolicy', 'FIXED')
                .addAttribute('noOfFailedLoginAttempts', '2')
                .addAttribute('userLockoutPeriod', '3')
                .namespace("RcsUser")
                .version("6.2.2")
                .type("MaintenanceUserSecurity")
                .build()
    }

    def addNeAccount(final String nodeName, final int neAccountId, final String neAccountStatus) {
        runtimeDps.addManagedObject().withFdn("NetworkElement="+nodeName+',SecurityFunction=1,NetworkElementAccount='+neAccountId)
                .addAttribute('networkElementAccountId', neAccountId + "")
                .addAttribute("currentUsername", 'currentUsernameEncrypted')
                .addAttribute("currentPassword", 'currentPasswordEncrypted')
                .addAttribute("nextUsername", 'nextUsernameEncrypted')
                .addAttribute("nextPassword", 'nextPasswordEncrypted')
                .addAttribute("lastPasswordChange", null)
                .addAttribute("errorDetails", null)
                .addAttribute("updateStatus",neAccountStatus)
                .namespace("OSS_NE_SEC_DEF")
                .version("1.0.0")
                .type("NetworkElementAccount")
                .build()
    }

    def addNpamJobWithNodes(final JobType jobType, final JobState jobState, final List<String> nodes, final List<JobProperty> jobProperties) {

        Map<String,List<String>> neInfo = new HashMap<>(1)
        neInfo.put("neNames",nodes)
        return runtimeDps.addPersistenceObject().type(ModelsConstants.NPAM_JOB).namespace(ModelsConstants.NAMESPACE)
                .version(ModelsConstants.VERSION)
                .addAttribute("jobType",jobType.name())
                .addAttribute("state",jobState.name())
                .addAttribute("executionIndex",0)
                .addAttribute("templateJobId",0L)
                .addAttribute("numberOfNetworkElements",nodes.size())
                .addAttribute("scheduledTime",new Date())
                .addAttribute("selectedNEs",neInfo)
                .addAttribute("jobProperties",jobProperties)
                .addAttribute("progressPercentage",0.0d)
                .build()
    }

    def addNpamNeJob(final long mainJobId, final JobState jobState, final JobResult jobResult, final String neName) {
        String jobResultString = null
        if (jobResult != null) {
            jobResultString = jobResult.name()
        }
        return runtimeDps.addPersistenceObject().type(ModelsConstants.NPAM_NEJOB).namespace(ModelsConstants.NAMESPACE)
                .version(ModelsConstants.VERSION)
                .addAttribute("state",jobState.name())
                .addAttribute("mainJobId",mainJobId)
                .addAttribute("neName",neName)
                .addAttribute("result",jobResultString)
                .addAttribute("step", Step.NONE.name())
                .build()
    }



    @Override
    Map<String, Object> getAttributesForMo(final String moFdn) {
        def map = null
        map == null ? [:] : map
    }

    @Override
    List<ManagedObjectData> getAdditionalNodeManagedObjects() {
        []  //we use setupDbNodes to create new objects
    }
}
