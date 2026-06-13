#### 接口描述：

- 

#### 接口版本：

|版本号|制定人|制定日期|修订日期| 备注   |
|:----    |:---|:----- |-----   |-----   |


#### 请求URL:

- 接口URL: /api/pos/queryRefund/{version}

#### 请求方式：

- POST

#### 请求参数:

|参数名|说明|
|:----    |:---  |
|refundOrderNum | 退款单号，发起退款接口返回   |

#### 返回数据说明:

|参数名|说明|
|:----    |:---  |
|refundOrderNum | 退款单号   |
|refundAmount |  退款金额，单位（元）2位小数    |
|refundStatus |  退款状态:0-订单生成,1-退款中,2-退款成功,3-退款失败,4-退款任务关闭；  |
|createdAt |  退款时间    |

#### 请求示例:

```
请求参数：
{
	"requestTime": 1650016933,
	"data": "{\"refundOrderNum\":\"R1514905832518746113\"}",
	"merchantId": "M1648869277",
	"appId": "5c93bb17952842e1afde1ad5ff461b18",
	"sign": "b2f1e75b0c2572fd8bf6ff48deace653",
	"productNum": "123456",
	"deviceId": "SANXIPAY666"
}
```

待加签字符串：2fbgmc9otniikpc0mgmuav7s25lfpr5m9d0z8aqy0ylu86sex7w7cj11nqn6p3buxvkoh5eyxygbrbny158spnunx0d1ub75ii5lr72ipx7jhew67fh8gmq08tylwj545c93bb17952842e1afde1ad5ff461b18{"refundOrderNum":"R1514905832518746113"}SANXIPAY666M16488692771234561650016933

签名结果：b2f1e75b0c2572fd8bf6ff48deace653


#### 返回示例:

```
{
    "result": {
        "createdAt": "2022-04-15 17:57:50",
        "refundOrderNum": "R1514905832518746113",
        "refundStatus": 2,
        "refundAmount": "0.01"
    },
    "code": "00000",
    "message": "success"
}
```

#### 备注:

-