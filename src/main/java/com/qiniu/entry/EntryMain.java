package com.qiniu.entry;

import com.qiniu.common.Zone;
import com.qiniu.config.CommandArgs;
import com.qiniu.config.PropertyConfig;
import com.qiniu.model.parameter.CommonParams;
import com.qiniu.model.parameter.FileInputParams;
import com.qiniu.model.parameter.HttpParams;
import com.qiniu.model.parameter.ListBucketParams;
import com.qiniu.service.datasource.FileInput;
import com.qiniu.service.datasource.IDataSource;
import com.qiniu.service.datasource.ListBucket;
import com.qiniu.service.interfaces.IEntryParam;
import com.qiniu.service.interfaces.ILineProcess;
import com.qiniu.storage.Configuration;
import com.qiniu.util.Auth;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EntryMain {

    private static Configuration configuration;

    public static void main(String[] args) throws Exception {
        IEntryParam entryParam = getEntryParam(args);
        HttpParams httpParams = new HttpParams(entryParam);
        configuration = new Configuration(Zone.autoZone());
        configuration.connectTimeout = httpParams.getConnectTimeout();
        configuration.readTimeout = httpParams.getReadTimeout();
        configuration.writeTimeout = httpParams.getWriteTimeout();

        ILineProcess<Map<String, String>> processor = new ProcessorChoice(entryParam, configuration).getFileProcessor();
        CommonParams commonParams = new CommonParams(entryParam);
        IDataSource dataSource = getDataSource(entryParam, commonParams);
        int threads = commonParams.getThreads();
        if (dataSource != null) dataSource.export(threads, processor);
        if (processor != null) processor.closeResource();
    }

    private static IDataSource getDataSource(IEntryParam entryParam, CommonParams commonParams) throws IOException {
        String sourceType = entryParam.getParamValue("source-type");
        IDataSource dataSource = null;

        boolean saveTotal = commonParams.getSaveTotal();
        String resultFormat = commonParams.getResultFormat();
        String resultSeparator = commonParams.getResultSeparator();
        String resultPath = commonParams.getResultPath();
        int unitLen = commonParams.getUnitLen();
        List<String> removeFields = commonParams.getRmFields();
        if ("list".equals(sourceType)) {
            ListBucketParams listBucketParams = new ListBucketParams(entryParam);
            String accessKey = listBucketParams.getAccessKey();
            String secretKey = listBucketParams.getSecretKey();
            String bucket = listBucketParams.getBucket();
            String customPrefix = listBucketParams.getCustomPrefix();
            List<String> antiPrefix = listBucketParams.getAntiPrefix();
            Auth auth = Auth.create(accessKey, secretKey);
            dataSource = new ListBucket(auth, configuration, bucket, unitLen, customPrefix, antiPrefix, resultPath);
            dataSource.setResultSaveOptions(saveTotal, resultFormat, resultSeparator, removeFields);
        } else if ("file".equals(sourceType)) {
            FileInputParams fileInputParams = new FileInputParams(entryParam);
            String filePath = fileInputParams.getFilePath();
            String parseType = fileInputParams.getParseType();
            String separator = fileInputParams.getSeparator();
            Map<String, String> indexMap = fileInputParams.getIndexMap();
            String sourceFilePath = System.getProperty("user.dir") + System.getProperty("file.separator") + filePath;
            dataSource = new FileInput(sourceFilePath, parseType, separator, indexMap, unitLen, resultPath);
            dataSource.setResultSaveOptions(saveTotal, resultFormat, resultSeparator, removeFields);
        }

        return dataSource;
    }

    private static IEntryParam getEntryParam(String[] args) throws IOException {
        List<String> configFiles = new ArrayList<String>(){{
            add("resources/qiniu.properties");
            add("resources/.qiniu.properties");
        }};
        boolean paramFromConfig = true;
        if (args != null && args.length > 0) {
            if (args[0].startsWith("-config=")) configFiles.add(args[0].split("=")[1]);
            else paramFromConfig = false;
        }
        String configFilePath = null;
        if (paramFromConfig) {
            for (int i = configFiles.size() - 1; i >= 0; i--) {
                File file = new File(configFiles.get(i));
                if (file.exists()) {
                    configFilePath = configFiles.get(i);
                    break;
                }
            }
            if (configFilePath == null) throw new IOException("there is no config file detected.");
            else paramFromConfig = true;
        }

        return paramFromConfig ? new PropertyConfig(configFilePath) : new CommandArgs(args);
    }
}
