package com.qiniu.service.datasource;

import com.qiniu.common.QiniuException;
import com.qiniu.persistence.FileMap;
import com.qiniu.service.convert.MapToString;
import com.qiniu.service.convert.LineToMap;
import com.qiniu.service.interfaces.ILineProcess;
import com.qiniu.service.interfaces.ITypeConvert;
import com.qiniu.util.HttpResponseUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Map.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileInput implements IDataSource {

    final private String filePath;
    final private String parseType;
    final private String separator;
    final private Map<String, String> indexMap;
    final private int unitLen;
    final private String resultPath;
    private boolean saveTotal;
    private String resultFormat;
    private String resultSeparator;
    private List<String> rmFields;

    public FileInput(String filePath, String parseType, String separator, Map<String, String> indexMap, int unitLen,
                     String resultPath) {
        this.filePath = filePath;
        this.parseType = parseType;
        this.separator = separator;
        this.indexMap = indexMap;
        this.unitLen = unitLen;
        this.resultPath = resultPath;
        this.saveTotal = false;
    }

    public void setResultSaveOptions(boolean saveTotal, String format, String separator, List<String> removeFields) {
        this.saveTotal = saveTotal;
        this.resultFormat = format;
        this.resultSeparator = separator;
        this.rmFields = removeFields;
    }

    private void traverseByReader(BufferedReader reader, FileMap fileMap, ILineProcess<Map<String, String>> processor)
            throws IOException {
        ITypeConvert<String, Map<String, String>> typeConverter = new LineToMap(parseType, separator, indexMap);
        ITypeConvert<Map<String, String>, String> writeTypeConverter = new MapToString(resultFormat,
                resultSeparator, rmFields);
        List<String> srcList = new ArrayList<>();
        List<Map<String, String>> infoMapList;
        List<String> writeList;
        String line = null;
        boolean goon = true;
        while (goon) {
            // 避免文件过大，行数过多，使用 lines() 的 stream 方式直接转换可能会导致内存泄漏，故使用 readLine() 的方式
            try { line = reader.readLine(); } catch (IOException e) { e.printStackTrace(); }
            if (line == null) goon = false;
            else srcList.add(line);
            if (srcList.size() >= unitLen || line == null) {
                infoMapList = typeConverter.convertToVList(srcList);
                if (typeConverter.getErrorList().size() > 0)
                    fileMap.writeError(String.join("\n", typeConverter.consumeErrorList()));
                if (saveTotal) {
                    writeList = writeTypeConverter.convertToVList(infoMapList);
                    if (writeList.size() > 0) fileMap.writeSuccess(String.join("\n", writeList));
                }
                // 如果抛出异常需要检测下异常是否是可继续的异常，如果是程序可继续的异常，忽略当前异常保持数据源读取过程继续进行
                try {
                    if (processor != null) processor.processLine(infoMapList);
                } catch (QiniuException e) {
                    HttpResponseUtils.processException(e, 1, null, null);
                }
                srcList = new ArrayList<>();
            }
        }
    }

    private FileMap getSourceFileMap() throws IOException {
        FileMap inputFileMap = new FileMap();
        File sourceFile = new File(filePath);
        if (sourceFile.isDirectory()) {
            inputFileMap.initReaders(filePath);
        } else {
            inputFileMap.initReader(sourceFile.getParent(), sourceFile.getName());
        }
        return inputFileMap;
    }

    public void export(Entry<String, BufferedReader> readerEntry, ILineProcess<Map<String, String>> processor)
            throws Exception {
        FileMap recordFileMap = new FileMap(resultPath);
        FileMap fileMap = new FileMap(resultPath, "fileinput", readerEntry.getKey());
        fileMap.initDefaultWriters();
        if (processor != null) processor.setResultTag(readerEntry.getKey());
        ILineProcess<Map<String, String>> lineProcessor = processor == null ? null : processor.clone();
        String record = "order: " + readerEntry.getKey();
        String next;
        try {
            traverseByReader(readerEntry.getValue(), fileMap, lineProcessor);
            next = readerEntry.getValue().readLine();
            if (next == null) record += "\tsuccessfully done";
            else record += "\tnextLine:" + next;
            System.out.println(record);
        } catch (IOException e) {
            try { next = readerEntry.getValue().readLine(); } catch (IOException ex) { next = ex.getMessage(); }
            record += "\tnextLine:" + next + "\t" + e.getMessage().replaceAll("\n", "\t");
            e.printStackTrace();
            throw e;
        } finally {
            try { recordFileMap.writeKeyFile("result", record); } catch (IOException e) { e.printStackTrace(); }
            fileMap.closeWriters();
            recordFileMap.closeWriters();
            if (lineProcessor != null) lineProcessor.closeResource();
        }
    }

    synchronized private void exit(AtomicBoolean exit, Exception e) {
        if (!exit.get()) e.printStackTrace();
        exit.set(true);
        System.exit(-1);
    }

    public void export(int threads, ILineProcess<Map<String, String>> processor) throws Exception {
        FileMap inputFileMap = getSourceFileMap();
        Set<Entry<String, BufferedReader>> readerEntrySet = inputFileMap.getReaderMap().entrySet();
        int listSize = readerEntrySet.size();
        int runningThreads = listSize < threads ? listSize : threads;
        String info = "read files" + (processor == null ? "" : " and " + processor.getProcessName());
        System.out.println(info + " concurrently running with " + runningThreads + " threads ...");
        ExecutorService executorPool = Executors.newFixedThreadPool(runningThreads);
        AtomicBoolean exit = new AtomicBoolean(false);
        for (Entry<String, BufferedReader> readerEntry : readerEntrySet) {
            executorPool.execute(() -> {
                try {
                    export(readerEntry, processor);
                } catch (Exception e) {
                    exit(exit, e);
                }
            });
        }
        executorPool.shutdown();
        while (!executorPool.isTerminated()) Thread.sleep(1000);
        inputFileMap.closeReaders();
        System.out.println(info + " finished");
    }
}
