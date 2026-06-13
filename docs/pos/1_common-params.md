## 接口描述：

下述参数中提到的ROOT_URL，appId，secretKey参数可通过如下两种方式得到：

方式1：由聚合支付系统侧线下提供，设备接入方将参数写入设备中使用
方式2：调用三希智付统一设备平台接口获取 [获取设备支付参数](https://docs.sanxipay.com/docs/sanxipay_api/sanxipay_api-1en8vqpr4k77m "获取设备支付参数")

`说明：`大部分使用方式2实现，如不想依赖三希智付统一设备平台可使用方式1实现。
三希智付统一设备平台是为了方便更多设备厂商接入三希智付聚合支付系统而实现，只提供设备参数统一获取，不参与支付。

## 接口版本：
版本 |日期 |描述 |作者
------- | ------- | ------- | -------

## 协议规则
传输方式：采用HTTP/HTTPS传输
提交方式：采用GET/POST 方式提交
字符编码：UTF-8

## 接口地址

ROO_URL：https://mch.s.sanxipay.com

`说明：`接口的完整地址为：ROOT_URL + 接口URL

## 公共参数
接口中的version默认1.0

| 参数名  | 说明  |
| ------------ | ------------ |
|  appId |  appId，请与对接人获取  |
|  deviceId |  设备编号，设备的IMEI等，请确保唯一性，调用接口前请将设备号告知对接人员  |
|  requestTime |  请求时间时间戳（单位：秒）  |
|  sign |  验签的sign，详见下方签名方式  |
|  merchantId |  商户号，在 “设备初始化” 接口获取，设备初始化接口此值传空即可  |
|  productNum |  产品编号，默认值：123456  |
|  data |  业务参数序列化后赋值给此字段，该字段为JSON字符串格式  |


## 返回参数
1. 服务端返回数据为json格式，参数说明：

|  参数 | 类型  | 是否必填  | 描述  | 示例值  |
| ------------ | ------------ | ------------ | ------------ | ------------ |
|  code | String  | 是  |  接口返回码，00000表示业务处理成功 |  00000 |
|  message  | String |  是  |  接口返回描述，业务处理失败时，返回错误信息 | success  |
|  data | Json  |  否 | 接口返回的数据  |  {}  |

返回数据参考：

```
{
  "code": "00000",
  "message": "SUCCESS",
  "deviceId": "xxxxx",
  "result": {}
}
```

## 安全规范

### 1、签名方式
secretKey：私钥

按照secretKey放在首位，然后把上面参数按照从低到高顺序排列，并转MD5后再转小写；拼接方式如：{secretKey}{appId}{data}{deviceId}{merchantId}{productNum}{requestTime}

签名例子：
```
{
    "appId": "appid1007005",
    "data": "{\"deviceOrderNum\":\"20210506180217050000\"}",
    "deviceId": "1",
    "requestTime": "1620295342",
    "sign": "6a158f0055c62f80f7767569fce6880e",
    "merchantId": "7005",
    "productNum": "1007005"
}

  MD5({secretKey}{appId}{data}{deviceId}{merchantId}{productNum}{requestTime})
= d2jHFhvryHJyAUKShEsPL53ZtLYvUX0Iappid1007005{"deviceOrderNum":"20210506180217050000"}1700510070051620295342
= 6a158f0055c62f80f7767569fce6880e
```

### 2、涉及隐私相关的使用HTTPS