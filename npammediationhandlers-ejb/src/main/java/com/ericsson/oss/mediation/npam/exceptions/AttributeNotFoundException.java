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
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.mediation.npam.exceptions;

public class AttributeNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1914294237511587423L;

    public AttributeNotFoundException(final String message) {
        super(message);
    }
}
