# 资源（音视频文件）查询元信息操作

# 简介
对空间中的音视频资源查询 avinfo 元信息。参考：[七牛音视频元信息查询](https://developer.qiniu.com/dora/manual/1247/audio-and-video-metadata-information-avinfo)  

### 配置文件选项

#### 必须参数
|数据源方式|参数及赋值|
|--------|-----|
|source-type=list（空间资源列举）|[list 数据源参数](listbucket.md) <br> process=avinfo <br> domain=\<domain> |  
|source-type=file（文件资源列表）|[file 数据源参数](fileinput.md) <br> process=avinfo <br> [domain=\<domain>] |  

#### 可选参数
```
url-index=0
domain=
https=
private=false
ak=
sk=
```

### 参数字段说明
|参数名|参数值及类型 | 含义|  
|-----|-------|-----|  
|process=avinfo| 查询avinfo时设置为avinfo| 表示查询 avinfo 操作|  
|url-index| 字符串| 通过 url 操作时需要设置的 url 索引（下标），需要手动指定才会进行解析|  
|domain| 域名字符串| 用于拼接文件名生成链接的域名，数据源为 file 且指定 url-index 时无需设置|  
|https| true/false| 设置 domain 的情况下可以选择是否使用 https 访问（默认否）|  
|private| true/false| 资源域名是否是七牛私有空间的域名（默认否）|  
|ak、sk| | private=true且source-type=file时必填（默认无）|  

#### 关于 url-index
当 parse-type=table 时下标必须为整数。url-index 表示输入行中存在 url 形式的源文件地址，未设置的情况下则默认从 key 字段加上 domain 的方式访
问源文件地址。  

### 命令行方式
```
-process=avinfo -domain= -https= -private= 
```
