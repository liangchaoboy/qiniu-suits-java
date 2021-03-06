package com.qiniu.model.parameter;

import com.qiniu.service.interfaces.IEntryParam;

import java.io.IOException;

public class QhashParams extends QossParams {

    private String domain;
    private String algorithm;
    private String https;
    private String needSign;

    public QhashParams(IEntryParam entryParam) throws Exception {
        super(entryParam);
        this.domain = entryParam.getParamValue("domain");
        try { this.algorithm = entryParam.getParamValue("algorithm"); } catch (Exception e) { algorithm = ""; }
        try { this.https = entryParam.getParamValue("https"); } catch (Exception e) { https = ""; }
        try { this.needSign = entryParam.getParamValue("private"); } catch (Exception e) { needSign = ""; }
    }

    public String getDomain() {
        return domain;
    }

    public String getAlgorithm() {
        if (algorithm.matches("(md5|sha1)")) {
            return algorithm;
        } else {
            return "md5";
        }
    }

    public String getProtocol() throws IOException {
        if ("".equals(https) || https.matches("false")) {
            return "http";
        } else if (https.matches("true")) {
            return "https";
        } else {
            throw new IOException("please set https as true/false.");
        }
    }

    public boolean getNeedSign() {
        if (needSign.matches("(true|false)")) {
            return Boolean.valueOf(needSign);
        } else {
            return false;
        }
    }
}
