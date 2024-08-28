/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2021
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *
 * This class is developed considering ApplyNetconfPayloadHandler as sample
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.mediation.npam.handlers;

import com.ericsson.oss.itpf.common.event.ComponentEvent;
import com.ericsson.oss.itpf.common.event.handler.EventHandlerContext;
import com.ericsson.oss.itpf.common.event.handler.TypedEventInputHandler;
import com.ericsson.oss.itpf.common.event.handler.annotation.EventHandler;
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject;
import com.ericsson.oss.itpf.modeling.common.info.ModelInfo;
import com.ericsson.oss.itpf.modeling.schema.util.SchemaConstants;
import com.ericsson.oss.itpf.sdk.recording.SystemRecorder;
import com.ericsson.oss.mediation.adapter.netconf.jca.api.operation.NetconfOperation;
import com.ericsson.oss.mediation.adapter.netconf.jca.xa.com.provider.operation.ComNetconfOperation;
import com.ericsson.oss.mediation.adapter.netconf.jca.xa.com.provider.operation.ComNetconfOperationRequest;
import com.ericsson.oss.mediation.adapter.netconf.jca.xa.com.provider.util.Constants;
import com.ericsson.oss.mediation.cba.netconf.exception.RpcBuilderException;
import com.ericsson.oss.mediation.npam.exceptions.NetconfExecutionException;
import com.ericsson.oss.mediation.npam.util.DpsHelper;
import com.ericsson.oss.mediation.npam.util.NodePamEncryptionManager;
import com.ericsson.oss.mediation.npam.util.message.NodePamEndUpdateOperationSender;
import com.ericsson.oss.mediation.util.netconf.api.Datastore;
import com.ericsson.oss.mediation.util.netconf.api.NetconfConnectionStatus;
import com.ericsson.oss.mediation.util.netconf.api.NetconfManager;
import com.ericsson.oss.mediation.util.netconf.api.NetconfResponse;
import com.ericsson.oss.mediation.util.netconf.api.exception.NetconfManagerException;
import com.ericsson.oss.services.security.npam.api.constants.NodePamEventOperation;
import com.ericsson.oss.services.security.npam.api.job.modelentities.JobResult;
import com.ericsson.oss.services.security.npam.api.job.modelentities.JobState;
import com.ericsson.oss.services.security.npam.api.message.NodePamEndUpdateOperation;
import com.ericsson.oss.services.security.npam.api.neaccount.modelentities.NetworkElementAccountUpdateStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.transaction.xa.XAException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static com.ericsson.oss.mediation.npam.util.FdnUtils.extractNameFromFdn;
import static com.ericsson.oss.mediation.npam.util.FdnUtils.getNeId;
import static com.ericsson.oss.services.security.npam.api.message.NodePamEndUpdateOperation.getKeyForEvent;
import static com.ericsson.oss.services.security.npam.api.message.NodePamEndUpdateOperation.getKeyForJob;

@EventHandler
public final class NodePamUpdateMaintenanceUserHandler implements TypedEventInputHandler { // NOSONAR

    private static Logger logger = LoggerFactory.getLogger(NodePamUpdateMaintenanceUserHandler.class);

    private static final int RESOURCE_DENIED_MAX_RETRY_COUNT = 0;
    private static final int RESOURCE_DENIED_DELAY = 0;

    private static final String NETCONF_NOT_AVAILABLE = "the Netconf Manager is not available";


    public static final String ERROR_PREFIX = "Mediation error: ";
    public static final String NO_INFO_AVAILABLE = "no information available";

    public static final String EXPECTED_ERROR_MESSAGE = "Forced logout";

    @Inject
    private NodePamUpdateMaintenanceUserHandlerInputManager handlerInputManager;

    @Inject
    private RpcHelperGetter rpcHelperGetter;

    @Inject
    private SystemRecorder systemRecorder;

    @Inject
    NodePamEncryptionManager nodePamEncryptionManager;

    Map<String, Object> additionalInfo = new HashMap<>();

    ComNetconfOperationRequest netconfOperationRequest;

    private static final String FROM_HANDLER = "_from_handler";

    @Inject
    DpsHelper dpsHelper;

    @Inject
    private NodePamEndUpdateOperationSender nodePamEndUpdateOperationSender;

