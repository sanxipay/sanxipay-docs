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
 *  运行：java SanxiPayDemo [wayCode] [amount(分)] [channelExtra] [--allow-qr-cashier-fallback]
 *        例：java SanxiPayDemo                # 默认 QR_CASHIER（聚合扫码），金额 1 分
 *            java SanxiPayDemo ALI_QR 100    # 支付宝二维码，1.00 元
 *            java SanxiPayDemo WX_JSAPI 100   # 默认不降级，直接显示原错误
 *            java SanxiPayDemo WX_JSAPI 100 --allow-qr-cashier-fallback  # 显式允许降级到聚合收银台
 *            java SanxiPayDemo QR_CASHIER 1 '{"payDataType":"codeImgUrl"}'   # 返回二维码图片地址
 *            java SanxiPayDemo query <mchOrderNo>
 *            java SanxiPayDemo refund <mchOrderNo> <mchRefundNo> <amountFen>
 *            java SanxiPayDemo refund-query <mchRefundNo>
 *        回调接收端（先显式登记本次测试的业务单号和金额，再把 NOTIFY_URL 指向它）：
 *            export SANXIPAY_EXPECTED_PAYMENTS='商户支付单号=金额分'
 *            export SANXIPAY_EXPECTED_REFUNDS='商户退款单号=金额分'
 *            java SanxiPayDemo notify-server [端口]   # 默认 20250，路径任意（如 /pay/notify）
 *
 *  运行前通过环境变量提供 SANXIPAY_GATEWAY / SANXIPAY_MCH_NO / SANXIPAY_APP_ID /
 *  SANXIPAY_APP_SECRET；缺任一必填值立即终止，不把真实凭据写进源码。
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
 *    3. 回调必须区分支付单与退款单，分别做幂等、金额核对和状态判断。Demo 用两个显式环境变量
 *       模拟本地订单库；生产必须替换为真实数据库查询与唯一约束，详见 NotifyServer 注释；
 *    4. 查单轮询请用递增间隔（如 2/5/10/30s），不要照抄本 Demo 的固定 sleep。
 * ============================================================================
 */
public class SanxiPayDemo {

    /* ==================== 运行配置（NO FALLBACK） ==================== */

    static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    /** 支付网关地址（文档：各接口请求 URL 的公共前缀） */
    static final String GATEWAY = requireEnv("SANXIPAY_GATEWAY");

    /** 商户号：平台分配，形如 M1621873433953 */
    static final String MCH_NO = requireEnv("SANXIPAY_MCH_NO");

    /** 应用ID：平台分配，形如 60cc09bce4b0f1c0b83761c9 */
    static final String APP_ID = requireEnv("SANXIPAY_APP_ID");

    /** 商户私钥（文档《签名规则》：运营管理平台可以管理商户的私钥） */
    static final String APP_SECRET = requireEnv("SANXIPAY_APP_SECRET");

    /** 客户端 IPV4（可选参数 clientIp 的演示取值，实际请传真实用户 IP） */
    static final String CLIENT_IP = "127.0.0.1";

    /**
     * 支付/退款结果异步通知地址（可选。文档：只有传了该值才会发起回调；留空则不回调）。
     * 联调回调时：先 `java SanxiPayDemo notify-server 20250` 启动本文件自带的接收端，
     * 再设置可选环境变量 SANXIPAY_NOTIFY_URL 为其公网可达地址。
     */
    static final String NOTIFY_URL = System.getenv("SANXIPAY_NOTIFY_URL") == null
            ? "" : System.getenv("SANXIPAY_NOTIFY_URL");

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
        if (args.length > 0 && "query".equals(args[0])) {
            if (args.length != 2) throw new IllegalArgumentException("Usage: query <mchOrderNo>");
            requireBusinessSuccess(queryOrder(args[1]), "查询订单");
            return;
        }
        if (args.length > 0 && "refund".equals(args[0])) {
            if (args.length != 4) {
                throw new IllegalArgumentException("Usage: refund <mchOrderNo> <mchRefundNo> <amountFen>");
            }
            requireBusinessSuccess(
                    refund(args[1], args[2], Long.parseLong(args[3]), "三希智付对接验收退款"),
                    "统一退款");
            return;
        }
        if (args.length > 0 && "refund-query".equals(args[0])) {
            if (args.length != 2) throw new IllegalArgumentException("Usage: refund-query <mchRefundNo>");
            requireBusinessSuccess(queryRefund(args[1]), "查询退款");
            return;
        }

