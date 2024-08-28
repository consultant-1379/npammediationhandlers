/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2018
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/

package com.ericsson.oss.mediation.npam.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for performing operations on the FDN.
 */
public class FdnUtils {
    public static final Pattern NEID_PATTERN = Pattern.compile(".*?(MeContext|ManagedElement)=([A-Za-z0-9-._:/?%&!\\s]*)");
    public static final  String EMPTY_STRING = "";
    public static final  String EQUALS_OPERATOR = "=";
    public static final  String COMMA = ",";

    /**
     * Utility class
     */
    private FdnUtils() {
        //This is a utility class and cannot be instantiated
    }

    /**
     * Extracts the neId from the FDN. For example it returns 'LTE0001' for the FDN: 'ManagedElement=LTE0001,SystemFunction=1'
     *
     * @param fdn
     *            The fdn from which neId to be extracted
     * @return The neId
     */
    public static String getNeId(final String fdn) {
        final Matcher matcher = NEID_PATTERN.matcher(fdn);
        matcher.find();
        final String neId = matcher.group(2);
        assert neId != null;
        assert !EMPTY_STRING.equals(neId);
        return neId;
    }

    public static String extractNameFromFdn(final String fdn) {
        if (fdn == null) {
            return null;
        }
        final int lastIndexOfNameSeparator = fdn.lastIndexOf('=');
        return fdn.substring(lastIndexOfNameSeparator + 1);
    }

}