    /**
     * Callback method, will be called once the handler is loaded and used to
     * initialize attributes.
     */
    @Override
    public void init(final EventHandlerContext context) {// Added unimplemented
        // method
        Map<String, Object> contextMap = context.getEventHandlerConfiguration().getAllProperties();
        int i = 1;
        for (Object value: contextMap.entrySet()) {
            logger.debug("init:: EventHandlerContext{}  value={}", i,  value);
            i++;
        }
    }


    @Override
    public ComponentEvent onEvent(final ComponentEvent inputEvent) {
        logger.info("NodePamUpdateMaintenanceUserHandler: Notification Received through channel: {}", inputEvent);

        try {
            int i = 1;
            for (String key : inputEvent.getHeaders().keySet()) {
                Object value = inputEvent.getHeaders().get(key);
                // Print input header formatted line by line
                logger.debug("NodePamUpdateMaintenanceUserHandler:onEvent:: getHeaders{}  key={} value={}", i, key, value);
                i++;
            }

            handlerInputManager.initOperation(inputEvent.getHeaders());

            final String operation = handlerInputManager.getOperation();
            NodePamEventOperation nodePamEventOperation = NodePamEventOperation.valueOf(operation);
            if (nodePamEventOperation == NodePamEventOperation.CREATE || nodePamEventOperation == NodePamEventOperation.MODIFY ||  nodePamEventOperation == NodePamEventOperation.DELETE)
            {
                handleMaintenanceUserOperation(inputEvent, nodePamEventOperation);
            } else if (nodePamEventOperation == NodePamEventOperation.MODIFY_REMOTE_MANAGEMENT) {
                handleRemoteManagementOperation(inputEvent, nodePamEventOperation);
            }
        } catch (Exception exception) {
            logger.error("NodePamUpdateMaintenanceUserHandler:onEvent:: Generic Exception {}, stacktrace {}",  exception.getMessage(), exception);
        }

        return inputEvent;
    }


    private void handleMaintenanceUserOperation(final ComponentEvent inputEvent, NodePamEventOperation nodePamEventOperation) {

        String requestId = "null";
        String nodeName = null;
        String moId = null;
        Long neJobId = -1L;

        try {
            //Get Parameters from handlerInputManager
            handlerInputManager.init(inputEvent.getHeaders());
            logger.debug("handleMaintenanceUserOperation: Get Parameters from handlerInputManager: fdn: {}", handlerInputManager.getFdn());

            requestId = handlerInputManager.getRequestId();
            final String nodeAddress = handlerInputManager.getNodeAddress();
            final String fdn = handlerInputManager.getFdn();
            final String moType = handlerInputManager.getMoType();
            final String nameSpace = handlerInputManager.getNameSpace();
            final String nameSpaceVersion = handlerInputManager.getNameSpaceVersion();
            String subjectName = handlerInputManager.getSubjectName(); //optional
            String userNameEncrypted = handlerInputManager.getUsername(); //optional
            String passwordEncrypted = handlerInputManager.getPassword(); //optional
            neJobId = handlerInputManager.getNeJobId();

            moId = extractNameFromFdn(fdn);
            List<ComNetconfOperationRequest> netconfOperationRequests = new ArrayList<>();
            nodeName = extractNameFromFdn(nodeAddress);
            final NetconfManager netconfManager = handlerInputManager.getNetconfManager();
            validateNetconfManagerStatus(netconfManager);
            final ModelInfo modelInfo = new ModelInfo(SchemaConstants.DPS_PRIMARYTYPE, nameSpace, moType, nameSpaceVersion);
            final ManagedObject networkElementMo = getNetworkElementMo(fdn);
            final String ossModelIdentity = networkElementMo.getAttribute(Constants.OSS_MODEL_IDENTITY);
            final String neType = networkElementMo.getAttribute(Constants.NE_TYPE);

            //Attributes map
            Map<String, Object> attributes = null;
            if (nodePamEventOperation == NodePamEventOperation.DELETE) {
                attributes = new HashMap<>();
            } else { //CREATE/MODIFY
                // decrypt userName and password
                String userName = null;
                if (userNameEncrypted != null) {
                    userName = nodePamEncryptionManager.decryptPassword(userNameEncrypted);
                }
                String password = null;
                if (passwordEncrypted != null) {
                    password = nodePamEncryptionManager.decryptPassword(passwordEncrypted);
                }
                attributes = createMaintenanceUserAttributesMap(moId, subjectName, userName, password);
            }

            final NetconfOperation netconfOperation = getNetconfOperation(nodePamEventOperation);
            netconfOperationRequest = new ComNetconfOperationRequest.NetconfOperationRequestBuilder().operation(netconfOperation)
                    .modelInfo(modelInfo).fdn(fdn).attributes(attributes).ossModelIdentity(ossModelIdentity).neType(neType)
                    .retryOnResourceDenied(RESOURCE_DENIED_MAX_RETRY_COUNT, RESOURCE_DENIED_DELAY)
                    .build();
            netconfOperationRequests.add(netconfOperationRequest);

            //execute request
            String rpcBody = getRpcBody(netconfOperationRequests);
            logger.debug("handleMaintenanceUserOperation:: rpcBody: {}", rpcBody);
            executeEditConfig(nodeAddress, rpcBody, netconfManager);
            logger.debug("handleMaintenanceUserOperation:: endMaintenanceUserOperationWithSuccess");
            endMaintenanceUserOperationWithSuccess(requestId, nodeName, moId, neJobId, nodePamEventOperation);
        } catch (NetconfManagerException netconfException) {
            logger.debug("handleMaintenanceUserOperation:: NetconfManagerException message={}", netconfException.getMessage());
            endMaintenanceUserOperationWithFailure(requestId, nodeName, moId, neJobId, netconfException, nodePamEventOperation);
        } catch (NetconfExecutionException netconfExecutionException) {
            logger.debug("handleMaintenanceUserOperation:: NetconfExecutionException message={}", netconfExecutionException.getMessage() );
            endMaintenanceUserOperationWithFailure(requestId, nodeName, moId, neJobId, netconfExecutionException, nodePamEventOperation);
        } catch (Exception exception) {
            logger.debug("handleMaintenanceUserOperation:: generic exception  class={} message={}", exception.getClass(), exception.getMessage() );
            endMaintenanceUserOperationWithFailure(requestId, nodeName, moId, neJobId, exception, nodePamEventOperation);
        }
    }

