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

import com.ericsson.oss.mediation.adapter.netconf.jca.xa.com.provider.util.RpcBuilderHelper;

public class RpcHelperGetter {

    public RpcBuilderHelper getRpcBuilderHelper() {
        return new RpcBuilderHelper();
    }

}
