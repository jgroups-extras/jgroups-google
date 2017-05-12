package org.jgroups.google;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.*;
import org.jgroups.util.UUID;
import org.jgroups.util.Util;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests the API to Google Cloud Storage. This only works with a valid Google Cloud Platform account. Remove the @Ignore
 * annotations from the tests: I didn't want to run against GCS every time (this costs money)...
 * @author Bela Ban
 * @since  1.0.0
 */
public class ClientTest {
    protected Storage store;
    protected static final String BUCKET="clienttest-" + ((UUID)Util.createRandomAddress()).toStringLong();

    @Before @Ignore public void init() {
        store=StorageOptions.getDefaultInstance().getService();
        assert store != null;
    }

    @After @Ignore public void destroy() {
        if(store != null)
            store.delete(BUCKET);
    }


    @Test @Ignore
    public void testBucketAndObjectCreation() {
        BucketInfo bucket_info=BucketInfo.of(BUCKET);
        Bucket bucket=store.create(bucket_info);
        System.out.println("bucket = " + bucket);
        assert bucket != null;

        // create some objects in the bucket:
        byte[] ignored="hello world".getBytes();
        String[] objects={"DrawCluster/one/first.list", "ChatCluster/one/second.list"};
        for(String obj: objects)
            bucket.create(obj, ignored);

        Page<Blob> list=bucket.list();
        int count=0;
        for(Blob blob: list.iterateAll()) {
            System.out.printf("blob: %s\n", blob);
            blob.delete();
            count++;
        }
        assert count == 2;
    }


    @Test @Ignore public void testBucketExists() {
        /*BucketInfo info=BucketInfo.newBuilder("mybucket")
          .setStorageClass(StorageClass.REGIONAL)
          .setLocation("us").build();*/
        //BucketInfo info=BucketInfo.of(BUCKET);
        //Bucket bucket=store.create(info);
        //System.out.println("bucket = " + bucket);

        Bucket bucket=store.get(BUCKET);
        if(bucket == null) {
            //if(!create_bucket_if_not_exists)
            //  throw new IllegalStateException("bucket " + location + " doesn't exist");
            BucketInfo info=BucketInfo.of(BUCKET);
            bucket=store.create(info);
        }

    }

    @Test @Ignore public void testReadExistingFile() {
        Bucket bucket=store.get("ispnperftest");
        for(Blob blob: bucket.list(Storage.BlobListOption.prefix("demo")).iterateAll()) {
            String name=blob.getName();
            if(name.endsWith(".list")) {
                System.out.println("name=" + blob.getName()); //  + ", blob = " + blob);
                byte[] contents=blob.getContent();
                byte[] new_contents=new byte[contents.length + 14];
                System.arraycopy(contents, 0, new_contents, 0, contents.length);
                byte[] add="hello world\n".getBytes();
                System.arraycopy(add, 0, new_contents, contents.length, add.length);
                bucket.create(name, new_contents);
            }
        }
    }
}