    private void handleRemoteManagementOperation(final ComponentEvent inputEvent, NodePamEventOperation nodePamEventOperation) {

        String requestId = "null";
        String nodeName = null;
        Long neJobId = -1L;
        try {
            //Get Parameters from handlerInputManager
            handlerInputManager.init(inputEvent.getHeaders());
            logger.debug("handleRemoteManagementOperation: Get Parameters from handlerInputManager: fdn: {}", handlerInputManager.getFdn());

            requestId = handlerInputManager.getRequestId();
            final String nodeAddress = handlerInputManager.getNodeAddress();
            final String fdn = handlerInputManager.getFdn();
            final String moType = handlerInputManager.getMoType();
            final String nameSpace = handlerInputManager.getNameSpace();
            final String nameSpaceVersion = handlerInputManager.getNameSpaceVersion();
            final Boolean remoteManagement = handlerInputManager.getRemoteManagement(); //optional
            final Boolean restrictMaintenanceUser = handlerInputManager.getRestrictMaintenanceUser(); //optional

            neJobId = handlerInputManager.getNeJobId();
            List<ComNetconfOperationRequest> netconfOperationRequests = new ArrayList<>();
            nodeName = extractNameFromFdn(nodeAddress);
            final NetconfManager netconfManager = handlerInputManager.getNetconfManager();
            validateNetconfManagerStatus(netconfManager);
            final ModelInfo modelInfo = new ModelInfo(SchemaConstants.DPS_PRIMARYTYPE, nameSpace, moType, nameSpaceVersion);
            final ManagedObject networkElementMo = getNetworkElementMo(fdn);
            final String ossModelIdentity = networkElementMo.getAttribute(Constants.OSS_MODEL_IDENTITY);
            final String neType = networkElementMo.getAttribute(Constants.NE_TYPE);

            //Attributes map
            Map<String, Object> attributes = createRemoteManagementAttributesMap(remoteManagement, restrictMaintenanceUser);

            final NetconfOperation netconfOperation = getNetconfOperation(nodePamEventOperation);
            netconfOperationRequest = new ComNetconfOperationRequest.NetconfOperationRequestBuilder().operation(netconfOperation)
                    .modelInfo(modelInfo).fdn(fdn).attributes(attributes).ossModelIdentity(ossModelIdentity).neType(neType)
                    .retryOnResourceDenied(RESOURCE_DENIED_MAX_RETRY_COUNT, RESOURCE_DENIED_DELAY)
                    .build();
            netconfOperationRequests.add(netconfOperationRequest);

            //execute request
            String rpcBody = getRpcBody(netconfOperationRequests);
            logger.debug("handleRemoteManagementOperation:: rpcBody: {}", rpcBody);
            executeEditConfig(nodeAddress, rpcBody, netconfManager);
            logger.debug("handleRemoteManagementOperation:: endRemoteManagementOperationWithSuccess");
            endRemoteManagementOperationWithSuccess(requestId, nodeName, neJobId);
        } catch (NetconfManagerException netconfException) {
            logger.debug("handleRemoteManagementOperation:: NetconfManagerException message={}", netconfException.getMessage());
            endRemoteManagementOperationWithFailure(requestId, nodeName, neJobId, netconfException);
        } catch (NetconfExecutionException netconfExecutionException) {
            logger.debug("handleRemoteManagementOperation:: NetconfExecutionException message={}", netconfExecutionException.getMessage() );
            endRemoteManagementOperationWithFailure(requestId, nodeName, neJobId, netconfExecutionException);
        } catch (Exception exception) {
            logger.debug("handleRemoteManagementOperation:: generic exception  class={} message={}", exception.getClass(), exception.getMessage() );
            endRemoteManagementOperationWithFailure(requestId, nodeName, neJobId, exception);
        }
    }

