/*
 *******************************************************************************
 * COPYRIGHT Ericsson 2022
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 */
package com.ericsson.oss.mediation.npam.util;

import javax.ejb.Local;
import java.io.UnsupportedEncodingException;

@Local
public interface NodePamEncryptionManager {
    String encryptPassword(String password) throws UnsupportedEncodingException;

    String decryptPassword(String password) throws UnsupportedEncodingException;
}
