package com.qiniu.service.media;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.qiniu.model.media.Item;
import com.qiniu.model.media.PfopResult;
import com.qiniu.persistence.FileMap;
import com.qiniu.common.QiniuException;
import com.qiniu.service.interfaces.ILineProcess;
import com.qiniu.util.HttpResponseUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class QueryPfopResult implements ILineProcess<Map<String, String>>, Cloneable {

    final private String processName;
    final private String persistentIdIndex;
    private MediaManager mediaManager;
    private int retryCount;
    final private String resultPath;
    private String resultTag;
    private int resultIndex;
    private FileMap fileMap;

    public QueryPfopResult(String persistentIdIndex, String resultPath, int resultIndex) throws IOException {
        this.processName = "pfopresult";
        if (persistentIdIndex == null || "".equals(persistentIdIndex))
            throw new IOException("please set the persistentIdIndex.");
        else this.persistentIdIndex = persistentIdIndex;
        this.mediaManager = new MediaManager();
        this.resultPath = resultPath;
        this.resultTag = "";
        this.resultIndex = resultIndex;
        this.fileMap = new FileMap(resultPath, processName, String.valueOf(resultIndex));
        this.fileMap.initDefaultWriters();
    }

    public QueryPfopResult(String persistentIdIndex, String resultPath) throws IOException {
        this(persistentIdIndex, resultPath, 0);
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

    public QueryPfopResult clone() throws CloneNotSupportedException {
        QueryPfopResult queryPfopResult = (QueryPfopResult)super.clone();
        queryPfopResult.mediaManager = new MediaManager();
        queryPfopResult.fileMap = new FileMap(resultPath, processName, resultTag + String.valueOf(++resultIndex));
        try {
            queryPfopResult.fileMap.initDefaultWriters();
        } catch (IOException e) {
            throw new CloneNotSupportedException("init writer failed.");
        }
        return queryPfopResult;
    }

    public void processLine(List<Map<String, String>> lineList, int retryCount) throws IOException {
        String result = null;
        PfopResult pfopResult;
        Gson gson = new Gson();
        for (Map<String, String> line : lineList) {
            int retry = retryCount;
            while (retry > 0) {
                try {
                    result = mediaManager.getPfopResultBodyById(line.get(persistentIdIndex));
                    retry = 0;
                } catch (QiniuException e) {
                    retry--;
                    HttpResponseUtils.processException(e, retry, fileMap,
                            new ArrayList<String>(){{add(line.get(persistentIdIndex));}});
                }
            }
            if (result != null && !"".equals(result)) {
                pfopResult = gson.fromJson(result, PfopResult.class);
                // 可能有多条转码指令
                for (Item item : pfopResult.items) {
                    // code == 0 时表示转码已经成功，不成功的情况下记录下转码参数和错误方便进行重试
                    if (item.code == 0) {
                        fileMap.writeSuccess(line.get(persistentIdIndex) + "\t" + pfopResult.inputKey + "\t" +
                                item.key + "\t" + result);
                    } else {
                        fileMap.writeError( line.get(persistentIdIndex) + "\t" + pfopResult.inputKey + "\t" +
                                item.key + "\t" + item.cmd + "\t" + item.code + "\t" + item.desc + "\t" + item.error);
                    }
                }
            } else {
                fileMap.writeError( line.get(persistentIdIndex) + "\tempty pfop result");
            }
        }
    }

    public void processLine(List<Map<String, String>> lineList) throws IOException {
        processLine(lineList, retryCount);
    }

    public void closeResource() {
        fileMap.closeWriters();
    }
}