    //for node 22.Q4 setting remoteMangement=true netconfManager.editConfig returns errored NetconfResponse editConfigResponse = [messageId=0, isError=true, errorMessage=Exception trying to parse the rpc-reply received from the node with data: Forced logout to update Roles, errorCode=0, data=, errors=null]
     private boolean isExpectedErrorMessage(final String errorMessage) {
        if (errorMessage != null) {
           return errorMessage.contains(EXPECTED_ERROR_MESSAGE);
        }
         return false;
     }

    private void endMaintenanceUserOperationWithSuccess(final String requestId, final String nodeName, String maintenanceUserId, Long neJobId, NodePamEventOperation nodePamEventOperation) {
        // update NetworkElementAccount
        if (updateNetworkElementAccount(nodePamEventOperation)) {
            dpsHelper.updateNetworkElementAccountWithSuccess(nodeName, maintenanceUserId);
        }

        // update NPamNEJob status
        dpsHelper.updateNPamNEJobState(neJobId, JobResult.SUCCESS, null, JobState.COMPLETED);

        // update status map sending on topic
        logger.debug("endMaintenanceUserOperationWithSuccess:: send message on topic");
        final String key = getRightKey(neJobId, nodeName);
        nodePamEndUpdateOperationSender.sendMessage(new NodePamEndUpdateOperation((requestId + FROM_HANDLER), nodeName, NetworkElementAccountUpdateStatus.CONFIGURED.name(), key));
    }

    private void endMaintenanceUserOperationWithFailure(final String requestId, final String nodeName, String maintenanceUserId, Long neJobId, Exception e, NodePamEventOperation nodePamEventOperation) {
        try {
            if (nodeName != null) {
                String errorMessage = getErrorMessage(e);

                // update NetworkElementAccount
                if (updateNetworkElementAccount(nodePamEventOperation)) {
                    dpsHelper.updateNetworkElementAccountWithFailure(nodeName, maintenanceUserId, errorMessage);
                }
                // update NPamNEJob status
                dpsHelper.updateNPamNEJobState(neJobId, JobResult.FAILED, errorMessage, JobState.COMPLETED);
                // update status map sending on topic
                logger.info("endMaintenanceUserOperationWithFailure:: send message on topic");
                final String key = getRightKey(neJobId, nodeName);
                NodePamEndUpdateOperation nodePamEndUpdateOperation = new NodePamEndUpdateOperation((requestId + FROM_HANDLER), nodeName, NetworkElementAccountUpdateStatus.FAILED.name(), key);
                nodePamEndUpdateOperation.setErrorDetails(errorMessage);
                nodePamEndUpdateOperationSender.sendMessage(nodePamEndUpdateOperation);

            } else {
                logger.info("endMaintenanceUserOperationWithFailure:: nothing to do cause nodeName is null");
            }
        } catch (Exception e1) {
            logger.error("endMaintenanceUserOperationWithFailure:: exception raised: {}", e1.getMessage());
        }
    }

