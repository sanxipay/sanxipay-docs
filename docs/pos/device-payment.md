#### 接口描述：

- 付款码支付接口

#### 接口版本：

|版本号|制定人|制定日期|修订日期| 备注   |
|:----    |:---|:----- |-----   |-----   |


#### 请求URL:

- 接口URL: /api/pos/pay/{version}

#### 请求方式：

- POST

#### 请求参数:

|参数名|说明|
|:----    |:---  |
|qrCode | 用户付款码值   |
|payType |  1支付宝，2微信，3云闪付   |
|payAmount | 支付金额，单位（元）2位小数   |
|deviceOrderNum |  设备机器上的订单号   |

#### 返回数据说明:

|参数名|说明|
|:----    |:---  |
|cxOrderNum | 系统订单号   |
|payAmount |  支付金额，单位（元）2位小数    |
|payStatus |  支付状态（0待支付，5支付中，10支付完成，15支付失败），订单返回支付中时，需轮询调用支付订单查询接口，查询订单最终状态   |
|receiveAmount | 实收金额，单位（元）2位小数    |
|payableAmount |  实付金额，单位（元）2位小数    |

#### 请求示例:

```
请求参数：
{
	"requestTime": 1650013587,
	"data": "{\"payType\":1,\"payAmount\":\"0.01\",\"qrCode\":\"286120303995188105\",\"deviceOrderNum\":\"A999999\"}",
	"merchantId": "M1648869277",
	"appId": "5c93bb17952842e1afde1ad5ff461b18",
	"sign": "64dda70fb10699f528c87a4c56a059b3",
	"productNum": "123456",
	"deviceId": "SANXIPAY666"
}
```

待加签字符串：2fbgmc9otniikpc0mgmuav7s25lfpr5m9d0z8aqy0ylu86sex7w7cj11nqn6p3buxvkoh5eyxygbrbny158spnunx0d1ub75ii5lr72ipx7jhew67fh8gmq08tylwj545c93bb17952842e1afde1ad5ff461b18{"payType":1,"payAmount":"0.01","qrCode":"286120303995188105","deviceOrderNum":"A999999"}SANXIPAY666M16488692771234561650013587

签名结果：64dda70fb10699f528c87a4c56a059b3


#### 返回示例:

```
{
    "result": {
        "receiveAmount": "0.01",
        "payAmount": "0.01",
        "cxOrderNum": "P1514893239079043074",
        "payStatus": 10,
        "payableAmount": "0.01"
    },
    "code": "00000",
    "message": "success"
}
```

#### 备注:

-