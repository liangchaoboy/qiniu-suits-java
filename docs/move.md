# 资源移动

# 简介
对空间中的资源进行**移动**到另一目标空间。参考：[七牛空间资源移动](https://developer.qiniu.com/kodo/api/1288/move)/[批量移动](https://developer.qiniu.com/kodo/api/1250/batch)

### 配置文件选项

#### 必须参数
|数据源方式|参数及赋值|  
|--------|-----|  
|source-type=list（空间资源列举）|[list 数据源参数](listbucket.md) <br> process=move <br> to-bucket=\<to-bucket\> |  
|source-type=file（文件资源列表）|[file 数据源参数](fileinput.md) <br> process=move <br> ak=\<ak\> <br> sk=\<sk\> <br> bucket=\<bucket\> <br> to-bucket=\<to-bucket\> |  

#### 可选参数
```
ak=
sk=
bucket= 
add-prefix=
rm-prefix=
```

### 参数字段说明
|参数名|参数值及类型 | 含义|  
|-----|-------|-----|  
|process=move| 移动资源时设置为move| 表示移动操作|  
|ak、sk|长度40的字符串|七牛账号的ak、sk，通过七牛控制台个人中心获取，当数据源方式为 list 时无需再设置|  
|bucket| 字符串| 操作的资源原空间，当数据源为 list 时无需再设置|  
|to-bucket| 字符串| 移动资源保存的目标空间|  
|add-prefix| 字符串| 表示为保存的文件名添加指定前缀|  
|rm-prefix| 字符串| 表示将原文件名去除存在的指定前缀后作为 move 之后的文件名|  

### 命令行方式
```
-process=move -ak= -sk= -bucket= -to-bucket= -keep-key= -add-prefix=
```