        // 解析命令行参数：
        //   java SanxiPayDemo [wayCode] [amount(分)] [channelExtra] [--allow-qr-cashier-fallback]
        // 默认 fail-fast，不把直连渠道配置错误藏在新建的 QR_CASHIER 订单后面；只有显式传入开关才降级。
        boolean fallbackToQrCashier = false;
        List<String> positional = new ArrayList<>();
        for (String arg : args) {
            if ("--allow-qr-cashier-fallback".equals(arg)) {
                fallbackToQrCashier = true;
            } else {
                positional.add(arg);
            }
        }
        // wayCode 可用第 1 个命令行参数指定，默认 QR_CASHIER（聚合扫码：用户扫商家）。
        // 全部支付方式见文档《支付接口》"支付方式"表：WEB_CASHIER / QR_CASHIER / AUTO_BAR /
        // ALI_BAR / ALI_JSAPI / ALI_APP / ALI_WAP / ALI_PC / ALI_QR / WX_BAR / WX_JSAPI /
        // WX_LITE / WX_APP / WX_H5 / WX_NATIVE / YSF_BAR / YSF_JSAPI / AUTO_POS / DCEP_BAR / DCEP_QR
        final String originalWayCode = positional.isEmpty() ? "QR_CASHIER" : positional.get(0);
        // 金额可用第 2 个命令行参数指定，单位【分】，不能带小数（文档《签名规则》-参数规范），默认 1 分
        long amount = positional.size() > 1 ? Long.parseLong(positional.get(1)) : 1L;
        // channelExtra 可传第 3 个参数；降级到 QR_CASHIER 时会保留原渠道参数（如 {"payDataType":"codeImgUrl"}）
        String channelExtra = positional.size() > 2 ? positional.get(2) : "";

        // 可选兼容策略：调用方显式允许时，非 QR_CASHIER 的 wayCode 下单失败可降级到聚合收银台。
        // QR_CASHIER 为两段式支付：下单只生成本地订单+收银台 URL，不碰上游渠道，因此不会触发
        // 渠道级签名/授权错误；降级会创建一个新的商户订单号，不能作为直连渠道验收结果。

        System.out.println("================ 三希智付 API 对接演示 ================");
        System.out.println("网关: " + GATEWAY + " , wayCode: " + originalWayCode + " , amount(分): " + amount);
        if (fallbackToQrCashier) {
            System.out.println("!!! 已显式允许 QR_CASHIER 降级；降级订单不能作为原直连渠道验收结果。");
        } else {
            System.out.println(">>> 默认 fail-fast：直连渠道失败时不自动创建 QR_CASHIER 订单。");
        }
        /* ---------- 第 1 步：统一下单（文档：《支付接口》-统一下单） ---------- */
        Map<String, Object> orderResp = unifiedOrder(originalWayCode, amount, "三希智付对接测试商品", channelExtra);
        Map<String, Object> orderData = dataOf(orderResp);
        String activeWayCode = originalWayCode;

        // 如果指定 wayCode 失败且启用了 QR_CASHIER 降级，则重试聚合收银台，避免在接口层直接报错。
        // 判定"失败"：网关 code!=0，或 code==0 但 orderState==3（支付失败）且无可用支付参数。
        if (fallbackToQrCashier && !"QR_CASHIER".equals(originalWayCode) && shouldFallbackToQrCashier(orderData)) {
            Map<String, Object> originalResp = orderResp;
            System.out.println("!!! wayCode=" + originalWayCode + " 统一下单失败，自动降级到 QR_CASHIER 聚合收银台继续推进用户旅程。");
            orderResp = unifiedOrder("QR_CASHIER", amount, "三希智付对接测试商品", channelExtra);
            orderData = dataOf(orderResp);
            activeWayCode = "QR_CASHIER";
            if (orderData == null && originalResp != null) {
                System.out.println("!!! QR_CASHIER 兜底也失败，原始下单报错：code=" + originalResp.get("code")
                        + " , msg=" + originalResp.get("msg"));
            }
        }

        if (orderData == null) {
            throw new IllegalStateException("统一下单失败，未获得业务数据");
        }
        printData("统一下单返回 data [" + activeWayCode + "]", orderData);
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

