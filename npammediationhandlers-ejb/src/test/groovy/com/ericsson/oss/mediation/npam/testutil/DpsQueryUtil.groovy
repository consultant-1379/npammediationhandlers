package com.ericsson.oss.mediation.npam.testutil

import com.ericsson.oss.itpf.datalayer.dps.DataBucket
import com.ericsson.oss.itpf.datalayer.dps.DataPersistenceService
import com.ericsson.oss.itpf.datalayer.dps.persistence.ManagedObject
import com.ericsson.oss.itpf.sdk.core.annotation.EServiceRef;

class DpsQueryUtil
{
    @EServiceRef
    private DataPersistenceService dataPersistenceService;

    public ManagedObject findManagedObject(final String fdn) {
        DataBucket dataBucket = dataPersistenceService.getLiveBucket()
        final ManagedObject managedObject = dataBucket.findMoByFdn(fdn);
        return managedObject;
    }

    public ManagedObject getNetworkElementAccount(String nodeName, int networkElementAccountId) {
        final String networkElementAccountFdn = "NetworkElement=" + nodeName + ",SecurityFunction=1,NetworkElementAccount=" + networkElementAccountId
        return findManagedObject(networkElementAccountFdn)
    }
}
