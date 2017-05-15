package org.jgroups.protocols.google;

import com.google.cloud.storage.*;
import org.jgroups.Address;
import org.jgroups.annotations.Property;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.protocols.FILE_PING;
import org.jgroups.protocols.PingData;
import org.jgroups.util.Responses;
import org.jgroups.util.Util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;


/**
 * Discovery protocol to be used in the Google cloud (Google Cloud Platform, GCP). Uses Google Cloud Storage (GCS) to
 * store and retrieve information about cluster members. The cluster name maps to a bucket and the list(s) of member
 * data are objects within that bucket.
 * <br/>
 * Note that field <em>location</em> of the superclass is used as bucket name. It needs to be globally unique, or else
 * bucket creation will fail if the bucket does not exist yet (or is owned by a different user).
 * It is recommended to create a bucket first (e.g. via gsutil) and then use this bucket. This way, an existing
 * bucket of the same name created by a different user doesn't prevent startup of the member due to an exception.
 * @author Bela Ban
 * @since 1.0.0
 */
public class GOOGLE_PING2 extends FILE_PING {

    protected static final short GOOGLE_PING_ID=2018;


    static {
        ClassConfigurator.addProtocol(GOOGLE_PING_ID, GOOGLE_PING2.class);
    }

    @Property(description="The region (eu, asia, us) where the bucket will be created. If non-null, the storage class " +
      "will be multi-regional, else regional")
    protected String  region;

    @Property(description="Creates the bucket if it doesn't exist")
    protected boolean create_bucket_if_not_exists=true;

    protected Storage store;  // client to access GCS

    protected Bucket  bucket; // the bucket to be used; all objects are in this bucket


    @Override
    public void init() throws Exception {
        super.init();

        if(location == null)
            throw new IllegalStateException("location must be set");
        store=StorageOptions.getDefaultInstance().getService();

        // Fetch the existing bucket: this either returns null (bucket doesn't exist), or throws an exception
        // (e.g. if the bucket is owned by a different user)
        bucket=store.get(location);
        if(bucket == null) {
            if(!create_bucket_if_not_exists)
                throw new IllegalStateException("bucket " + location + " doesn't exist");
            BucketInfo info=region == null? BucketInfo.of(location)
              : BucketInfo.newBuilder(location).setStorageClass(StorageClass.REGIONAL).setLocation(this.region).build();
            bucket=store.create(info);
            log.debug("created bucket %s", bucket.getName());
        }
        log.debug("using bucket %s", bucket.getName());
    }



    @Override
    protected void createRootDir() {
        ; // do *not* create root file system (don't remove !)
    }

    @Override
    protected void readAll(List<Address> members, String clustername, Responses responses) {
        if(clustername == null)
            return;
        try {
            clustername=sanitize(clustername);
            for(Blob blob: bucket.list(Storage.BlobListOption.prefix(clustername)).iterateAll()) {
                String name=blob.getName();
                if(name.endsWith(SUFFIX)) {
                    if(log.isTraceEnabled())
                        log.trace("%s: reading %s", local_addr, blob.getName());
                    byte[] contents=blob.getContent();
                    readResponse(contents, members, responses);
                }
            }
        }
        catch(Exception ex) {
            log.error(Util.getMessage("FailedReadingAddresses"), ex);
        }
    }


    protected void readResponse(byte[] buf, List<Address> mbrs, Responses responses) {
        if(buf != null && buf.length > 0) {
            try {
                List<PingData> list=read(new ByteArrayInputStream(buf));
                if(list != null) {
                    for(PingData data : list) {
                        if(mbrs == null || mbrs.contains(data.getAddress()))
                            responses.addResponse(data, data.isCoord());
                        if(local_addr != null && !local_addr.equals(data.getAddress()))
                            addDiscoveryResponseToCaches(data.getAddress(), data.getLogicalName(), data.getPhysicalAddr());
                    }
                }
            }
            catch(Throwable e) {
                log.error(Util.getMessage("FailedUnmarshallingResponse"), e);
            }
        }
    }



    @Override
    protected void write(List<PingData> list, String clustername) {
        String filename=addressToFilename(local_addr);
        String key=sanitize(clustername) + "/" + sanitize(filename);
        if(log.isTraceEnabled())
            log.trace("%s: writing %s", local_addr, key);
        try {
            ByteArrayOutputStream out=new ByteArrayOutputStream(4096);
            write(list, out);
            byte[] data=out.toByteArray();
            bucket.create(key, data);
        } catch (Exception e) {
            log.error(Util.getMessage("ErrorMarshallingObject"), e);
        }
    }


    protected void remove(String clustername, Address addr) {
        if(clustername == null || addr == null)
            return;
        String filename=addressToFilename(addr);//  addr instanceof org.jgroups.util.UUID? ((org.jgroups.util.UUID)addr).toStringLong() : addr.toString();
        String key=sanitize(clustername) + "/" + sanitize(filename);
        try {
            BlobId obj=BlobId.of(location, key);
            boolean success=store.delete(obj);
            if(success && log.isTraceEnabled())
                log.trace("%s: removed %s/%s", local_addr, location, key);
        }
        catch(Exception e) {
            log.error(Util.getMessage("FailureRemovingData"), e);
        }
    }

    @Override
    protected void removeAll(String clustername) {
        if(clustername == null)
            return;

        try {
            for(Blob blob: bucket.list(Storage.BlobListOption.prefix(clustername)).iterateAll()) {
                if(blob.getName().endsWith(".list")) {
                    if(log.isTraceEnabled())
                        log.trace("%s: deleting %s", local_addr, blob.getName());
                    blob.delete();
                }
            }
            // store.delete(BlobId.of(location, clustername)); // also delete the top folder for the cluster name
        }
        catch(Exception ex) {
            log.error(Util.getMessage("FailedDeletingAllObjects"), ex);
        }
    }




    /** Sanitizes bucket and folder names according to AWS guidelines */
    protected static String sanitize(final String name) {
        return name.replace('/', '-').replace('\\', '-');
    }


}





