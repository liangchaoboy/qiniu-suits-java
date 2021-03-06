package com.qiniu.service.media;

import com.qiniu.persistence.FileMap;
import com.qiniu.common.QiniuException;
import com.qiniu.sdk.OperationManager;
import com.qiniu.service.interfaces.ILineProcess;
import com.qiniu.storage.Configuration;
import com.qiniu.util.Auth;
import com.qiniu.util.HttpResponseUtils;
import com.qiniu.util.StringMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QiniuPfop implements ILineProcess<Map<String, String>>, Cloneable {

    final private String processName;
    final private Auth auth;
    final private Configuration configuration;
    private OperationManager operationManager;
    final private String bucket;
    final private String fopsIndex;
    final private StringMap pfopParams;
    public int retryCount;
    final private String resultPath;
    private String resultTag;
    private int resultIndex;
    public FileMap fileMap;

    public QiniuPfop(Auth auth, Configuration configuration, String bucket, String pipeline, String fopsIndex,
                     String resultPath, int resultIndex) throws IOException {
        this.processName = "pfop";
        this.auth = auth;
        this.configuration = configuration;
        this.operationManager = new OperationManager(auth, configuration);
        this.bucket = bucket;
        if (fopsIndex == null || "".equals(fopsIndex)) throw new IOException("please set the fopsIndex.");
        else this.fopsIndex = fopsIndex;
        this.pfopParams = new StringMap().putNotEmpty("pipeline", pipeline);
        this.resultPath = resultPath;
        this.resultTag = "";
        this.resultIndex = resultIndex;
        this.fileMap = new FileMap(resultPath, processName, String.valueOf(resultIndex));
        this.fileMap.initDefaultWriters();
    }

    public QiniuPfop(Auth auth, Configuration configuration, String bucket, String pipeline, String fopsIndex,
                     String resultPath) throws IOException {
        this(auth, configuration, bucket, pipeline, fopsIndex, resultPath, 0);
    }

    public String getProcessName() {
        return this.processName;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount < 1 ? 1 : retryCount;
    }

    public void setResultTag(String resultTag) {
        this.resultTag = resultTag == null ? "" : resultTag;
    }

    public QiniuPfop clone() throws CloneNotSupportedException {
        QiniuPfop qiniuPfop = (QiniuPfop)super.clone();
        qiniuPfop.operationManager = new OperationManager(auth, configuration);
        qiniuPfop.fileMap = new FileMap(resultPath, processName, resultTag + String.valueOf(++resultIndex));
        try {
            qiniuPfop.fileMap.initDefaultWriters();
        } catch (IOException e) {
            throw new CloneNotSupportedException("init writer failed.");
        }
        return qiniuPfop;
    }

    public void processLine(List<Map<String, String>> lineList, int retryCount) throws IOException {
        String key;
        String persistentId = null;
        for (Map<String, String> line : lineList) {
            key = line.get("key");
            int retry = retryCount;
            while (retry > 0) {
                try {
                    persistentId = operationManager.pfop(bucket, key, line.get(fopsIndex), pfopParams);
                    retry = 0;
                } catch (QiniuException e) {
                    retry--;
                    HttpResponseUtils.processException(e, retry, fileMap,
                            new ArrayList<String>(){{ add(line.get("key") + "\t" + line.get(fopsIndex)); }});
                }
            }
            if (persistentId != null && !"".equals(persistentId)) fileMap.writeSuccess(persistentId + "\t" + key);
            else fileMap.writeError( key + "\t" + line.get(fopsIndex) + "\tempty persistent id");
        }
    }

    public void processLine(List<Map<String, String>> lineList) throws IOException {
        processLine(lineList, retryCount);
    }

    public void closeResource() {
        fileMap.closeWriters();
    }
}