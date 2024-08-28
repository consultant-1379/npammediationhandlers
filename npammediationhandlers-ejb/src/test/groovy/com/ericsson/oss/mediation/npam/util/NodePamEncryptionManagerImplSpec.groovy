package com.ericsson.oss.mediation.npam.util

import com.ericsson.cds.cdi.support.rule.ObjectUnderTest
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.itpf.security.cryptography.CryptographyService
import spock.lang.Unroll

import javax.inject.Inject

class NodePamEncryptionManagerImplSpec extends CdiSpecification {

    @ObjectUnderTest
    private NodePamEncryptionManagerImpl objUnderTest

    @Inject
    CryptographyService criptoMock

    def setup() {
    }

    @Unroll
    def 'NodePamEncryptionManagerImpl test encryptPassword/decryptPassword for #password'(password) {
        given: 'simple CryptographyService implemented here'

            // we similate simple algo that add +1
            criptoMock.encrypt(_) >> { args ->
                byte[] bytes = args[0]
                for (int i=0; i<bytes.length; i++) {
                    bytes[i]++;
                }
                return bytes
            }

            // we similate simple algo that add -1
            criptoMock.decrypt(_)  >> { args ->
                byte[] bytes = args[0]
                for (int i=0; i<bytes.length; i++) {
                    bytes[i]--;
                }
                return bytes
            }
        when: 'invoke decryptPassword(encryptPassword)'
            def decryptedOfEncryptedPassword = objUnderTest.decryptPassword(objUnderTest.encryptPassword(password))
        then: 'value is equal to original one'
            decryptedOfEncryptedPassword == password
        where: ''
            password         || _
            'testPassword1'  || _
            'aaa         '   || _
            'aaa!@#!#DD  '   || _
            '$%$%%@@@   #'   || _
    }

    private byte[] getBytes(final String password) {
        return  password.getBytes("UTF-8")
    }

    def 'dummy test to force maven to run test class with all other test methods annotated with @Unroll'() {
        when:
        objUnderTest.toString()
        System.out.println(new Date())
        then:
        true
    }
}

