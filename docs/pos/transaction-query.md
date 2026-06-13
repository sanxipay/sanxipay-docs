#### 接口描述：

- 

#### 接口版本：

|版本号|制定人|制定日期|修订日期| 备注   |
|:----    |:---|:----- |-----   |-----   |


#### 请求URL:

- 接口URL: /api/pos/payList/{version}

#### 请求方式：

- POST

#### 请求参数:

|参数名|说明|
|:----    |:---  |
|pageNumber |  页码  |
|pageSize |  每页条数  |
|queryDate |  日期（yyyy-MM-dd）或月份（yyyy-MM）  |

#### 返回数据 result 说明:
|参数名|说明|
|:----    |:---  |
|total| 总条数 |
|current| 当前页码 |
|records| 交易记录，字段见下方表格 |
|hasNext| 下一页是否仍有记录 |

#### records 说明:
|参数名|说明|
|:----    |:---  |
|payOrderId | 订单号   |
|state | 支付状态: 2-支付成功, 5-已退款   |
|wayCodeType |  支付方式：WECHAT-微信, ALIPAY-支付宝, YSFPAY-云闪付, UNIONPAY-银联, OTHER-其他    |
|amount |  收款金额，**单位分**   |
|refundAmount |  退款金额，**单位分**   |
|createdAt |  交易时间   |

#### 请求示例:

```
请求参数：
{
	"requestTime": 1650017143,
	"data": "{\"pageNumber\":\"1\",\"queryDate\":\"2022-04\",\"pageSize\":\"10\"}",
	"merchantId": "M1648869277",
	"appId": "5c93bb17952842e1afde1ad5ff461b18",
	"sign": "efb4236db46a899eb65f99aaeebb5fff",
	"productNum": "123456",
	"deviceId": "SANXIPAY666"
}

```

待加签字符串：2fbgmc9otniikpc0mgmuav7s25lfpr5m9d0z8aqy0ylu86sex7w7cj11nqn6p3buxvkoh5eyxygbrbny158spnunx0d1ub75ii5lr72ipx7jhew67fh8gmq08tylwj545c93bb17952842e1afde1ad5ff461b18{"pageNumber":"1","queryDate":"2022-04","pageSize":"10"}SANXIPAY666M16488692771234561650017143

签名结果：efb4236db46a899eb65f99aaeebb5fff

#### 返回示例:

```
{
    "result": {
        "total": 49,
        "current": 1,
        "records": [
            {
                "amount": 2,
                "createdAt": "2022-04-15 17:16:09",
                "payOrderId": "P1514895343050981378",
                "refundAmount": 0,
                "state": 2,
                "wayCodeType": "WECHAT"
            },
            {
                "amount": 2,
                "createdAt": "2022-04-15 11:58:55",
                "payOrderId": "P1514815507372683266",
                "refundAmount": 2,
                "state": 5,
                "wayCodeType": "WECHAT"
            }
        ],
        "hasNext": true
    },
    "code": "00000",
    "message": "success"
}
```

#### 备注:

-