    private boolean updateNetworkElementAccount(final NodePamEventOperation nodePamEventOperation) {
        return nodePamEventOperation == NodePamEventOperation.CREATE || nodePamEventOperation == NodePamEventOperation.MODIFY;
    }

    private void endRemoteManagementOperationWithSuccess(final String requestId, final String nodeName, Long neJobId) {

        // update NPamNEJob.step = WFE (waiting for event) leaving state = JobState.RUNNING.
        dpsHelper.updateNPamNEJobStepToWaitingForEvent(neJobId);

        // update status map sending on topic
        logger.debug("endRemoteManagementOperationWithSuccess:: send message on topic");
        final String key = getRightKey(neJobId, nodeName);
        nodePamEndUpdateOperationSender.sendMessage(new NodePamEndUpdateOperation((requestId + FROM_HANDLER), nodeName, NetworkElementAccountUpdateStatus.CONFIGURED.name(), key));
    }

    private void endRemoteManagementOperationWithFailure(final String requestId, final String nodeName, Long neJobId, Exception e) {
        try {
            if (nodeName != null) {
                String errorMessage = getErrorMessage(e);

                // update NPamNEJob status
                dpsHelper.updateNPamNEJobState(neJobId, JobResult.FAILED, errorMessage, JobState.COMPLETED);

                // update status map sending on topic
                logger.info("endRemoteManagementOperationWithFailure:: send message on topic");
                final String key = getRightKey(neJobId, nodeName);
                NodePamEndUpdateOperation nodePamEndUpdateOperation = new NodePamEndUpdateOperation((requestId + FROM_HANDLER), nodeName, NetworkElementAccountUpdateStatus.FAILED.name(), key);
                nodePamEndUpdateOperation.setErrorDetails(errorMessage);
                nodePamEndUpdateOperationSender.sendMessage(nodePamEndUpdateOperation);

            } else {
                logger.info("endRemoteManagementOperationWithFailure:: nothing to do cause nodeName is null");
            }
        } catch (Exception e1) {
            logger.error("endRemoteManagementOperationWithFailure:: exception raised: {}", e1.getMessage());
        }
    }

    private String getErrorMessage(final Exception e) {
        String errorMessage = e.getMessage();
        if (errorMessage == null) {
            errorMessage = NO_INFO_AVAILABLE;
        }
        errorMessage = ERROR_PREFIX + errorMessage;
        return errorMessage;
    }

    private Map<String, Object> createMaintenanceUserAttributesMap(
            final String maintenanceUserId,
            final String subjectName,
            final String username,
            final String password
         ) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("maintenanceUserId", maintenanceUserId);

        //we reset this value passing "" (see getSubjectNameToBeSet inside nodepamservice)
        if (subjectName != null) {
            attributes.put("subjectName", subjectName);
        }

        if (username != null) {
            attributes.put("userName", username);
        }

        if (password != null) {
            Map<String, Object> passwordMap = new HashMap<>();
            passwordMap.put("cleartext", Boolean.TRUE);
            passwordMap.put("password", password);
            attributes.put("password", passwordMap);
        }

