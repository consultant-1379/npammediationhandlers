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
package com.ericsson.oss.mediation.npam.handlers;

import com.ericsson.oss.mediation.npam.exceptions.AttributeNotFoundException;
import com.ericsson.oss.mediation.util.netconf.api.NetconfManager;

import java.util.Map;

/**
 * The Class NodePamUpdateMaintenanceUserHandlerInputManager is used to handle the input event headers.
 */
public class NodePamUpdateMaintenanceUserHandlerInputManager {

    protected static final String NODE_ADDRESS_ATTR = "nodeAddress";
    protected static final String REQUEST_ID = "nodePamRequestId";

    protected static final String NODE_PAM_FDN = "nodePamFdn";
    protected static final String MO_TYPE = "nodePamMoType";
    protected static final String NAME_SPACE = "nodePamNameSpace";
    protected static final String NAMESPACE_VERSION = "nodePamNameSpaceVersion";

    protected static final String NODE_PAM_OPERATION = "nodePamOperation";
    protected static final String SUBJECT_NAME = "nodePamSubjectName";
    protected static final String USER_NAME = "nodePamUsername";
    protected static final String PASS_WORD = "nodePamPassword";
    protected static final String REMOTE_MANAGEMENT = "nodePamRemoteManagement";
    protected static final String RESTRICT_MAINTENANCE_USER = "nodePamRestrictMaintenanceUser";

    protected static final String NE_JOB_ID = "neJobId";

    protected static final String NETCONF_MANAGER_ATTR = "netconfManager";

    private Map<String, Object> headers;
    private String nodeAddress;
    private String requestId;
    private String fdn;
    private String moType;
    private String nameSpace;
    private String nameSpaceVersion;
    private String operation;
    private String subjectName;
    private String username;
    private String password;
    private Boolean remoteManagement;
    private Boolean restrictMaintenanceUser;
    private Long neJobId;
    private NetconfManager netconfManager;


    /**
     * Initialize method used to retrieve input attributes in the input event headers , needs to be invoked first before getting any attribute
     *
     * @param inputEventHeaders
     */
    public void init(final Map<String, Object> inputEventHeaders) {
        this.headers = inputEventHeaders;
        nodeAddress = getAttribute(NODE_ADDRESS_ATTR, true);
        requestId = getAttribute(REQUEST_ID, true);

        fdn = getAttribute(NODE_PAM_FDN, true);
        moType = getAttribute(MO_TYPE, true);
        nameSpace = getAttribute(NAME_SPACE, true);
        nameSpaceVersion = getAttribute(NAMESPACE_VERSION, true);
        operation = getAttribute(NODE_PAM_OPERATION, true);
        subjectName = getAttribute(SUBJECT_NAME, false);
        username = getAttribute(USER_NAME, false);
        password = getAttribute(PASS_WORD, false);
        remoteManagement = getAttribute(REMOTE_MANAGEMENT, false);
        restrictMaintenanceUser = getAttribute(RESTRICT_MAINTENANCE_USER, false);

        neJobId = getAttribute(NE_JOB_ID, false);

        netconfManager = getAttribute(NETCONF_MANAGER_ATTR, false);
    }

    public void initOperation(final Map<String, Object> inputEventHeaders) {
        this.headers = inputEventHeaders;
        operation = getAttribute(NODE_PAM_OPERATION, true);
    }

    /**
     * Helper method used to retrieve the input attributes
     *
     * @param attributeName
     * @param isMandatory
     * @return attributeValue
     */
    private <T> T getAttribute(final String attributeName, final boolean isMandatory) {
        @SuppressWarnings("unchecked")
        final T attributeValue = (T) headers.get(attributeName);
        if (isMandatory && (attributeValue == null || (attributeValue instanceof String && ((String) attributeValue).trim().isEmpty()))) {
            throw new AttributeNotFoundException(String.format("Attribute %s is invalid: %s", attributeName, attributeValue));
        }

        return attributeValue;
    }

    public String getNodeAddress() {
        return nodeAddress;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getFdn() {
        return fdn;
    }

    public String getMoType() {
        return moType;
    }

    public String getNameSpace() {
        return nameSpace;
    }

    public String getNameSpaceVersion() {
        return nameSpaceVersion;
    }

    public String getOperation() {
        return operation;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Boolean getRemoteManagement() {
        return remoteManagement;
    }

    public Boolean getRestrictMaintenanceUser() {
        return restrictMaintenanceUser;
    }

    public Long getNeJobId() {
        return neJobId;
    }

    public NetconfManager getNetconfManager() {
        return netconfManager;
    }
}
