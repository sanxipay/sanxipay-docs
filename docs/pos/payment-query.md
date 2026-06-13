#### 接口描述：

- 

#### 接口版本：

|版本号|制定人|制定日期|修订日期| 备注   |
|:----    |:---|:----- |-----   |-----   |


#### 请求URL:

- 接口URL: /api/pos/orderDetail/{version}

#### 请求方式：

- POST

#### 请求参数:

|参数名|是否必填|说明|
|:----    |:---  |:---  |
|cxOrderNum |  是  | 订单号，支付接口返回   |
|queryEntryType |  否  | 查询订单入口类型，默认不传为payOrder类型。<br/>payOrder：使用支付订单号查询，一般指设备支付接口返回的cxOrderNum字段<br/>mchOrder：使用商户订单号查询，一般指设备支付接口的deviceOrderNum字段<br/>beforeRefund：扫码退款或输入订单号退款，一般指交易凭证上的商户单号  |

#### 返回数据说明:

|参数名|说明|
|:----    |:---  |
|cxOrderNum | 订单号   |
|payAmount |  支付金额，单位（元）2位小数    |
|payStatus |  支付状态（0待支付，5支付中，10支付完成，15支付失败）  |
|payType | 1支付宝，2微信，3云闪付    |
|merchantReceiveAmount |  商家实际收到的金额，单位（元）2位小数    |
|payableAmount |  实付金额，单位（元）2位小数    |
|createdAt |  交易时间    |

#### 请求示例:

```
请求参数：
{
	"requestTime": 1650014588,
	"data": "{\"cxOrderNum\":\"P1514893239079043074\"}",
	"merchantId": "M1648869277",
	"appId": "5c93bb17952842e1afde1ad5ff461b18",
	"sign": "ce1becaa2b6a18f9d0375902c0774176",
	"productNum": "123456",
	"deviceId": "SANXIPAY666"
}
```

待加签字符串：2fbgmc9otniikpc0mgmuav7s25lfpr5m9d0z8aqy0ylu86sex7w7cj11nqn6p3buxvkoh5eyxygbrbny158spnunx0d1ub75ii5lr72ipx7jhew67fh8gmq08tylwj545c93bb17952842e1afde1ad5ff461b18{"cxOrderNum":"P1514893239079043074"}SANXIPAY666M16488692771234561650014588

签名结果：ce1becaa2b6a18f9d0375902c0774176


#### 返回示例:

```
{
    "result": {
		"cxOrderNum": "P1514893239079043074",
        "payAmount": "0.01",
        "payType": 1,
        "payStatus": 10,
        "payableAmount": "0.01",
        "merchantReceiveAmount": "0.01",
		"createdAt": "2022-04-15 17:07:48",
    },
    "code": "00000",
    "message": "success"
}
```

#### 备注:

-