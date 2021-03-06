package com.qiniu.service.qoss;

import com.qiniu.storage.BucketManager.*;
import com.qiniu.service.interfaces.ILineProcess;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.model.StorageType;
import com.qiniu.util.Auth;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ChangeType extends OperationBase implements ILineProcess<Map<String, String>>, Cloneable {

    final private int type;

    public ChangeType(Auth auth, Configuration configuration, String bucket, int type, String resultPath,
                      int resultIndex) throws IOException {
        super("type", auth, configuration, bucket, resultPath, resultIndex);
        this.type = type;
    }

    public ChangeType(Auth auth, Configuration configuration, String bucket, int type, String resultPath)
            throws IOException {
        this(auth, configuration, bucket, type, resultPath, 0);
    }

    synchronized public BatchOperations getOperations(List<Map<String, String>> lineList) {
        lineList.forEach(line -> {
            if (line.get("key") == null)
                errorLineList.add(String.valueOf(line) + "\tno target key in the line map.");
            else
                batchOperations.addChangeTypeOps(bucket, type == 0 ? StorageType.COMMON : StorageType.INFREQUENCY,
                        line.get("key"));
        });
        return batchOperations;
    }

    public String getInputParams(Map<String, String> line) {
        return line.get("key");
    }
}
