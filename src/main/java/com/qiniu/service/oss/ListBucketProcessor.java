package com.qiniu.service.oss;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qiniu.common.*;
import com.qiniu.common.QiniuBucketManager.*;
import com.qiniu.http.Client;
import com.qiniu.http.Response;
import com.qiniu.interfaces.IOssFileProcess;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.model.FileInfo;
import com.qiniu.storage.model.FileListing;
import com.qiniu.util.JSONConvertUtils;
import com.qiniu.util.StringMap;
import com.qiniu.util.StringUtils;
import com.qiniu.util.UrlSafeBase64;

import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class ListBucketProcessor {

    private QiniuAuth auth;
    private QiniuBucketManager bucketManager;
    private Client client;
    private FileReaderAndWriterMap targetFileReaderAndWriterMap;

    private static volatile ListBucketProcessor listBucketProcessor = null;

    public ListBucketProcessor(QiniuAuth auth, Configuration configuration, FileReaderAndWriterMap targetFileReaderAndWriterMap) {
        this.auth = auth;
        this.bucketManager = new QiniuBucketManager(auth, configuration);
        this.client = new Client();
        this.targetFileReaderAndWriterMap = targetFileReaderAndWriterMap;
    }

    public static ListBucketProcessor getChangeStatusProcessor(QiniuAuth auth, Configuration configuration, FileReaderAndWriterMap targetFileReaderAndWriterMap) {
        if (listBucketProcessor == null) {
            synchronized (ListBucketProcessor.class) {
                if (listBucketProcessor == null) {
                    listBucketProcessor = new ListBucketProcessor(auth, configuration, targetFileReaderAndWriterMap);
                }
            }
        }
        return listBucketProcessor;
    }

    /*
    单次列举，可以传递 marker 和 limit 参数，通常采用此方法进行并发处理
     */
    public String doFileList(String bucket, String prefix, String delimiter, String marker, int limit, String endFile,
                             IOssFileProcess iOssFileProcessor, int retryCount) {

        String endMarker = null;

        try {
            FileListing fileListing = bucketManager.listFiles(bucket, prefix, marker, limit, delimiter);
            checkFileListing(fileListing);
            FileInfo[] items = fileListing.items;
            String fileInfoStr;

            for (int i = 0; i < items.length; i++) {
                if (items[i].key.equals(endFile)) {
                    fileListing = null;
                    break;
                }
                fileInfoStr = JSONConvertUtils.toJson(items[i]);
                targetFileReaderAndWriterMap.writeSuccess(fileInfoStr);
                if (iOssFileProcessor != null) {
                    iOssFileProcessor.processFile(fileInfoStr);
                }
            }

            endMarker = fileListing == null ? null : fileListing.marker;
        } catch (QiniuException e) {
            retryCount--;
            if (retryCount > 0) {
                System.out.println(e.getMessage() + ", last " + retryCount + " times retry...");
                endMarker = doFileList(bucket, prefix, delimiter, marker, limit, endFile, iOssFileProcessor, retryCount);
            } else {
                targetFileReaderAndWriterMap.writeErrorAndNull(bucket + "\t" + prefix + "\t" + delimiter + "\t" + marker
                        + "\t" + limit + "\t" + "{\"msg\":\"" + e.error() + "\"}");
            }
        } catch (QiniuSuitsException e) {
            targetFileReaderAndWriterMap.writeErrorAndNull(bucket + "\t" + prefix + "\t" + delimiter + "\t" + marker
                    + "\t" + limit + "\t" + e.getMessage());
        }

        return endMarker;
    }

    /*
    迭代器方式列举带 prefix 前缀的所有文件，直到列举完毕，limit 为单次列举的文件个数
     */
    public void doFileIteratorList(String bucket, String prefix, String endFile, int limit, IOssFileProcess iOssFileProcessor) {

        FileListIterator fileListIterator = bucketManager.createFileListIterator(bucket, prefix, limit, null);

        loop:while (fileListIterator.hasNext()) {
            FileInfo[] items = fileListIterator.next();

            for (FileInfo fileInfo : items) {
                if (fileInfo.key.equals(endFile)) {
                    break loop;
                }
                targetFileReaderAndWriterMap.writeSuccess(JSONConvertUtils.toJson(fileInfo));
                if (iOssFileProcessor != null) {
                    iOssFileProcessor.processFile(JSONConvertUtils.toJson(fileInfo));
                }
            }
        }
    }

    /*
    v2 的 list 接口，接收到响应后通过 java8 的流来处理响应的文本流。
     */
    public String doFileListV2(String bucket, String prefix, String delimiter, String marker, int limit, String endFile,
                               IOssFileProcess iOssFileProcessor, boolean withParallel, int retryCount) {

        Response response = null;
        InputStream inputStream;
        Reader reader;
        BufferedReader bufferedReader;
        AtomicBoolean endFlag = new AtomicBoolean(false);
        AtomicReference<String> endMarker = new AtomicReference<>();

        try {
            response = listV2(bucket, prefix, delimiter, marker, limit, retryCount);
            inputStream = new BufferedInputStream(response.bodyStream());
            reader = new InputStreamReader(inputStream);
            bufferedReader = new BufferedReader(reader);
            Stream<String> lineStream = withParallel ? bufferedReader.lines().parallel() : bufferedReader.lines();
            lineStream.forEach(line -> {
                        try {
                            String fileInfoStr = getFileInfoV2AndMarker(bucket, line)[0];
                            if (endFile.equals(JSONConvertUtils.toJson(fileInfoStr).get("key").getAsString())) {
                                endFlag.set(true);
                                endMarker.set(null);
                            }
                            if (!endFlag.get()) {
                                targetFileReaderAndWriterMap.writeSuccess(fileInfoStr);
                                iOssFileProcessor.processFile(fileInfoStr);
                                endMarker.set(JSONConvertUtils.toJson(line).get("marker").getAsString());
                            }
                        } catch (QiniuException e) {
                            targetFileReaderAndWriterMap.writeErrorAndNull(bucket + "\t" + prefix + "\t" + delimiter + "\t" + marker
                                    + "\t" + limit + "\t" + line + "\t" + "{\"msg\":\"" + e.error() + "\"}");
                        } catch (QiniuSuitsException e) {
                            targetFileReaderAndWriterMap.writeErrorAndNull(bucket + "\t" + prefix + "\t" + delimiter + "\t" + marker
                                    + "\t" + limit + "\t" + line + "\t" + e.getMessage());
                        }
                    });
            inputStream.close();
            reader.close();
            bufferedReader.close();
        } catch (IOException e) {
            targetFileReaderAndWriterMap.writeOther(bucket + "\t" + prefix + "\t" + delimiter + "\t" + marker
                    + "\t" + limit + "\t" + "{\"mdg\":\"io error\"}");
        } finally {
            if (response != null) {
                response.close();
            }
        }

        return endMarker.get();
    }

    /*
    v2 的 list 接口，通过文本流的方式返回文件信息。
     */
    public Response listV2(String bucket, String prefix, String delimiter, String marker, int limit, int retryCount) {

        String prefixParam = StringUtils.isNullOrEmpty(prefix) ? "" : "&prefix=" + prefix;
        String delimiterParam = StringUtils.isNullOrEmpty(delimiter) ? "" : "&delimiter=" + delimiter;
        String limitParam = limit == 0 ? "" : "&limit=" + limit;
        String markerParam = StringUtils.isNullOrEmpty(marker) ? "" : "&marker=" + marker;
        String url = "http://rsf.qbox.me/v2/list?bucket=" + bucket + prefixParam + delimiterParam + limitParam + markerParam;
        String authorization = "QBox " + auth.signRequest(url, null, null);
        StringMap headers = new StringMap().put("Authorization", authorization);
        Response response = null;

        try {
            response = client.post(url, null, headers, null);
        } catch (QiniuException e) {
            retryCount--;
            if (retryCount > 0) {
                System.out.println(e.getMessage() + ", last " + retryCount + " times retry...");
                response = listV2(bucket, prefix, delimiter, marker, limit, retryCount);
            } else {
                targetFileReaderAndWriterMap.writeErrorAndNull(bucket + "\t" + prefix + "\t" + delimiter + "\t" + marker
                        + "\t" + limit + "\t" + "{\"msg\":\"" + e.error() + "\"}");
            }
        }

        return response;
    }

    public void checkFileListing(FileListing fileListing) throws QiniuSuitsException {
        if (fileListing == null)
            throw new QiniuSuitsException("line is empty");
    }

    public String[] getFirstFileInfoAndMarker(String bucket, FileListing fileListing, int index) throws QiniuSuitsException, QiniuException {
        checkFileListing(fileListing);
        FileInfo[] items = fileListing.items;
        String marker = fileListing.marker;
        String fileInfoStr = items.length > 0 ? JSONConvertUtils.toJson(items[index]) : getFileInfoByMarker(bucket, marker);
        String retMarker = items.length < 1000 ? null : marker;

        return new String[]{fileInfoStr, retMarker};
    }

    public String getFileInfoByMarker(String bucket, String marker)throws QiniuSuitsException, QiniuException {
        String fileKey;
        String fileInfoStr;

        if (!StringUtils.isNullOrEmpty(marker)) {
            JsonObject decodedMarker = JSONConvertUtils.toJson(new String(UrlSafeBase64.decode(marker)));
            fileKey = decodedMarker.get("k").getAsString();
            fileInfoStr = JSONConvertUtils.toJson(bucketManager.stat(bucket, fileKey));
        } else
            throw new QiniuSuitsException("marker is empty");

        return fileInfoStr;
    }

    public String[] getFileInfoV2AndMarker(String bucket, String line) throws QiniuSuitsException, QiniuException {
        String fileKey;
        String fileInfoStr;
        String retMarker;

        if (StringUtils.isNullOrEmpty(line))
            throw new QiniuSuitsException("line is empty");
        JsonObject json = JSONConvertUtils.toJson(line);
        JsonElement jsonElement = json.get("item");
        if (jsonElement == null || "null".equals(jsonElement.toString())) {
            if (json.get("marker") != null && json.get("marker").getAsString().equals("")) {
                retMarker = json.get("marker").getAsString();
                JsonObject decodedMarker = JSONConvertUtils.toJson(new String(UrlSafeBase64.decode(retMarker)));
                fileKey = decodedMarker.get("k").getAsString();
                fileInfoStr = JSONConvertUtils.toJson(bucketManager.stat(bucket, fileKey));
            } else
                throw new QiniuSuitsException("marker is empty");
        } else {
            fileInfoStr = JSONConvertUtils.toJson(json.getAsJsonObject("item"));
            retMarker = json.get("marker").getAsString();
        }

        return new String[]{fileInfoStr, retMarker};
    }

    public void closeBucketManager() {
        if (bucketManager != null)
            bucketManager.closeResponse();
    }
}