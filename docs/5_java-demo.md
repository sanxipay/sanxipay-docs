# Java 对接示例

::: tip ☕ 单文件 · 零第三方依赖 · 可直接运行
<a href="/SanxiPayDemo.java" download>点此下载完整源码 SanxiPayDemo.java</a>（含逐行中文注释）。
仅使用 JDK 17+ 标准库，无需 Maven/Gradle，`javac` 编译即可运行。
覆盖：MD5 签名、统一下单、支付查单、关闭订单、统一退款、退款查单、**支付/退款通知接收端**。
:::

## 快速开始

**第 1 步**：下载 <a href="/SanxiPayDemo.java" download>SanxiPayDemo.java</a>，通过环境变量提供平台分配的四个值。示例不会从源码常量或默认值读取真实凭据，缺少任一必填值会立即退出：

```bash
export SANXIPAY_GATEWAY='https://pay.sanxipay.com'
export SANXIPAY_MCH_NO='<平台分配的商户号>'
export SANXIPAY_APP_ID='<平台分配的应用ID>'
read -r -s SANXIPAY_APP_SECRET && export SANXIPAY_APP_SECRET
export SANXIPAY_NOTIFY_URL='https://你的域名/pay/notify'  # 可选；要接收异步通知时配置
```

**第 2 步**：编译并运行（要求 JDK 17+）：

```bash
javac -encoding UTF-8 SanxiPayDemo.java
java SanxiPayDemo                 # 默认 QR_CASHIER 聚合收银台，下单 1 分钱
java SanxiPayDemo ALI_QR 1        # 支付宝二维码，1 分；失败时默认直接报错
java SanxiPayDemo query <mchOrderNo>
java SanxiPayDemo refund <mchOrderNo> <mchRefundNo> <amountFen>
java SanxiPayDemo refund-query <mchRefundNo>
```

程序会依次执行「统一下单 → 查询订单」，每一步都完整打印**请求 JSON、响应 JSON、响应验签结果**，
可直接与[《支付接口》](/1_payment-api)的参数表逐字段比对。

**第 3 步**（联调异步通知时）：先显式登记本轮测试允许处理的支付单和退款单，再在公网可达的机器上启动接收端。生产系统应把这两张内存表替换为自己的订单数据库查询：

```bash
export SANXIPAY_EXPECTED_PAYMENTS='商户支付单号=金额分'
export SANXIPAY_EXPECTED_REFUNDS='商户退款单号=金额分'
java SanxiPayDemo notify-server 20250    # 通知地址形如 http://你的域名或IP:20250/pay/notify
```

直连渠道失败时默认 fail-fast，不会静默创建另一笔订单。只有明确接受聚合收银台兜底时才传
`--allow-qr-cashier-fallback`；兜底订单不能作为原直连渠道已经验收的证据。

## 核心实现要点

### 1. MD5 签名（对应[《签名规则》](/0_signature-rules)）

```java
// 取全部非空参数（sign 自身除外），构造完整 key=value& 片段
List<String> fragments = new ArrayList<>();
// ... fragments.add(key + "=" + value + "&");
fragments.sort(String.CASE_INSENSITIVE_ORDER);
String stringA = String.join("", fragments);
// 每个片段已带 &，因此直接拼接 key=商户私钥
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

### 4. 支付与退款通知必须分开核对、分开幂等

支付/退款结果通知（`notifyUrl` 回调）默认以 `POST application/x-www-form-urlencoded` 投递。
示例内置的 `NotifyServer` 演示了商户侧**缺一不可**的三道校验：

| 校验 | 要点 |
|------|------|
| ① 验签 | 对全部非空参数（sign 除外）按签名算法计算并比对，失败必须拒绝 |
| ② 业务核对 | 支付核对 `mchOrderNo + amount` 且仅接受 `state=2`；退款核对 `mchRefundNo + refundAmount`，接受 `state=2` 成功或 `state=3` 失败；未知订单与其他状态必须拒绝 |
| ③ 幂等 | 支付以 `payOrderId` 幂等，退款以 `refundOrderId` 幂等；两类通知不能共用 `payOrderId` 作为唯一键 |

全部通过后应答**小写字符串 `success`**（前后不能有空格和换行符）；否则平台会按
0/30/60/90/120/150 秒的频率重试通知（最多 6 次）。

### 5. 生产化注意

示例为对接演示，以下四处从简，商户系统必须自行加固：

1. 错误处理为演示级（HTTP 异常直接抛出、无重试/熔断），生产请按自身框架包装；
2. 验签失败必须拒绝处理（示例已默认严格，生产不得放宽）；
3. 回调处理必须区分支付/退款，并做各自的幂等、金额核对和状态判断（见上表）；
4. 查单轮询请用递增间隔（如 2/5/10/30 秒），不要使用固定短间隔轮询。

## 完整源码

> 也可直接<a href="/SanxiPayDemo.java" download>下载源文件</a>。

<<< @/public/SanxiPayDemo.java{java}
