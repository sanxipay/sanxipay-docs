# Java 对接示例

::: tip ☕ 单文件 · 零第三方依赖 · 可直接运行
[点此下载完整源码 SanxiPayDemo.java](/SanxiPayDemo.java)（含逐行中文注释）。
仅使用 JDK 17+ 标准库，无需 Maven/Gradle，`javac` 编译即可运行。
覆盖：MD5 签名、统一下单、查询订单、关闭订单、统一退款、**异步通知接收端**（含验签 + 幂等演示）。
:::

## 快速开始

**第 1 步**：下载 [SanxiPayDemo.java](/SanxiPayDemo.java)，将文件顶部常量区的三个参数替换为平台分配的真实值：

```java
static final String MCH_NO     = "你的商户号mchNo";     // 形如 M1621873433953
static final String APP_ID     = "你的应用appId";       // 形如 60cc09bce4b0f1c0b83761c9
static final String APP_SECRET = "你的私钥appSecret";   // 运营平台商户应用中查看/重置
```

**第 2 步**：编译并运行（要求 JDK 17+）：

```bash
javac -encoding UTF-8 SanxiPayDemo.java
java SanxiPayDemo                 # 默认 QR_CASHIER 聚合收银台，下单 1 分钱
java SanxiPayDemo ALI_QR 100      # 指定支付方式与金额（分），如支付宝二维码 1.00 元
```

程序会依次执行「统一下单 → 查询订单」，每一步都完整打印**请求 JSON、响应 JSON、响应验签结果**，
可直接与[《支付接口》](/1_payment-api)的参数表逐字段比对。

**第 3 步**（联调异步通知时）：在公网可达的机器上启动内置的通知接收端，并把源码中
`NOTIFY_URL` 常量指向它：

```bash
java SanxiPayDemo notify-server 20250    # 通知地址形如 http://你的域名或IP:20250/pay/notify
```

## 核心实现要点

### 1. MD5 签名（对应[《签名规则》](/0_signature-rules)）

```java
// 取全部非空参数（sign 自身除外），按参数名字典序排序，拼成 key1=value1&key2=value2...
// 末尾拼接 "&key=商户私钥"，对整串做 MD5（UTF-8）并转大写
Map<String, Object> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
sorted.putAll(params);
// ... 拼接后：
return md5Upper(stringA + "key=" + secret);
```

排查签名失败时，把源码中 `DEBUG_SIGN` 设为 `true`，程序会打印**待签名串**（私钥自动脱敏），
与[签名测试工具](/signature-test-tool)的结果逐字比对即可定位差异。

### 2. 统一下单与支付参数

下单成功后重点关注返回的 `payDataType` 与 `payData` 两个字段——前者决定后者如何使用
（跳转链接 / 表单 / 二维码地址 / 小程序参数等，详见[《支付接口》](/1_payment-api)返回参数表）。
需要二维码图片时传渠道参数：

```java
// channelExtra 传 {"payDataType":"codeImgUrl"}，payData 即返回二维码图片地址
unifiedOrder("QR_CASHIER", 1L, "商品标题", "{\"payDataType\":\"codeImgUrl\"}");
```

### 3. 响应验签必须严格

平台响应中的 `sign` 是对 `data` 内全部非空字段的签名。示例默认开启严格模式
（`STRICT_RESP_VERIFY = true`）：**验签失败直接抛异常终止**——验签不过的数据不可信，
生产系统同样必须拒绝处理，绝不能"只打日志继续走"。

### 4. 异步通知的三道必做校验

支付/退款结果通知（`notifyUrl` 回调）默认以 `POST application/x-www-form-urlencoded` 投递。
示例内置的 `NotifyServer` 演示了商户侧**缺一不可**的三道校验：

| 校验 | 要点 |
|------|------|
| ① 验签 | 对全部非空参数（sign 除外）按签名算法计算并比对，失败必须拒绝 |
| ② 幂等 | 同一订单可能重复通知（平台重试机制），已处理过的直接应答 success，不能重复发货/记账 |
| ③ 业务核对 | `state==2` 才是支付成功；`amount` 必须与本地订单金额一致；`mchOrderNo` 必须存在于本地 |

全部通过后应答**小写字符串 `success`**（前后不能有空格和换行符）；否则平台会按
0/30/60/90/120/150 秒的频率重试通知（最多 6 次）。

### 5. 生产化注意

示例为对接演示，以下四处从简，商户系统必须自行加固：

1. 错误处理为演示级（HTTP 异常直接抛出、无重试/熔断），生产请按自身框架包装；
2. 验签失败必须拒绝处理（示例已默认严格，生产不得放宽）；
3. 回调处理必须做幂等 + 金额核对 + 状态判断（见上表）；
4. 查单轮询请用递增间隔（如 2/5/10/30 秒），不要使用固定短间隔轮询。

## 完整源码

> 也可直接[下载源文件](/SanxiPayDemo.java)。

<<< @/public/SanxiPayDemo.java{java}