    /** 查询退款结果（POST {GATEWAY}/api/refund/query），按商户退款单号查询。 */
    public static Map<String, Object> queryRefund(String mchRefundNo) throws Exception {
        Map<String, Object> p = newRequest();
        p.put("mchRefundNo", mchRefundNo);
        return send("/api/refund/query", p, "查询退款");
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
     * 判断是否需要降级到 QR_CASHIER：
     * 1. 网关 code != 0（dataOf 返回 null）；
     * 2. 或 code == 0 但统一下单返回 orderState == 3（支付失败）且无可用支付参数。
     *    支付参数为空包含：payDataType / payData 缺失、为空字符串，或 payDataType = "none"。
     *    orderState 兼容 Number / String 两种 JSON 反序列化形态。
     */
    static boolean shouldFallbackToQrCashier(Map<String, Object> data) {
        if (data == null) return true;
        int state = intValueOf(data.get("orderState"), -1);
        return state == 3 && isBlankPayData(data.get("payDataType"), data.get("payData"));
    }

    /** 将 Number 或 String 解析为 int；无法解析时返回默认值。 */
    static int intValueOf(Object o, int defaultValue) {
        if (o instanceof Number n) return n.intValue();
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    /** 判定支付参数是否为空（"none" 也视为空，因为表示无需客户端处理）。 */
    static boolean isBlankPayData(Object payDataType, Object payData) {
        String type = payDataType == null ? "" : String.valueOf(payDataType).trim();
        String data = payData == null ? "" : String.valueOf(payData).trim();
        return (type.isEmpty() || "none".equalsIgnoreCase(type)) && data.isEmpty();
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

    /** CLI 子命令遇到业务失败必须以非零退出，避免自动验收把失败请求当成功。 */
    static void requireBusinessSuccess(Map<String, Object> resp, String operation) {
        Object code = resp.get("code");
        if (!(code instanceof Number) || ((Number) code).longValue() != 0L) {
            throw new IllegalStateException(operation + "失败: code=" + code + ", msg=" + resp.get("msg"));
        }
    }

    /* ==================== 签名（文档：《签名规则》-签名算法） ==================== */

    /**
     * MD5 签名（文档《签名规则》-签名算法 第一步/第二步）：
     * 第一步：取集合 M 内【非空参数值】的参数，先构造每个完整的 key=value& 片段，再按片段
     *         不区分大小写的字典序排序并拼接成 stringA。
     *   ◆ 参数值为空不参与签名；            ◆ 参数名区分大小写；
     *   ◆ sign 字段自身不参与签名；         ◆ 值取原文拼接，不做 URL 编码（与文档示例一致）；
     *   ◆ 验签时必须支持支付中心新增的扩展字段。
     * 第二步：stringA 的最后一个片段已经以 & 结尾，直接拼接 "key=" + 私钥得到 stringSignTemp，
     *         再对其做 MD5（UTF-8）运算，
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
     * 排序对象是完整的 key=value& 片段，不是只排参数名；比较器使用
     * String.CASE_INSENSITIVE_ORDER，与生产服务端的 Arrays.sort(...) 逐字一致。
     */
    static String stringToSign(Map<String, Object> params, String secret) {
        List<String> fragments = new ArrayList<>();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if ("sign".equals(k) || v == null) continue;       // sign 不参与签名；null 视为空
            String val = (v instanceof Map || v instanceof List) ? toJson(v) : String.valueOf(v);
            if (val.isEmpty()) continue;                       // 空值不参与签名
            fragments.add(k + '=' + val + '&');
        }
        fragments.sort(String.CASE_INSENSITIVE_ORDER);
        StringBuilder sb = new StringBuilder(256);
        for (String fragment : fragments) sb.append(fragment);
        sb.append("key=").append(secret);                      // stringA 已以 & 结尾，直接拼 key=私钥
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
        int n = intValueOf(state, -1);
        return switch (n) {
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
        int n = intValueOf(state, -1);
        return switch (n) {
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
     * 商户系统处理通知的【三道必做校验】（缺一不可，本类 process() 按此顺序演示）：
     *   ① 验签   ：对全部非空参数（sign 自身除外）按《签名规则》计算并与 sign 比对，失败必须拒绝；
     *   ② 业务核对：支付按 mchOrderNo+amount，退款按 mchRefundNo+refundAmount 核对本地预期；
     *   ③ 幂等   ：支付用 payOrderId、退款用 refundOrderId 分开去重，不能互相误判为重复。
     * Demo 通过 SANXIPAY_EXPECTED_PAYMENTS / SANXIPAY_EXPECTED_REFUNDS 显式登记预期业务单，
     * 格式均为「业务单号=金额分」，多笔用逗号分隔；未登记的通知 fail closed。生产必须把这两张
     * 内存表替换成自己的订单数据库查询，并用数据库唯一约束 / Redis SETNX 做持久化幂等。
     * 全部通过后应答小写 success（无空格/换行）；否则平台按 0/30/60/90/120/150 秒重试（最多 6 次）。
     */
    static final class NotifyServer {

        /** 演示用预期业务单；未显式登记就拒绝通知，禁止用默认值假装完成业务核对。 */
        private static final Map<String, Long> EXPECTED_PAYMENTS = expectedAmounts("SANXIPAY_EXPECTED_PAYMENTS");
        private static final Map<String, Long> EXPECTED_REFUNDS = expectedAmounts("SANXIPAY_EXPECTED_REFUNDS");

        /** 已处理通知去重表（演示用内存 Set；生产请用 DB 唯一约束 / Redis SETNX 等持久化手段做幂等） */
        private static final Set<String> HANDLED = java.util.concurrent.ConcurrentHashMap.newKeySet();

        static void start(int port) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", NotifyServer::handle); // 任意路径均处理；实际路径以填写的 notifyUrl 为准
            server.setExecutor(null); // 演示用默认单线程分发；生产按并发量配置线程池
            server.start();
            System.out.println("================ 三希智付 异步通知接收端 ================");
            System.out.println("监听端口: " + port + "（通知地址示例: http://<公网IP或域名>:" + port + "/pay/notify）");
            System.out.println("预期支付单: " + EXPECTED_PAYMENTS.keySet());
            System.out.println("预期退款单: " + EXPECTED_REFUNDS.keySet());
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

            // ② 业务核对：先辨认通知类型，再按各自业务单号和金额核对显式预期。
            boolean isRefund = hasText(p.get("refundOrderId")) || hasText(p.get("mchRefundNo"));
            String businessNoField = isRefund ? "mchRefundNo" : "mchOrderNo";
            String amountField = isRefund ? "refundAmount" : "amount";
            String platformIdField = isRefund ? "refundOrderId" : "payOrderId";
            String businessNo = requiredText(p, businessNoField);
            String platformId = requiredText(p, platformIdField);
            long actualAmount = requiredLong(p, amountField);
            Map<String, Long> expected = isRefund ? EXPECTED_REFUNDS : EXPECTED_PAYMENTS;
            Long expectedAmount = expected.get(businessNo);
            if (expectedAmount == null) {
                return "fail: unknown " + (isRefund ? "refund" : "payment") + " order";
            }
            if (expectedAmount.longValue() != actualAmount) {
                return "fail: amount mismatch";
            }
            int state = toInt(p.get("state"), -1);
            boolean acceptedState = isRefund ? state == 2 || state == 3 : state == 2;
            if (!acceptedState) {
                return "fail: unexpected state";
            }
            System.out.println(">>> " + (isRefund ? "退款" : "支付") + "业务核对通过: "
                    + businessNo + " amount=" + actualAmount + " state=" + state);

            // ③ 幂等：支付和退款分别使用各自平台单号，避免同一 payOrderId 下的退款被误判为支付重复通知。
            String idempotencyKey = (isRefund ? "REFUND:" : "PAY:") + platformId;
            if (!HANDLED.add(idempotencyKey)) {
                System.out.println(">>> 重复通知(" + idempotencyKey + ")，此前已处理，直接应答 success");
                return "success";
            }

            String action = isRefund
                    ? (state == 2 ? "退款成功 → 此处更新本地退款单" : "退款失败 → 此处更新本地退款单失败状态")
                    : "支付成功 → 此处执行发货/记账逻辑";
            System.out.println(">>> " + action);
            return "success";
        }

        private static Map<String, Long> expectedAmounts(String envName) {
            String raw = System.getenv(envName);
            Map<String, Long> result = new LinkedHashMap<>();
            if (raw == null || raw.isBlank()) return result;
            for (String entry : raw.split(",")) {
                int eq = entry.indexOf('=');
                if (eq <= 0 || eq == entry.length() - 1) {
                    throw new IllegalStateException(envName + " format must be businessNo=amountFen[,businessNo=amountFen]");
                }
                String businessNo = entry.substring(0, eq).trim();
                long amount = Long.parseLong(entry.substring(eq + 1).trim());
                if (businessNo.isEmpty() || amount < 0 || result.putIfAbsent(businessNo, amount) != null) {
                    throw new IllegalStateException(envName + " contains invalid or duplicate business order");
                }
            }
            return result;
        }

        private static boolean hasText(Object value) {
            return value != null && !String.valueOf(value).isBlank() && !"null".equals(String.valueOf(value));
        }

        private static String requiredText(Map<String, Object> params, String field) {
            Object value = params.get(field);
            if (!hasText(value)) throw new IllegalArgumentException("missing notification field: " + field);
            return String.valueOf(value);
        }

        private static long requiredLong(Map<String, Object> params, String field) {
            String value = requiredText(params, field);
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid numeric notification field: " + field);
            }
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
