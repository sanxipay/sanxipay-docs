# 签名规则

::: tip 💡 签名测试工具
为了帮助开发者更好地理解和验证签名算法，我们提供了一个交互式的[签名测试工具](/signature-test-tool)，支持：
- 🚀 一键加载官方示例
- 📋 JSON数据导入
- 🔍 详细的签名生成步骤
- ✅ 实时验证签名正确性

[👉 立即使用签名测试工具](/signature-test-tool)
:::

## 协议规则
传输方式：采用HTTP传输(生产环境建议HTTPS)   
提交方式：`POST` 或 `GET`   
内容类型：`application/json`   
字符编码：`UTF-8`   
签名算法：`MD5`   

## 参数规范
交易金额：默认为人民币交易，单位为分，参数值不能带小数。   
时间参数：所有涉及时间参数均使用精确到毫秒的13位数值，如：1622016572190。时间戳具体是指从格林尼治时间1970年01月01日00时00分00秒起至现在的毫秒数。

## 签名算法
`签名生成的通用步骤如下`

***第一步：*** 设所有发送或者接收到的数据为集合M，将集合M内非空参数值的参数按照参数名ASCII码从小到大排序（字典序），使用URL键值对的格式（即key1=value1&amp;key2=value2…）拼接成字符串stringA。
特别注意以下重要规则：   
◆ 参数名ASCII码从小到大排序（字典序）；   
◆ 如果参数的值为空不参与签名；   
◆ 参数名区分大小写；   
◆ 验证调用返回或支付中心主动通知签名时，传送的sign参数不参与签名，将生成的签名与该sign值作校验。   
◆ 支付中心接口可能增加字段，验证签名时必须支持增加的扩展字段

***第二步：*** 在stringA最后拼接上key`[即 StringA +"&key=" + 私钥 ]` 得到stringSignTemp字符串，并对stringSignTemp进行MD5运算，再将得到的字符串所有字符转换为大写，得到sign值signValue。

如请求统一下单API参数如下：
```java
Map signMap = new HashMap<>();
signMap.put("mchNo", "M1748248715");
signMap.put("appId", "6834288be4b08df6d75dbd00");
signMap.put("mchOrderNo", "ORDER20250111-02");
signMap.put("wayCode", "WX_LITE");
signMap.put("amount", "1");
signMap.put("currency", "CNY");
signMap.put("clientIp", "192.168.1.1");
signMap.put("subject", "商品标题1");
signMap.put("body", "商品描述信息");
signMap.put("notifyUrl", "http://example.com/notify");
signMap.put("returnUrl", "http://example.com/return");
signMap.put("expiredTime", "3600");
signMap.put("channelExtra", "{\"Openid\": \"xxx\"}");
signMap.put("extParam", "{}");
signMap.put("divisionMode", "0");
signMap.put("reqTime", "20231019120000");
signMap.put("version", "1.0");
signMap.put("signType", "MD5");
```
`待签名值`： 
amount=1&amp;appId=6834288be4b08df6d75dbd00&amp;body=商品描述信息&amp;channelExtra={"Openid": "xxx"}&amp;clientIp=192.168.1.1&amp;currency=CNY&amp;divisionMode=0&amp;expiredTime=3600&amp;extParam={}&amp;mchNo=M1748248715&amp;mchOrderNo=ORDER20250111-02&amp;notifyUrl=http://example.com/notify&amp;reqTime=20231019120000&amp;returnUrl=http://example.com/return&amp;signType=MD5&amp;subject=商品标题1&amp;version=1.0&amp;wayCode=WX_LITE&amp;key=your_private_key_here

`签名结果`：根据上述参数计算得出

`最终请求支付系统参数`：包含所有上述参数及计算出的sign值

&gt; 运营管理平台可以管理商户的私钥