package org.jgroups.protocols.google;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.jgroups.Address;
import org.jgroups.annotations.Property;
import org.jgroups.protocols.FILE_PING;
import org.jgroups.protocols.PingData;
import org.jgroups.util.Responses;

import java.util.List;


/**
 * Discovery protocol using Google Cloud Storage (GCS) to store information on cluster members.
 * @author Bela Ban
 */
public class GOOGLE_PING2 extends FILE_PING {

    @Property(description="The name of the GCS server")
    protected String host="https://www.googleapis.com";

    protected Storage store;


    @Override
    public void init() throws Exception {
        super.init();
        store=StorageOptions.getDefaultInstance().getService();
        System.out.println("store = " + store);
    }


    @Override
    protected void createRootDir() {
        ; // do *not* create root file system (don't remove !)
    }

    @Override
    protected void readAll(List<Address> members, String clustername, Responses responses) {
        if(clustername == null)
            return;

    }


    @Override
    protected void write(List<PingData> list, String clustername) {

    }


    protected void remove(String clustername, Address addr) {
        if(clustername == null || addr == null)
            return;
    }

    @Override
    protected void removeAll(String clustername) {
        if(clustername == null)
            return;
    }





}





