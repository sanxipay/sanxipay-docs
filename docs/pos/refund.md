#### 接口描述：

- 

#### 接口版本：

|版本号|制定人|制定日期|修订日期| 备注   |
|:----    |:---|:----- |-----   |-----   |


#### 请求URL:

- 接口URL: /api/pos/refund/{version}

#### 请求方式：

- POST

#### 请求参数:

|参数名|说明|
|:----    |:---  |
|cxOrderNum |  订单号，支付接口返回   |
|refundPwd |  退款密码   |
|refundAmount |  退款金额，非必填，默认为订单可退金额，单位（元）2位小数   |

#### 返回数据说明:

|参数名|说明|
|:----    |:---  |
|cxOrderNum | 订单号   |
|refundOrderNum | 退款单号   |
|refundAmount |  退款金额，单位（元）2位小数    |
|refundStatus |  退款状态:0-订单生成,1-退款中,2-退款成功,3-退款失败,4-退款任务关闭；返回退款中状态，需调用退款订单查询接口，查询最终退款状态  |

#### 请求示例:

```
请求参数：
{
	"requestTime": 1650016642,
	"data": "{\"refundPwd\":\"123456\",\"cxOrderNum\":\"P1514894810143686657\"}",
	"merchantId": "M1648869277",
	"appId": "5c93bb17952842e1afde1ad5ff461b18",
	"sign": "168cbe1f39b8e797852feb5465a781d0",
	"productNum": "123456",
	"deviceId": "SANXIPAY666"
}
```

待加签字符串：2fbgmc9otniikpc0mgmuav7s25lfpr5m9d0z8aqy0ylu86sex7w7cj11nqn6p3buxvkoh5eyxygbrbny158spnunx0d1ub75ii5lr72ipx7jhew67fh8gmq08tylwj545c93bb17952842e1afde1ad5ff461b18{"refundPwd":"123456","cxOrderNum":"P1514894810143686657"}SANXIPAY666M16488692771234561650016642

签名结果：168cbe1f39b8e797852feb5465a781d0

#### 返回示例:

```
{
    "result": {
        "refundOrderNum": "R1514905832518746113",
        "refundStatus": 2,
        "cxOrderNum": "P1514894810143686657",
        "refundAmount": "0.01"
    },
    "code": "00000",
    "message": "success"
}
```

#### 备注:

-