        return attributes;
    }

    // WE use remoteManagement
    private Map<String, Object> createRemoteManagementAttributesMap(
            final Boolean remoteManagement,
            final Boolean restrictMaintenanceUser
        ) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("remoteManagement", remoteManagement);

        //Note: this value is passed !=null ONLY when jobType=CREATE_NE_ACCOUNT and NpamConfig restrict_maintenance_user=enabled
        if (restrictMaintenanceUser != null) {
            attributes.put("restrictMaintenanceUser", restrictMaintenanceUser);
        }

        return attributes;
    }

    // this method construct rpc expression (like COMNetconfXAClientProvider.prePrepare)
    public String getRpcBody(final List<ComNetconfOperationRequest> netconfOperationRequests) throws XAException {
        final StringBuilder mergedRpcBody = new StringBuilder();

        for (final ComNetconfOperationRequest request : netconfOperationRequests) {
            //int resourceDeniedMaxRetryCount = request.getResourceDeniedMaxRetryCount(); //EMARDEP these are used inside COMNetconfXAClientProvider.netconfOperationFacade to implement a retry mechanism not implemented today in ApplyNetconfPayloadHandler
            //int resourceDeniedDelay = request.getResourceDeniedDelay(); //EMARDEP these are used inside COMNetconfXAClientProvider.netconfOperationFacade to implement a retry mechanism not implemented today in ApplyNetconfPayloadHandler

            String rpcChunk = request.getRequestBody();
            if (rpcChunk != null && !rpcChunk.isEmpty()) {
                logger.debug("Rpc chunk from the request: {}", rpcChunk);
                mergedRpcBody.append(rpcChunk);
            } else {
                try {
                    rpcChunk = rpcHelperGetter.getRpcBuilderHelper().buildRpcMessage(request);
                } catch (final RpcBuilderException e) {
                    final XAException xaException = new XAException(e.getMessage());
                    xaException.addSuppressed(new ExecutionException(e.getMessage(), null));
                    xaException.errorCode = XAException.XA_RBROLLBACK;
                    throw xaException;
                }
                logger.debug("Rpc chunk built from the request: {}", rpcChunk);
                mergedRpcBody.append(rpcChunk);
            }
        }
        return mergedRpcBody.toString();
    }

    // this method send request like rest netconf implementation (like ApplyNetconfPayloadHandler.executeEditConfigs)
    private void executeEditConfig(final String nodeAddress, String rpcBody, final NetconfManager netconfManager)
            throws NetconfManagerException {
        String messageId = "";
        NetconfResponse editConfigResponse = null;

        final Datastore datastore = Datastore.RUNNING;
        logger.debug("executeEditConfig:: Applying edit-config with messageId : {}, Datastore : {}, on node : {}. Edit config rpcBody : {} ",
                messageId, datastore, nodeAddress, rpcBody);

        Instant editConfigStartTime = Instant.now();
        editConfigResponse = netconfManager.editConfig(datastore, rpcBody);
        Duration editConfigResponseDuration = Duration.between(editConfigStartTime, Instant.now());
        long executionTime = editConfigResponseDuration.toMillis();
        logger.debug("executeEditConfig:: Edit config response received for messageId : {} is : {} execution time={}", messageId, editConfigResponse, executionTime);
        if (editConfigResponse != null && editConfigResponse.isError()) {
            if (isExpectedErrorMessage(editConfigResponse.getErrorMessage())) {
                logger.warn("executeEditConfig:: Expected erroreMessage contained in erorred editConfigResponse={} SO CONTINUE AS SUCCESS", editConfigResponse);
            } else {
                throw new NetconfExecutionException(editConfigResponse.toString());
            }
        }
    }

    protected ManagedObject getNetworkElementMo(final String fdn) {
        final String neId = getNeId(fdn);
        return dpsHelper.getManagedObjectInstance(String.format(Constants.NETWORK_ELEMENT_MO, neId));
    }

    private NetconfOperation getNetconfOperation(NodePamEventOperation nodePamEventOperation) {
            switch (nodePamEventOperation) {
                case CREATE:
                    return ComNetconfOperation.CREATE;
                case MODIFY:
                case MODIFY_REMOTE_MANAGEMENT:
                    return ComNetconfOperation.MODIFY;
                case DELETE:
                    return ComNetconfOperation.DELETE;
                default:
                    return null;
            }
    }

    private void validateNetconfManagerStatus(final NetconfManager netconfManager) throws NetconfManagerException {
        if (netconfManager == null) {
            throw new NetconfManagerException(NETCONF_NOT_AVAILABLE);
        }
        if (netconfManager.getStatus() != NetconfConnectionStatus.CONNECTED) {
            final String errMessage = String.format("the Netconf Manager is not connected, current status: %s",
                    netconfManager.getStatus().toString());
            throw new NetconfManagerException(errMessage);
        }
    }

    private String getRightKey(final Long neJobId, final String nodeName) {
        if ((neJobId != null) && (neJobId.longValue() > 0)) {
                return getKeyForJob(nodeName);
        }
        return getKeyForEvent(nodeName);
    }

    /**
     * Callback method, will be called once handler is unloaded.
     */
    @Override
    public void destroy() {// Added Unimplemented method
    }

}