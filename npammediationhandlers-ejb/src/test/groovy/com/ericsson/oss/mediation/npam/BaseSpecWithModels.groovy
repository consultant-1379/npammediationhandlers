/*
 * ------------------------------------------------------------------------------
 * *******************************************************************************
 *  COPYRIGHT Ericsson  2017
 *
 *  The copyright to the computer program(s) herein is the property of
 *  Ericsson Inc. The programs may be used and/or copied only with written
 *  permission from Ericsson Inc. or in accordance with the terms and
 *  conditions stipulated in the agreement/contract under which the
 *  program(s) have been supplied.
 * ******************************************************************************
 * ----------------------------------------------------------------------------
 */

package com.ericsson.oss.mediation.npam

import com.ericsson.cds.cdi.support.configuration.InjectionProperties
import com.ericsson.cds.cdi.support.providers.custom.model.ClasspathModelServiceProvider
import com.ericsson.cds.cdi.support.providers.custom.model.ModelPattern
import com.ericsson.cds.cdi.support.rule.custom.node.ManagedObjectData
import com.ericsson.cds.cdi.support.rule.custom.node.NodeDataInjector
import com.ericsson.cds.cdi.support.rule.custom.node.NodeDataProvider
import com.ericsson.cds.cdi.support.spock.CdiSpecification
import com.ericsson.oss.itpf.datalayer.dps.stub.RuntimeConfigurableDps
import com.ericsson.oss.itpf.modeling.schema.util.SchemaConstants
import org.junit.Rule

/**
 * This class would be base for adding required models to the tests.
 * It uses both node injector and RuntimeConfigurableDps.
 */
class BaseSpecWithModels extends CdiSpecification implements NodeDataProvider {
    static filteredModels = [
            new ModelPattern(SchemaConstants.DPS_PRIMARYTYPE, 'OSS_NE_DEF', '.*', '.*'),
            new ModelPattern(SchemaConstants.DPS_PRIMARYTYPE, 'OSS_TOP', '.*', '.*'),
            new ModelPattern(SchemaConstants.DPS_PRIMARYTYPE, 'OSS_NE_CM_DEF', '.*', '.*'),
            new ModelPattern(SchemaConstants.DPS_PRIMARYTYPE, 'OSS_NE_SEC_DEF', '.*', '.*'),
            new ModelPattern(SchemaConstants.DPS_PRIMARYTYPE, 'ComTop', '.*', '.*'),
            new ModelPattern(SchemaConstants.DPS_PRIMARYTYPE, 'ECIM_Top', '.*', '.*'),
            new ModelPattern(SchemaConstants.DPS_PRIMARYTYPE, 'ECIM_Security_Management', '.*', '.*'),
            new ModelPattern(SchemaConstants.DPS_PRIMARYTYPE, 'RcsSecM', '.*', '.*'),
            new ModelPattern(SchemaConstants.DPS_PRIMARYTYPE, 'RcsUser', '.*', '.*'),
            new ModelPattern(SchemaConstants.DPS_PRIMARYTYPE, 'RcsUser', '.*', '.*'),
//            new ModelPattern(SchemaConstants.DPS_PRIMARYTYPE, 'TOP_MED', 'ConnectivityInformation', '.*'),
//            new ModelPattern(SchemaConstants.DPS_PRIMARYTYPE, 'MEDIATION', 'ConnectivityInformation', '.*'),
            new ModelPattern(SchemaConstants.DPS_RELATIONSHIP, 'ComTop', '.*', '.*'),
            new ModelPattern(SchemaConstants.DPS_RELATIONSHIP, 'OSS_TOP', '.*', '.*'),
            new ModelPattern(SchemaConstants.DPS_RELATIONSHIP, 'RcsSecM', '.*', '.*'),
            new ModelPattern(SchemaConstants.DPS_RELATIONSHIP, 'RcsUser', '.*', '.*'),

            new ModelPattern(SchemaConstants.OSS_CDT, 'OSS_NE_DEF', '.*', '.*'),
            new ModelPattern(SchemaConstants.OSS_CDT, 'ComTop', '.*', '.*'),
            new ModelPattern(SchemaConstants.OSS_CDT, 'RcsUser', '.*', '.*'),

            new ModelPattern(SchemaConstants.OSS_EDT, 'OSS_NE_DEF', '.*', '.*'),
            new ModelPattern(SchemaConstants.OSS_EDT, 'OSS_NE_CM_DEF', '.*', '.*'),
            new ModelPattern(SchemaConstants.OSS_EDT, 'RcsUser', '.*', '.*'),

            new ModelPattern(SchemaConstants.OSS_TARGETTYPE, 'NODE', 'RadioNode', '.*'),

            new ModelPattern(SchemaConstants.OSS_TARGETVERSION, '.*', '.*', '.*'),

            new ModelPattern(SchemaConstants.CFM_MIMINFO, 'RadioNode', '.*', '.*')
    ]

    static ClasspathModelServiceProvider classpathModelServiceProvider = new ClasspathModelServiceProvider(filteredModels)

    RuntimeConfigurableDps runtimeDps

    def setup() {
        runtimeDps = cdiInjectorRule.getService(RuntimeConfigurableDps)
    }

    @Override
    def addAdditionalInjectionProperties(InjectionProperties injectionProperties) {
        super.addAdditionalInjectionProperties(injectionProperties)
        injectionProperties.addInjectionProvider(classpathModelServiceProvider)
        injectionProperties.autoLocateFrom('com.ericsson.oss.services.security')
    }

    @Rule
    public final NodeDataInjector nodeDataInjector = new NodeDataInjector(this, cdiInjectorRule)

    @Override
    Map<String, Object> getAttributesForMo(final String moFdn) {
        return [:]
    }

    @Override
    List<ManagedObjectData> getAdditionalNodeManagedObjects() {
        return []
    }
}
