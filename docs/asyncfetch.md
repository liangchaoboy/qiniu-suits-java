# 资源异步抓取

# 简介
对文件列表进行异步抓取保存到目标空间。参考：[七牛异步第三方资源抓取](https://developer.qiniu.com/kodo/api/4097/asynch-fetch)  

### 配置文件选项

#### 必须参数
|数据源方式|参数及赋值|  
|--------|-----|  
|source-type=list（空间资源列举）|[list 数据源参数](listbucket.md) <br> process=asyncfetch <br> to-bucket=\<bucket\> |
|source-type=file（文件资源列表）|[file 数据源参数](fileinput.md) <br> process=asyncfetch <br> ak=\<ak\> <br> sk=\<sk\> <br> to-bucket=\<bucket\> |

#### 可选参数
```
add-prefix=video/
file-type=
ignore-same-key=
url-index=0
md5-index=2
domain=
https=
private=false
hash-check=
host=
callback-url=
callback-body=
callback-body-type=
callback-host=
```

### 参数字段说明
|参数名|参数值及类型 | 含义|  
|-----|-------|-----|  
|process| 异步抓取时设置为asyncfetch | 表示异步 fetch 操作|  
|ak、sk|长度 40 的字符串|七牛账号的ak、sk，通过七牛控制台个人中心获取，当数据源方式为 list 时无需再设置|  
|to-bucket|字符串| 保存抓取结果的空间名|  
|add-prefix| 字符串| 表示为保存的文件名添加指定前缀|  
|ignore-same-key| true/false| 暂未启用|  
|url-index| 字符串| 通过 url 操作时需要设置的 url 索引（下标），需要手动指定才会进行解析|  
|md5-index| 字符串| 资源 md5 值索引（下标），需要手动指定才会进行解析|  
|domain| 域名字符串| 当数据源数据的资源为文件名列表时，需要设置进行访问的域名，当数据源方式为 file 且指定 url-index 时无需设置|  
|https| true/false| 是否使用 https 抓取资源（默认否）|  
|private| true/false| 资源域名是否是七牛私有空间的域名（默认否）|  
|hash-check| true/false| 抓取结果是否进行 hash 值校验（默认否）|  
|host| host 字符串| 抓取源资源时指定 host|  
|callback-url| 公网可访问的 url 字符串| 设置回调地址|  
|callback-body| body 字符串| 设置回调 body|  
|callback-body-type| body-type 字符串| 设置回调 body 类型|  
|callback-host| host 字符串| 设置回调 host |  

#### 关于 url-index 和 md5-index
当 parse-type=table 时下标必须为整数。url-index 表示输入行中存在 url 形式的源文件地址，未设置的情况下则默认从 key 字段加上 domain 的方式访
问源文件地址，md5-index 为需要进行 md5 校验时输入 md5 值的字段下标，不设置则无效。  

### 命令行参数方式
```
-process=asyncfetch -process-batch=true -ak= -sk= -to-bucket=bucket2 -add-prefix=video/ -file-type= -ignore-same-key= -domain= -https= -private= -hash-check= -host= -callback-url= -callback-body= -callback-body-type= -callback-host=
```

