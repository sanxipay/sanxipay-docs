#### 接口描述：

- 

#### 接口版本：

|版本号|制定人|制定日期|修订日期| 备注   |
|:----    |:---|:----- |-----   |-----   |


#### 请求URL:

- 接口URL: /api/pos/statictics/{version}

#### 请求方式：

- POST

#### 请求参数:

|参数名|说明|
|:----    |:---  |
|queryDate |  日期（yyyy-MM-dd）或月份（yyyy-MM）  |

#### 返回数据说明:

|参数名|说明|
|:----    |:---  |
|totalPayCount | 支付笔数(不含退款)   |
|totalPayAmount |  支付金额(不含退款)，**单位分**    |
|totalRefundCount |  退款笔数   |
|totalRefundAmount |  退款金额，**单位分**   |

#### 请求示例:

```
请求参数：
{
	"requestTime": 1650017336,
	"data": "{\"queryDate\":\"2022-04-11\"}",
	"merchantId": "M1648869277",
	"appId": "5c93bb17952842e1afde1ad5ff461b18",
	"sign": "14f62d94472da721a9f38f349990fbf6",
	"productNum": "123456",
	"deviceId": "SANXIPAY666"
}

```

待加签字符串：2fbgmc9otniikpc0mgmuav7s25lfpr5m9d0z8aqy0ylu86sex7w7cj11nqn6p3buxvkoh5eyxygbrbny158spnunx0d1ub75ii5lr72ipx7jhew67fh8gmq08tylwj545c93bb17952842e1afde1ad5ff461b18{"queryDate":"2022-04-11"}SANXIPAY666M16488692771234561650017336

签名结果：14f62d94472da721a9f38f349990fbf6


#### 返回示例:

```
{
    "result": {
        "totalRefundCount": 1,
        "totalPayCount": 0,
        "totalPayAmount": 0,
        "totalRefundAmount": 1
    },
    "code": "00000",
    "message": "success"
}
```

#### 备注:

-