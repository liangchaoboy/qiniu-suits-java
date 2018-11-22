package com.qiniu.service.media;

import com.google.gson.*;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Client;
import com.qiniu.http.Response;
import com.qiniu.model.media.*;
import com.qiniu.util.HttpResponseUtils;
import com.qiniu.util.JsonConvertUtils;

public class MediaManager {

    private Client client;
    private Avinfo avinfo;
    private int retryCount = 3;

    public MediaManager() {
        this.client = new Client();
        this.avinfo = new Avinfo();
    }

    public Avinfo getAvinfo(String url) throws QiniuException {

        String[] addr = url.split("/");
        if (addr.length < 3) throw new QiniuException(null, "not valid url.");
        String domain = addr[2];
        StringBuilder key = new StringBuilder();
        for (int i = 3; i < addr.length; i++) {
            key.append(addr[i]);
        }
        return getAvinfo(domain, key.toString());
    }

    public Avinfo getAvinfo(String domain, String sourceKey) throws QiniuException {

        String url = "http://" + domain + "/" + sourceKey.split("\\?")[0];
        JsonObject jsonObject = requestAvinfo(url);
        this.avinfo.setFormat(JsonConvertUtils.fromJson(jsonObject.getAsJsonObject("format"), Format.class));
        JsonElement element = jsonObject.get("streams");
        JsonArray streams = element.getAsJsonArray();
        for (JsonElement stream : streams) {
            JsonElement typeElement = stream.getAsJsonObject().get("codec_type");
            String type = (typeElement == null || typeElement instanceof JsonNull) ? "" : typeElement.getAsString();
            if ("video".equals(type)) this.avinfo.setVideoStream(JsonConvertUtils.fromJson(stream, VideoStream.class));
            if ("audio".equals(type)) this.avinfo.setAudioStream(JsonConvertUtils.fromJson(stream, AudioStream.class));
        }
        return this.avinfo;
    }

    private JsonObject requestAvinfo(String url) throws QiniuException {

        Response response = null;
        try {
            response = client.get(url + "?avinfo");
        } catch (QiniuException e) {
            HttpResponseUtils.checkRetryCount(e, retryCount);
            while (retryCount > 0) {
                try {
                    response = client.get(url + "?avinfo");
                    retryCount = 0;
                } catch (QiniuException e1) {
                    retryCount = HttpResponseUtils.getNextRetryCount(e1, retryCount);
                }
            }
        }

        if (response == null) throw new QiniuException(null, "no response.");
        JsonObject bodyJson = JsonConvertUtils.toJsonObject(response.bodyString());
        JsonElement jsonElement = bodyJson.get("format");
        if (jsonElement == null || jsonElement instanceof JsonNull) {
            throw new QiniuException(response);
        }

        return bodyJson;
    }
}