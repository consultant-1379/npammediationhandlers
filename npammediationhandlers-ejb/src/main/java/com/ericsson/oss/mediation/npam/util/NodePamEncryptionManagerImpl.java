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

import com.ericsson.oss.itpf.security.cryptography.CryptographyService;

import javax.inject.Inject;
import javax.xml.bind.DatatypeConverter;
import java.io.UnsupportedEncodingException;

public class NodePamEncryptionManagerImpl implements NodePamEncryptionManager {

    @Inject
    CryptographyService cryptoServ;

    public static final String CHARSET_NAME = "UTF-8";

    @Override
    public String encryptPassword(String password) throws UnsupportedEncodingException {
       return DatatypeConverter.printBase64Binary(cryptoServ.encrypt(password.getBytes(CHARSET_NAME)));
    }

    @Override
    public String decryptPassword(String password) throws UnsupportedEncodingException {
        return new String(cryptoServ.decrypt(DatatypeConverter.parseBase64Binary(password)), CHARSET_NAME);
    }
}
