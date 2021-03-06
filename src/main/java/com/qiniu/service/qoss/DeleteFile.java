package com.qiniu.service.qoss;

import com.qiniu.storage.BucketManager.BatchOperations;
import com.qiniu.service.interfaces.ILineProcess;
import com.qiniu.storage.Configuration;
import com.qiniu.util.Auth;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DeleteFile extends OperationBase implements ILineProcess<Map<String, String>>, Cloneable {

    public DeleteFile(Auth auth, Configuration configuration, String bucket, String resultPath,
                      int resultIndex) throws IOException {
        super("delete", auth, configuration, bucket, resultPath, resultIndex);
    }

    public DeleteFile(Auth auth, Configuration configuration, String bucket, String resultPath) throws IOException {
        this(auth, configuration, bucket, resultPath, 0);
    }

    synchronized public BatchOperations getOperations(List<Map<String, String>> lineList) {
        lineList.forEach(line -> {
            if (line.get("key") == null)
                errorLineList.add(String.valueOf(line) + "\tno target key in the line map.");
            else
                batchOperations.addDeleteOp(bucket, line.get("key"));
        });
        return batchOperations;
    }

    public String getInputParams(Map<String, String> line) {
        return line.get("key");
    }
}
