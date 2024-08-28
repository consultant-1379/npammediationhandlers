/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2017
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.mediation.npam.util;

import com.ericsson.oss.itpf.datalayer.dps.BucketProperties;
import com.ericsson.oss.itpf.datalayer.dps.DataBucket;
import com.ericsson.oss.itpf.datalayer.dps.DataPersistenceService;
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject;
import com.ericsson.oss.itpf.datalayer.dps.persistence.PersistenceObject;
import com.ericsson.oss.itpf.sdk.core.annotation.EServiceRef;
import com.ericsson.oss.itpf.sdk.core.classic.ServiceFinderBean;
import com.ericsson.oss.services.security.npam.api.constants.NodePamConstants;
import com.ericsson.oss.services.security.npam.api.job.modelentities.JobResult;
import com.ericsson.oss.services.security.npam.api.job.modelentities.JobState;
import com.ericsson.oss.services.security.npam.api.job.modelentities.NPamNEJob;
import com.ericsson.oss.services.security.npam.api.job.modelentities.Step;
import com.ericsson.oss.services.security.npam.api.neaccount.modelentities.NetworkElementAccount;
import com.ericsson.oss.services.security.npam.api.neaccount.modelentities.NetworkElementAccountUpdateStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * This class manages the DPS access.
 */
@Stateless
public class DpsHelper {
    private static final Logger logger = LoggerFactory.getLogger(DpsHelper.class);

    @EServiceRef
    private DataPersistenceService dataPersistenceService;

    public DataPersistenceService getDataPersistenceService() {
        if (dataPersistenceService == null) {
            dataPersistenceService = new ServiceFinderBean().find(DataPersistenceService.class);
        }
        return dataPersistenceService;
    }

    protected DataBucket getLiveBucket() {
        return getDataPersistenceService().getDataBucket(NodePamConstants.CONFIGURATION_LIVE, BucketProperties.SUPPRESS_MEDIATION,
                BucketProperties.SUPPRESS_CONSTRAINTS);
    }

    public ManagedObject getManagedObjectInstance(final String fdn) {
        return getLiveBucket().findMoByFdn(fdn);
    }

    public void updateNetworkElementAccountWithSuccess(final String nodeName,
                                             final String networkElementAccountId
                                             ) {
        final DataBucket dataBucket = getLiveBucket();
        final String networkElementAccountFdn = NodePamConstants.NETWORK_ELEMENT_MO + "=" + nodeName + "," + NodePamConstants.SECURITY_FUNCTION_MO + "=1," + NodePamConstants.NETWORK_ELEMENT_ACCOUNT_MO + "=" + networkElementAccountId;
        ManagedObject networkElementAccountMo = dataBucket.findMoByFdn(networkElementAccountFdn);
        logger.debug("updateNetworkElementAccountWithSuccess:: for networkElementAccountMo={}", networkElementAccountMo);

        //update values
        networkElementAccountMo.setAttribute(NetworkElementAccount.NEA_UPDATE_STATUS, NetworkElementAccountUpdateStatus.CONFIGURED.name());
        networkElementAccountMo.setAttribute(NetworkElementAccount.NEA_CURRENT_USER_NAME, networkElementAccountMo.getAttribute(NetworkElementAccount.NEA_NEXT_USER_NAME));
        networkElementAccountMo.setAttribute(NetworkElementAccount.NEA_CURRENT_PASSWORD, networkElementAccountMo.getAttribute(NetworkElementAccount.NEA_NEXT_PASSWORD));
        networkElementAccountMo.setAttribute(NetworkElementAccount.NEA_LAST_PASSWORD_CHANGE, new Date());

        //reset values
        networkElementAccountMo.setAttribute(NetworkElementAccount.NEA_NEXT_USER_NAME, NodePamConstants.NULL_USERNAME);
        networkElementAccountMo.setAttribute(NetworkElementAccount.NEA_NEXT_PASSWORD, NodePamConstants.NULL_PASSWORD);
        networkElementAccountMo.setAttribute(NetworkElementAccount.NEA_ERROR_DETAILS, NodePamConstants.NO_ERRROR_VALUE);
    }

    public void updateNetworkElementAccountWithFailure(final String nodeName,
                                             final String networkElementAccountId,
                                             final String errorDetails
    ) {
       final DataBucket dataBucket = getLiveBucket();
       final String networkElementAccountFdn = NodePamConstants.NETWORK_ELEMENT_MO + "=" + nodeName + "," + NodePamConstants.SECURITY_FUNCTION_MO + "=1," + NodePamConstants.NETWORK_ELEMENT_ACCOUNT_MO + "=" + networkElementAccountId;
       ManagedObject networkElementAccountMo = dataBucket.findMoByFdn(networkElementAccountFdn);
       logger.info("updateNetworkElementAccountWithFailure:: for networkElementAccountMo={}", networkElementAccountMo);
       networkElementAccountMo.setAttribute(NetworkElementAccount.NEA_UPDATE_STATUS, NetworkElementAccountUpdateStatus.FAILED.name());
       networkElementAccountMo.setAttribute(NetworkElementAccount.NEA_LAST_FAILED, new Date());
       networkElementAccountMo.setAttribute(NetworkElementAccount.NEA_ERROR_DETAILS, errorDetails);
    }

    public void updateNPamNEJobState(final Long neJobId, JobResult jobResult, final String errorDetails, JobState jobState) {
        logger.debug("updateNPamNEJobStateAsCompleted::START neJobId={}", neJobId);
        if (neJobId != null) {
            long poid = neJobId.longValue();
            if (poid > 0) {
                final Map<String, Object> neJobAttributes = new HashMap<>();
                neJobAttributes.put(NPamNEJob.JOB_RESULT, jobResult.name());
                neJobAttributes.put(NPamNEJob.JOB_STATE, jobState.name());
                neJobAttributes.put(NPamNEJob.JOB_END_TIME, new Date());
                if (errorDetails != null) {
                    neJobAttributes.put(NPamNEJob.JOB_ERROR_DETAILS, errorDetails);
                }

                PersistenceObject persistenceObject = this.getLiveBucket().findPoById(poid);
                if (persistenceObject != null) {
                    persistenceObject.setAttributes(neJobAttributes);
                } else {
                    logger.error("updateNPamNEJobStateAsCompleted:: PO not found with Id:{}, and skipping the neJobAttributes update", poid);
                }
            }
        }
        logger.debug("updateNPamNEJobStateAsCompleted::STOP neJobId={}", neJobId);
    }


    public void updateNPamNEJobStepToWaitingForEvent(final Long neJobId) {
        logger.debug("updateNPamNEJobStep::START neJobId={}", neJobId);
        if (neJobId != null) {
            long poid = neJobId.longValue();
            if (poid > 0) {
                final Map<String, Object> neJobAttributes = new HashMap<>();
                neJobAttributes.put(NPamNEJob.JOB_END_TIME, new Date());
                neJobAttributes.put(NPamNEJob.STEP, Step.WFE.name());

                PersistenceObject persistenceObject = this.getLiveBucket().findPoById(poid);
                if (persistenceObject != null) {
                    persistenceObject.setAttributes(neJobAttributes);
                } else {
                    logger.error("updateNPamNEJobStep:: PO not found with Id:{}, and skipping the neJobAttributes update", poid);
                }
            }
        }
        logger.debug("updateNPamNEJobStep::STOP neJobId={}", neJobId);
    }

}



