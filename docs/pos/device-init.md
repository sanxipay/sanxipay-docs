#### 接口描述：

- 设备初始化接口。此接口返回merchantId字段，请求其他接口时，需附带merchantId字段。

#### 接口版本：

|版本号|制定人|制定日期|修订日期| 备注   |
|:----    |:---|:----- |-----   |-----   |


#### 请求URL:

- 接口URL: /api/pos/deviceInit/{version}

#### 请求方式：

- POST

#### 请求参数:

无

#### 返回数据说明:

|参数名|说明|
|:----    |:---  |
|merchantName | 商户名称   |
|merchantId |  商户号   |


#### 请求示例:

```
请求参数：
{
	"requestTime": 1650010509,
	"merchantId": "",
	"appId": "5c93bb17952842e1afde1ad5ff461b18",
	"sign": "1e3b727bd8fe8fd75f02a30dd25cf5c5",
	"productNum": "123456",
	"deviceId": "SANXIPAY666"
}
```

待加签字符串：2fbgmc9otniikpc0mgmuav7s25lfpr5m9d0z8aqy0ylu86sex7w7cj11nqn6p3buxvkoh5eyxygbrbny158spnunx0d1ub75ii5lr72ipx7jhew67fh8gmq08tylwj545c93bb17952842e1afde1ad5ff461b18SANXIPAY6661234561650010509

签名结果：1e3b727bd8fe8fd75f02a30dd25cf5c5


#### 返回示例:

```
{
    "result": {
        "merchantId": "M1648869277",
        "merchantName": "丁_商户B1"
    },
    "code": "00000",
    "message": "success"
}
```

#### 备注:

-