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
package com.ericsson.oss.mediation.npam.util.message;

import com.ericsson.oss.itpf.sdk.eventbus.Channel;
import com.ericsson.oss.itpf.sdk.eventbus.Event;
import com.ericsson.oss.itpf.sdk.eventbus.EventConfigurationBuilder;
import com.ericsson.oss.itpf.sdk.eventbus.annotation.Endpoint;
import com.ericsson.oss.services.security.npam.api.message.NodePamEndUpdateOperation;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import static com.ericsson.oss.services.security.npam.api.message.NodePamMessageProperties.*;

@Stateless
public class NodePamEndUpdateOperationSender {

    @Inject
    @Endpoint(value = NODE_PAM_TOPIC_ENDPOINT, timeToLive=NODE_PAM_TOPIC_TTL)
    private Channel channel;

    @Inject
    private Logger logger;

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void sendMessage(final NodePamEndUpdateOperation nodePamEndUpdateOperation) {
        try {
            final EventConfigurationBuilder eventConfigurationBuilder = new EventConfigurationBuilder();
            eventConfigurationBuilder.addEventProperty(JMS_NOTIFICATION_TYPE_PROPERTY, JMS_NOTIFICATION_COMMAND_STATUS_PROPERTY);
            final Event event = channel.createEvent(nodePamEndUpdateOperation, nodePamEndUpdateOperation.getRequestId());
            channel.send(event, eventConfigurationBuilder.build());
        } catch (Exception e) {
            logger.error("NodePamEndUpdateOperationSender::sendMessage Unable to send message on topic = {}. Cause is: {}",nodePamEndUpdateOperation.getRequestId(), e.getMessage());
        }
    }
}