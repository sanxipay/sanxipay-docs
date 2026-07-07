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

```java
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * ============================================================================
 *  三希智付（SanxiPay）商户对接 Java 示例程序
 * ============================================================================
 *  依据对外 API 文档站 https://docs.sanxipay.com 编写，覆盖：
 *    - 《签名规则》 ：MD5 签名（参数名字典序排序 + "&key=私钥" 拼接 + MD5 转大写）
 *    - 《支付接口》 ：统一下单 / 查询订单 / 关闭订单
 *    - 《退款接口》 ：统一退款
 *    - 《支付通知》 ：异步通知（notifyUrl 回调）接收端，含验签 + 幂等去重演示
 *
 *  特点：单文件、零第三方依赖，仅使用 JDK 标准库（Java 17+ 可直接 javac 编译）。
 *
 *  编译：javac SanxiPayDemo.java
 *  运行：java SanxiPayDemo [wayCode] [amount(分)]
 *        例：java SanxiPayDemo                # 默认 QR_CASHIER（聚合扫码），金额 1 分
 *            java SanxiPayDemo ALI_QR 100    # 支付宝二维码，1.00 元
 *        回调接收端（另开进程/机器，公网可达后把 NOTIFY_URL 指向它）：
 *            java SanxiPayDemo notify-server [端口]   # 默认 20250，路径任意（如 /pay/notify）
 *
 *  运行前请先将下方常量区 MCH_NO / APP_ID / APP_SECRET 替换为平台分配的真实参数。
 *
 *  异步通知要点（notifyUrl 回调，默认 POST application/x-www-form-urlencoded）：
 *        商户系统处理成功后必须返回小写字符串 success（前后不能有空格和换行符），
 *        否则支付中心会按 0/30/60/90/120/150 秒的频率重试通知（最多 6 次）。
 *        接收端范例见文件末尾 NotifyServer 内部类。
 *
 *  ⚠️ 生产化注意（本 Demo 为对接演示，以下从简处，商户系统必须自行加固）：
 *    1. 错误处理为演示级：HTTP 异常直接抛出、无重试/熔断/超时退避 —— 生产请按自身框架包装；
 *    2. 响应验签失败本 Demo 默认直接抛异常终止（STRICT_RESP_VERIFY）—— 生产同样必须拒绝
 *       处理验签失败的响应/通知，绝不能"只打日志继续走"；
 *    3. 回调必须做幂等（同一订单可能重复通知）+ 金额核对（通知 amount 与本地订单一致）
 *       + 状态判断（state==2 才是支付成功）—— 三者缺一不可，详见 NotifyServer 注释；
 *    4. 查单轮询请用递增间隔（如 2/5/10/30s），不要照抄本 Demo 的固定 sleep。
 * ============================================================================
 */
public class SanxiPayDemo {

    /* ==================== 常量区（对接前必须替换） ==================== */

    /** 支付网关地址（文档：各接口请求 URL 的公共前缀） */
    static final String GATEWAY = "https://pay.sanxipay.com";

    /** 商户号：平台分配，形如 M1621873433953 —— 请替换 */
    static final String MCH_NO = "你的商户号mchNo";

    /** 应用ID：平台分配，形如 60cc09bce4b0f1c0b83761c9 —— 请替换 */
    static final String APP_ID = "你的应用appId";

    /** 商户私钥（文档《签名规则》：运营管理平台可以管理商户的私钥）—— 请替换 */
    static final String APP_SECRET = "你的私钥appSecret";

    /** 客户端 IPV4（可选参数 clientIp 的演示取值，实际请传真实用户 IP） */
    static final String CLIENT_IP = "127.0.0.1";

    /**
     * 支付/退款结果异步通知地址（可选。文档：只有传了该值才会发起回调；留空则不回调）。
     * 联调回调时：先 `java SanxiPayDemo notify-server 20250` 启动本文件自带的接收端，
     * 再把此处填为其公网可达地址（如 "http://你的域名或IP:20250/pay/notify"）。
     */
    static final String NOTIFY_URL = "";

    /** 调试开关：true 时打印待签名串（私钥已脱敏）。排查"签名失败"时打开，与文档《签名规则》示例逐字比对 */
    static final boolean DEBUG_SIGN = false;

    /**
     * 响应验签失败是否中断（默认 true：抛异常终止 —— 这是生产的正确姿势，验签不过的数据不可信）。
     * 仅当排查"疑似序列化差异导致的验签误报"时可临时设 false 观察，排查完必须改回 true。
     */
    static final boolean STRICT_RESP_VERIFY = true;

    /** HTTP 客户端（JDK 内置 java.net.http，无需第三方依赖） */
    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /* ==================== main：完整演示流程 ==================== */

    /**
     * 演示流程：统一下单 → 打印全部返回字段（重点 payDataType / payData）→ 查询订单 → 打印订单状态。
     * 关闭订单 / 统一退款的调用示例见 main 末尾注释。每一步均打印请求/响应 JSON，便于对接调试。
     */
    public static void main(String[] args) throws Exception {
        // 模式二：回调接收端（java SanxiPayDemo notify-server [端口]），常驻监听平台异步通知
        if (args.length > 0 && "notify-server".equals(args[0])) {
            NotifyServer.start(args.length > 1 ? Integer.parseInt(args[1]) : 20250);
            return; // start() 内部阻塞常驻，此 return 仅为可读性
        }

        // wayCode 可用第 1 个命令行参数指定，默认 QR_CASHIER（聚合扫码：用户扫商家）。
        // 全部支付方式见文档《支付接口》"支付方式"表：WEB_CASHIER / QR_CASHIER / AUTO_BAR /
        // ALI_BAR / ALI_JSAPI / ALI_APP / ALI_WAP / ALI_PC / ALI_QR / WX_BAR / WX_JSAPI /
        // WX_LITE / WX_APP / WX_H5 / WX_NATIVE / YSF_BAR / YSF_JSAPI / AUTO_POS / DCEP_BAR / DCEP_QR
        String wayCode = args.length > 0 ? args[0] : "QR_CASHIER";
        // 金额可用第 2 个命令行参数指定，单位【分】，不能带小数（文档《签名规则》-参数规范），默认 1 分
        long amount = args.length > 1 ? Long.parseLong(args[1]) : 1L;

        System.out.println("================ 三希智付 API 对接演示 ================");
        System.out.println("网关: " + GATEWAY + " , wayCode: " + wayCode + " , amount(分): " + amount);
        if (MCH_NO.contains("你的") || APP_ID.contains("你的") || APP_SECRET.contains("你的")) {
            System.out.println("!!! 提醒：常量区 MCH_NO / APP_ID / APP_SECRET 仍是占位值，签名必然失败；");
            System.out.println("!!!       请先替换为平台分配的真实参数。（本次仍会发起请求，便于观察报文格式）");
        }

        /* ---------- 第 1 步：统一下单（文档：《支付接口》-统一下单） ---------- */
        Map<String, Object> orderResp = unifiedOrder(wayCode, amount, "三希智付对接测试商品");
        Map<String, Object> orderData = dataOf(orderResp);
        if (orderData == null) {
            System.out.println("下单未获得业务数据，流程终止。");
            return;
        }
        printData("统一下单返回 data", orderData);
        // 重点字段：payDataType 决定 payData 如何使用（文档：统一下单-返回参数）
        //   payUrl-跳转链接 / form-表单 / wxapp-微信支付参数 / aliapp-支付宝APP参数 /
        //   ysfapp-云闪付APP参数 / codeUrl-二维码地址 / codeImgUrl-二维码图片地址 / none-空支付参数
        System.out.println(">>> 支付参数: payDataType = " + orderData.get("payDataType")
                + " , payData = " + orderData.get("payData"));
        System.out.println(">>> 下单订单状态 orderState = " + orderData.get("orderState")
                + " (" + payStateText(orderData.get("orderState")) + ")");

        String mchOrderNo = String.valueOf(orderData.get("mchOrderNo"));

        /* ---------- 第 2 步：查询订单（文档：《支付接口》-查询订单） ---------- */
        Thread.sleep(1000L); // 稍候 1 秒再查询
        Map<String, Object> queryResp = queryOrder(mchOrderNo);
        Map<String, Object> queryData = dataOf(queryResp);
        if (queryData != null) {
            printData("查询订单返回 data", queryData);
            System.out.println(">>> 订单状态 state = " + queryData.get("state")
                    + " (" + payStateText(queryData.get("state")) + ")");
        }

        /* ---------- 其他接口按需调用（默认注释，避免误操作） ---------- */
        // 关闭订单（文档：《支付接口》-关闭订单）：
        // closeOrder(mchOrderNo);
        //
        // 统一退款（文档：《退款接口》-统一退款，需订单已支付成功）：
        // Map<String, Object> refundResp = refund(mchOrderNo, "R" + System.currentTimeMillis(), amount, "用户退货");
        // Map<String, Object> refundData = dataOf(refundResp);
        // if (refundData != null) {
        //     printData("统一退款返回 data", refundData);
        //     System.out.println(">>> 退款状态 state = " + refundData.get("state")
        //             + " (" + refundStateText(refundData.get("state")) + ")");
        // }
    }

    /* ==================== 业务接口 ==================== */

    /**
     * 统一下单（文档：《支付接口》- 统一下单，POST {GATEWAY}/api/pay/unifiedOrder）
     * 必填：mchNo、appId、mchOrderNo、wayCode、amount(分)、currency、subject、body、
     *       reqTime(13位毫秒)、version=1.0、signType=MD5、sign
     *
     * @param wayCode 支付方式，如 QR_CASHIER / ALI_QR / WX_NATIVE / ALI_BAR ...
     * @param amount  支付金额，单位分（不能带小数）
     * @param subject 商品标题
     * @return 网关完整响应 {code, msg, sign, data}
     */
    public static Map<String, Object> unifiedOrder(String wayCode, long amount, String subject) throws Exception {
        return unifiedOrder(wayCode, amount, subject, "");
    }

    /**
     * 统一下单（带 channelExtra 渠道参数的重载）。
     * 文档《支付接口》- channelExtra 参数说明（JSON 格式字符串）：
     *   - AUTO_BAR / ALI_BAR / WX_BAR / YSF_BAR：必传 {"authCode":"用户付款码值"}
     *   - WX_JSAPI / WX_LITE：必传 {"openid":"微信OpenId"}（特约商户用自有公众号/小程序时另加 subAppId）
     *   - ALI_JSAPI：必传 {"buyerUserId":"支付宝用户ID"}
     *   - ALI_QR / WX_NATIVE / QR_CASHIER：可传 {"payDataType":"codeUrl|codeImgUrl"}，默认 codeUrl
     *   - ALI_WAP：可传 payDataType=form|codeImgUrl|payUrl（默认 payUrl）；ALI_PC：form|payUrl（默认 payUrl）
     */
    public static Map<String, Object> unifiedOrder(String wayCode, long amount, String subject,
                                                   String channelExtra) throws Exception {
        // 商户订单号：商户系统内必须唯一，String(30)。纯毫秒时间戳并发下会撞号，
        // 故追加 4 位随机数（生产建议：业务前缀 + 时间 + 分布式序列/随机段，自行保证全局唯一）
        String mchOrderNo = "SXDEMO" + System.currentTimeMillis()
                + String.format("%04d", java.util.concurrent.ThreadLocalRandom.current().nextInt(10000));

        Map<String, Object> p = newRequest();
        p.put("mchOrderNo", mchOrderNo);
        p.put("wayCode", wayCode);
        p.put("amount", amount);             // 支付金额：单位分（文档《签名规则》：金额不能带小数）
        p.put("currency", "cny");            // 三位货币代码，人民币: cny（按《支付接口》表格与示例取小写）
        p.put("subject", subject);           // 商品标题 String(64)
        p.put("body", subject);              // 商品描述 String(256)（演示：复用标题，实际按业务填写）
        p.put("clientIp", CLIENT_IP);        // 可选：客户端 IPV4
        p.put("notifyUrl", NOTIFY_URL);      // 可选：异步回调 URL，传了才会回调（空值不参与签名）
        p.put("channelExtra", channelExtra); // 可选：渠道参数（空值不参与签名）
        return send("/api/pay/unifiedOrder", p, "统一下单");
    }

    /**
     * 查询订单（文档：《支付接口》- 查询订单，POST {GATEWAY}/api/pay/query）
     * payOrderId 与 mchOrderNo 二者传一即可；本示例按商户订单号查询。
     *
     * @param mchOrderNo 商户订单号
     * @return 网关完整响应；data 内含 state（0-订单生成 1-支付中 2-支付成功 3-支付失败 4-已撤销 5-已退款 6-订单关闭）
     */
    public static Map<String, Object> queryOrder(String mchOrderNo) throws Exception {
        Map<String, Object> p = newRequest();
        p.put("mchOrderNo", mchOrderNo);
        return send("/api/pay/query", p, "查询订单");
    }

    /**
     * 关闭订单（文档：《支付接口》- 关闭订单，POST {GATEWAY}/api/pay/close）
     * payOrderId 与 mchOrderNo 二者传一即可；本示例按商户订单号关单。
     * 返回 data 内仅含渠道错误信息 errCode / errMsg（无错误时为空）。
     */
    public static Map<String, Object> closeOrder(String mchOrderNo) throws Exception {
        Map<String, Object> p = newRequest();
        p.put("mchOrderNo", mchOrderNo);
        return send("/api/pay/close", p, "关闭订单");
    }

    /**
     * 统一退款（文档：《退款接口》- 统一退款，POST {GATEWAY}/api/refund/refundOrder）
     * 必填：mchNo、appId、payOrderId/mchOrderNo 二选一、mchRefundNo、refundAmount(分)、
     *       currency、refundReason、reqTime、version、signType、sign
     * 退款结果查询接口为 POST {GATEWAY}/api/refund/query（refundOrderId 与 mchRefundNo 二者传一），
     * 参数组装与签名方式与本方法一致，按需自行扩展。
     *
     * @param mchOrderNo   原支付的商户订单号（与 payOrderId 二选一，本示例用商户订单号）
     * @param mchRefundNo  商户退款单号：商户系统内唯一，String(30)
     * @param refundAmount 退款金额，单位分
     * @param refundReason 退款原因
     * @return 网关完整响应；data 内含 state（0-订单生成 1-退款中 2-退款成功 3-退款失败 4-退款关闭）
     */
    public static Map<String, Object> refund(String mchOrderNo, String mchRefundNo,
                                             long refundAmount, String refundReason) throws Exception {
        Map<String, Object> p = newRequest();
        p.put("mchOrderNo", mchOrderNo);
        p.put("mchRefundNo", mchRefundNo);
        p.put("refundAmount", refundAmount);
        p.put("currency", "cny");
        p.put("refundReason", refundReason);
        p.put("clientIp", CLIENT_IP);   // 可选
        p.put("notifyUrl", NOTIFY_URL); // 可选：退款完成后回调该 URL（空值不参与签名）
        return send("/api/refund/refundOrder", p, "统一退款");
    }

    /* ==================== 公共请求逻辑 ==================== */

    /** 新建请求参数表并预置商户身份字段 mchNo / appId（LinkedHashMap 仅为报文字段顺序可读，签名与顺序无关） */
    static Map<String, Object> newRequest() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("mchNo", MCH_NO);
        p.put("appId", APP_ID);
        return p;
    }

    /**
     * 追加公共参数并签名后 POST 到网关（文档：《签名规则》-协议规则）：
     *   传输 HTTPS；提交 POST；内容类型 application/json；字符编码 UTF-8；签名算法 MD5。
     * 每次调用打印请求/响应 JSON；HTTP 非 200 抛异常；响应按 JSON 解析并做验签提示。
     */
    static Map<String, Object> send(String path, Map<String, Object> params, String step) throws Exception {
        params.put("reqTime", System.currentTimeMillis()); // 请求时间：13 位毫秒时间戳（文档《签名规则》-参数规范）
        params.put("version", "1.0");                      // 接口版本：固定 1.0
        params.put("signType", "MD5");                     // 签名类型：目前只支持 MD5
        params.put("sign", sign(params, APP_SECRET));      // 最后计算签名（sign 字段自身不参与签名）

        String body = toJson(params);
        System.out.println();
        System.out.println(">>> [" + step + "] POST " + GATEWAY + path);
        System.out.println(">>> 请求JSON: " + body);

        HttpRequest req = HttpRequest.newBuilder(URI.create(GATEWAY + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("<<< HTTP状态: " + resp.statusCode());
        System.out.println("<<< 响应JSON: " + resp.body());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("[" + step + "] HTTP 状态异常: " + resp.statusCode());
        }

        Object parsed = MiniJson.parse(resp.body().trim());
        if (!(parsed instanceof Map)) {
            throw new IllegalStateException("[" + step + "] 响应不是合法 JSON 对象");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) parsed;
        verifyRespSign(m);
        return m;
    }

    /**
     * 提取业务数据：code==0 时返回 data 对象（文档-返回码：0-成功，9999-异常详见 msg 字段），
     * 失败时打印 code / msg 并返回 null；兼容 data 以 JSON 字符串形式返回的情形。
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> dataOf(Map<String, Object> resp) {
        Object code = resp.get("code");
        if (!(code instanceof Number) || ((Number) code).longValue() != 0L) {
            System.out.println("!!! 业务失败: code=" + code + " , msg=" + resp.get("msg"));
            return null;
        }
        Object data = resp.get("data");
        if (data instanceof String s && !s.isBlank()) {
            Object p = MiniJson.parse(s.trim());
            if (p instanceof Map) return (Map<String, Object>) p;
        }
        return (data instanceof Map) ? (Map<String, Object>) data : null;
    }

    /* ==================== 签名（文档：《签名规则》-签名算法） ==================== */

    /**
     * MD5 签名（文档《签名规则》-签名算法 第一步/第二步）：
     * 第一步：取集合 M 内【非空参数值】的参数，按参数名 ASCII 码从小到大排序（字典序），
     *         用 URL 键值对格式 key1=value1&key2=value2... 拼接成 stringA。
     *   ◆ 参数值为空不参与签名；            ◆ 参数名区分大小写；
     *   ◆ sign 字段自身不参与签名；         ◆ 值取原文拼接，不做 URL 编码（与文档示例一致）；
     *   ◆ 验签时必须支持支付中心新增的扩展字段。
     * 第二步：stringSignTemp = stringA + "&key=" + 私钥，对其做 MD5（UTF-8）运算，
     *         再将得到的字符串所有字符转换为大写，即为 sign 值。
     *
     * @param params 参与签名的全部参数（值可为 String/Number 等，统一按字符串原文参与拼接）
     * @param secret 商户私钥
     * @return 32 位大写 MD5 签名
     */
    public static String sign(Map<String, Object> params, String secret) {
        String stringSignTemp = stringToSign(params, secret);
        if (DEBUG_SIGN) {
            String masked = secret.isEmpty() ? stringSignTemp : stringSignTemp.replace(secret, "******");
            System.out.println("    [DEBUG] 待签名串(私钥已脱敏): " + masked);
        }
        return md5Upper(stringSignTemp);
    }

    /**
     * 组装待签名串 stringSignTemp = 排序拼接串stringA + "&key=" + 私钥（独立成方法便于排查签名问题）。
     * 排序采用【不区分大小写】的字典序（String.CASE_INSENSITIVE_ORDER），与平台服务端签名工具
     * （JeepayKit.getSign 的 Arrays.sort(..., String.CASE_INSENSITIVE_ORDER)）逐字一致——
     * 标准字段全部小写开头时两种字典序结果相同，但若平台新增大写开头扩展字段，ASCII 序会产生验签差异。
     */
    static String stringToSign(Map<String, Object> params, String secret) {
        Map<String, Object> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); // 与服务端排序规则一致
        sorted.putAll(params);
        StringBuilder sb = new StringBuilder(256);
        for (Map.Entry<String, Object> e : sorted.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if ("sign".equals(k) || v == null) continue;       // sign 不参与签名；null 视为空
            String val = (v instanceof Map || v instanceof List) ? toJson(v) : String.valueOf(v);
            if (val.isEmpty()) continue;                       // 空值不参与签名
            sb.append(k).append('=').append(val).append('&');
        }
        sb.append("key=").append(secret);                      // stringA + "&key=" + 私钥
        return sb.toString();
    }

    /** 对文本做 MD5（UTF-8）并转 32 位大写十六进制（文档：签名算法 第二步） */
    static String md5Upper(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(32);
            for (byte b : digest) hex.append(String.format("%02X", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 计算失败", e);
        }
    }

    /**
     * 响应验签（文档《签名规则》：返回的 sign 是"对 data 内数据签名"，data 为空则不返回 sign；
     * 验签时 sign 参数不参与签名，且必须支持支付中心新增的扩展字段——故对 data 内全部非空字段参与计算）。
     * ⚠️ 验签失败 = 数据不可信（可能被篡改或私钥配置错误），默认（STRICT_RESP_VERIFY）直接抛异常终止。
     *    生产代码同样必须拒绝处理验签失败的响应，绝不能"只打日志继续走"。
     */
    static void verifyRespSign(Map<String, Object> resp) {
        Object data = resp.get("data");
        Object sig = resp.get("sign");
        if (!(data instanceof Map) || sig == null) return; // data 为空不返回 sign，无需验签
        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = (Map<String, Object>) data;
        String calc = sign(dataMap, APP_SECRET);
        if (calc.equalsIgnoreCase(String.valueOf(sig))) {
            System.out.println("<<< 响应验签: 通过");
            return;
        }
        String hint = "响应验签未通过(本地计算=" + calc + ")。多为 APP_SECRET 配置错误；"
                + "若 data 含嵌套 JSON 字段也可能为序列化差异，请打开 DEBUG_SIGN 比对或联系平台技术支持";
        if (STRICT_RESP_VERIFY) {
            throw new IllegalStateException("<<< " + hint);
        }
        System.out.println("<<< [警告][仅排查模式] " + hint + "（生产必须拒绝该响应）");
    }

    /* ==================== 打印辅助 ==================== */

    /** 逐行打印 data 内全部字段（支付中心可能新增扩展字段，全部打印便于观察） */
    static void printData(String title, Map<String, Object> data) {
        System.out.println("--- " + title + " ---");
        for (Map.Entry<String, Object> e : data.entrySet()) {
            Object v = e.getValue();
            System.out.println("    " + e.getKey() + " = "
                    + ((v instanceof Map || v instanceof List) ? toJson(v) : v));
        }
    }

    /** 支付订单状态含义（文档：统一下单返回 orderState / 查询订单与支付通知返回 state，取值含义相同） */
    static String payStateText(Object state) {
        if (!(state instanceof Number n)) return "未知";
        return switch (n.intValue()) {
            case 0 -> "订单生成";
            case 1 -> "支付中";
            case 2 -> "支付成功";
            case 3 -> "支付失败";
            case 4 -> "已撤销";
            case 5 -> "已退款";
            case 6 -> "订单关闭";
            default -> "未知状态";
        };
    }

    /** 退款状态含义（文档《退款接口》：0-订单生成 1-退款中 2-退款成功 3-退款失败 4-退款关闭） */
    static String refundStateText(Object state) {
        if (!(state instanceof Number n)) return "未知";
        return switch (n.intValue()) {
            case 0 -> "订单生成";
            case 1 -> "退款中";
            case 2 -> "退款成功";
            case 3 -> "退款失败";
            case 4 -> "退款关闭";
            default -> "未知状态";
        };
    }

    /* ==================== 极简 JSON 序列化（请求体 application/json 使用） ==================== */

    /** 将 Map/List/String/Number/Boolean/null 序列化为紧凑 JSON 字符串 */
    static String toJson(Object v) {
        StringBuilder sb = new StringBuilder(256);
        writeJson(sb, v);
        return sb.toString();
    }

    static void writeJson(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            quote(sb, s);
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeJson(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                writeJson(sb, list.get(i));
            }
            sb.append(']');
        } else {
            quote(sb, String.valueOf(v));
        }
    }

    /** JSON 字符串转义（双引号/反斜杠/控制字符；中文按 UTF-8 原样输出） */
    static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    /* ==================== 异步通知（回调）接收端演示 ==================== */

    /**
     * 支付/退款异步通知接收端（文档：《支付通知》）。启动：java SanxiPayDemo notify-server [端口]
     * 平台按商户配置的通知类型投递（默认 POST form 即 application/x-www-form-urlencoded；
     * 亦可配置为 POST JSON、或参数拼在 URL 上的 queryString 形式），本接收端三种形态全兼容。
     *
     * 商户系统处理通知的【三道必做校验】（缺一不可，本类 process() 即按此顺序实现）：
     *   ① 验签   ：对全部非空参数（sign 自身除外）按《签名规则》计算并与 sign 比对，失败必须拒绝；
     *   ② 幂等   ：同一订单可能重复通知（平台重试机制），已处理过的直接应答 success，不能重复发货/记账；
     *   ③ 业务核对：state==2 才是支付成功；amount 必须与本地订单金额一致；mchOrderNo 必须存在于本地。
     * 全部通过后应答小写 success（无空格/换行）；否则平台按 0/30/60/90/120/150 秒重试（最多 6 次）。
     */
    static final class NotifyServer {

        /** 已处理订单去重表（演示用内存 Set；生产请用 DB 唯一约束 / Redis SETNX 等持久化手段做幂等） */
        private static final Set<String> HANDLED = java.util.concurrent.ConcurrentHashMap.newKeySet();

        static void start(int port) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", NotifyServer::handle); // 任意路径均处理；实际路径以填写的 notifyUrl 为准
            server.setExecutor(null); // 演示用默认单线程分发；生产按并发量配置线程池
            server.start();
            System.out.println("================ 三希智付 异步通知接收端 ================");
            System.out.println("监听端口: " + port + "（通知地址示例: http://<公网IP或域名>:" + port + "/pay/notify）");
            System.out.println("等待平台通知中... (Ctrl+C 退出)");
        }

        private static void handle(HttpExchange ex) throws IOException {
            String answer;
            try {
                Map<String, Object> params = readParams(ex);
                System.out.println();
                System.out.println(">>> 收到通知 " + ex.getRequestMethod() + " " + ex.getRequestURI());
                printData("通知参数", params);
                answer = process(params);
            } catch (Exception e) {
                System.out.println("!!! 通知处理异常: " + e);
                answer = "fail: internal error"; // 非 success 应答，平台会重试
            }
            byte[] out = answer.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(out); }
            System.out.println("<<< 应答: " + answer);
        }

        /** 三道必做校验；全过返回 "success"（小写、无空格换行），任一不过返回错误说明（平台会重试） */
        private static String process(Map<String, Object> p) {
            // ① 验签：与请求方向同一算法（sign 自身不参与、空值不参与、大小写不敏感字典序）
            Object sig = p.get("sign");
            if (sig == null) return "fail: missing sign";
            String calc = sign(p, APP_SECRET);
            if (!calc.equalsIgnoreCase(String.valueOf(sig))) {
                System.out.println("!!! 通知验签失败(本地计算=" + calc + ")，数据不可信，拒绝处理");
                return "fail: sign verify failed";
            }
            System.out.println(">>> 通知验签: 通过");

            // ② 幂等：重复通知直接应答 success（告知平台已收到、停止重试），但绝不重复发货/记账
            String payOrderId = String.valueOf(p.get("payOrderId"));
            if (!HANDLED.add(payOrderId)) {
                System.out.println(">>> 重复通知(payOrderId=" + payOrderId + ")，此前已处理，直接应答 success");
                return "success";
            }

            // ③ 业务核对（演示仅打印。生产必须：按 mchOrderNo 查本地订单存在、核对 amount 与本地一致，再按 state 处理）
            int state = toInt(p.get("state"), -1); // form 形态下为字符串，统一归一化
            System.out.println(">>> 订单 " + p.get("mchOrderNo") + " 金额(分)=" + p.get("amount")
                    + " 状态=" + state + " (" + payStateText(state) + ")");
            if (state == 2) {
                System.out.println(">>> 支付成功 → 此处执行商户系统发货/记账逻辑（须先核对本地订单金额一致）");
            } else {
                System.out.println(">>> 非支付成功终态，按业务标记失败/关闭，不发货");
            }
            return "success";
        }

        /** 兼容三种投递形态读取参数：URL queryString / POST form（默认） / POST JSON */
        private static Map<String, Object> readParams(HttpExchange ex) throws IOException {
            Map<String, Object> params = new LinkedHashMap<>();
            parseFormInto(ex.getRequestURI().getRawQuery(), params);         // queryString 形态
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!body.isBlank()) {
                String ct = String.valueOf(ex.getRequestHeaders().getFirst("Content-Type"));
                if (ct.contains("json")) {                                    // POST JSON 形态
                    Object parsed = MiniJson.parse(body.trim());
                    if (parsed instanceof Map<?, ?> m) m.forEach((k, v) -> params.put(String.valueOf(k), v));
                } else {                                                      // POST form 形态（默认）
                    parseFormInto(body, params);
                }
            }
            return params;
        }

        /** 解析 application/x-www-form-urlencoded 键值对（UTF-8 URL 解码）并入 params */
        private static void parseFormInto(String encoded, Map<String, Object> params) {
            if (encoded == null || encoded.isBlank()) return;
            for (String kv : encoded.split("&")) {
                if (kv.isEmpty()) continue;
                int eq = kv.indexOf('=');
                String k = eq < 0 ? kv : kv.substring(0, eq);
                String v = eq < 0 ? "" : kv.substring(eq + 1);
                params.put(URLDecoder.decode(k, StandardCharsets.UTF_8),
                           URLDecoder.decode(v, StandardCharsets.UTF_8));
            }
        }

        /** 宽松取整：Number 直取，字符串尝试解析，失败返回默认值（form 形态下数字字段是字符串） */
        private static int toInt(Object v, int def) {
            if (v instanceof Number n) return n.intValue();
            try { return Integer.parseInt(String.valueOf(v)); } catch (NumberFormatException e) { return def; }
        }
    }

    /* ==================== 极简 JSON 解析器（递归下降，解析响应用） ==================== */

    /**
     * 仅支持标准 JSON：对象 / 数组 / 字符串 / 数字 / true / false / null。
     * 数字无小数点与指数时解析为 Long，否则 Double（13 位时间戳、金额分均为整数，不丢精度）。
     */
    static final class MiniJson {
        private final String s;
        private int i;

        private MiniJson(String s) { this.s = s; }

        /** 解析 JSON 文本，返回 Map / List / String / Long / Double / Boolean / null */
        static Object parse(String text) {
            MiniJson p = new MiniJson(text);
            Object v = p.value();
            p.skipWs();
            if (p.i < p.s.length()) throw p.err("JSON 末尾存在多余字符");
            return v;
        }

        private Object value() {
            skipWs();
            char c = peek();
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // 吃掉 '{'
            skipWs();
            if (peek() == '}') { i++; return m; }
            while (true) {
                skipWs();
                String k = string();
                skipWs();
                if (peek() != ':') throw err("期望 ':'");
                i++;
                m.put(k, value());
                skipWs();
                char c = peek();
                i++;
                if (c == '}') return m;
                if (c != ',') throw err("期望 ',' 或 '}'");
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            i++; // 吃掉 '['
            skipWs();
            if (peek() == ']') { i++; return list; }
            while (true) {
                list.add(value());
                skipWs();
                char c = peek();
                i++;
                if (c == ']') return list;
                if (c != ',') throw err("期望 ',' 或 ']'");
            }
        }

        private String string() {
            if (peek() != '"') throw err("期望字符串");
            i++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (i >= s.length()) throw err("字符串未闭合");
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (i >= s.length()) throw err("转义未结束");
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (i + 4 > s.length()) throw err("\\u 转义不完整");
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> throw err("非法转义 \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private Object number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String t = s.substring(start, i);
            if (t.isEmpty()) throw err("非法 JSON 值");
            try {
                return (t.indexOf('.') < 0 && t.indexOf('e') < 0 && t.indexOf('E') < 0)
                        ? (Object) Long.parseLong(t) : (Object) Double.parseDouble(t);
            } catch (NumberFormatException ex) {
                throw err("数字格式错误: " + t);
            }
        }

        private Object literal(String lit, Object val) {
            if (!s.startsWith(lit, i)) throw err("期望 " + lit);
            i += lit.length();
            return val;
        }

        private void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private char peek() {
            if (i >= s.length()) throw err("JSON 意外结束");
            return s.charAt(i);
        }

        private RuntimeException err(String msg) {
            return new IllegalArgumentException("JSON 解析失败(位置 " + i + "): " + msg);
        }
    }
}
```
