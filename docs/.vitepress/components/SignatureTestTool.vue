<template>
  <div class="signature-tool-container">
    <!-- 示例数据区域 -->
    <div class="example-section">
      <h3>📝 快速导入数据</h3>
      <div style="margin-bottom: 15px;">
        <button type="button" class="btn btn-success" @click="loadExample">
          🚀 加载官方示例
        </button>
        <button type="button" class="btn btn-primary" @click="loadServerLogExample" style="margin-left: 10px;">
          🔧 加载服务器日志示例
        </button>
        <button type="button" class="btn btn-primary" @click="toggleJsonImport" style="margin-left: 10px;">
          📋 JSON导入
        </button>
        <button type="button" class="btn btn-secondary" @click="toggleUrlImport" style="margin-left: 10px;">
          🔗 URL参数导入
        </button>
      </div>
      
      <div v-show="showJsonImport" style="margin-top: 20px;">
        <label for="jsonInput" style="display: block; margin-bottom: 8px; font-weight: 600; color: #1e40af;">
          粘贴JSON数据：
        </label>
        <textarea 
          v-model="jsonInput" 
          class="form-control" 
          style="min-height: 200px; font-family: 'Courier New', monospace;" 
          placeholder='粘贴JSON数据，例如:
{
    "version": "1.0",
    "signType": "MD5",
    "reqTime": "2023-10-19T12:00:00Z",
    "mchNo": "M1748248715",
    "appId": "6834288be4b08df6d75dbd00",
    "mchOrderNo": "ORDER20250111-02",
    "wayCode": "WX_LITE",
    "amount": 1,
    "currency": "CNY",
    "clientIp": "192.168.1.1",
    "subject": "商品标题1",
    "body": "商品描述信息",
    "notifyUrl": "http://example.com/notify",
    "returnUrl": "http://example.com/return",
    "expiredTime": 3600,
    "channelExtra": "{\"Openid\": \"xxx\"}",
    "extParam": "{}",
    "divisionMode": 0
}'></textarea>
        <div style="margin-top: 10px;">
          <button type="button" class="btn btn-primary" @click="importFromJson">
            ✨ 导入数据
          </button>
          <button type="button" class="btn btn-secondary" @click="cleanJsonFormat" style="margin-left: 10px;">
            🧹 清理JSON格式
          </button>
        </div>
      </div>

      <div v-show="showUrlImport" style="margin-top: 20px;">
        <label for="urlInput" style="display: block; margin-bottom: 8px; font-weight: 600; color: #1e40af;">
          粘贴URL参数字符串：
        </label>
        <textarea 
          v-model="urlInput" 
          class="form-control" 
          style="min-height: 150px; font-family: 'Courier New', monospace;" 
          placeholder='粘贴URL参数字符串，例如:
amount=1&appId=6834288be4b08df6d75dbd00&body=商品描述信息&channelExtra={"Openid": "xxx"}&clientIp=192.168.1.1&currency=CNY&divisionMode=0&expiredTime=3600&extParam={}&mchNo=M1748248715&mchOrderNo=ORDER20250111-02&notifyUrl=http://example.com/notify&reqTime=20231019200000&returnUrl=http://example.com/return&sign=F8DF232926CE4C3F7AD734136900E20A&signType=MD5&subject=商品标题1&version=1.0&wayCode=WX_LITE'></textarea>
        <div style="margin-top: 10px;">
          <button type="button" class="btn btn-primary" @click="importFromUrl">
            ✨ 导入URL参数
          </button>
        </div>
      </div>
      
      <p style="margin-top: 10px; color: #1e40af; font-size: 0.9rem;">
        可以加载官方示例验证算法，或直接粘贴JSON数据/URL参数快速填充表单。支持导入现有签名进行验证。
      </p>
    </div>

    <div class="form-section">
      <h2>🔐 签名参数配置</h2>
      
      <div class="form-group">
        <label>私钥 (key) *</label>
        <input type="text" v-model="formData.privateKey" class="form-control" placeholder="例如: your_private_key_here" required>
        <small style="color: #6b7280; margin-top: 5px; display: block;">
          商户私钥，用于签名计算
        </small>
      </div>

      <div class="form-row">
        <div class="form-group">
          <label>商户订单号 (mchOrderNo)</label>
          <input type="text" v-model="formData.mchOrderNo" class="form-control" placeholder="例如: P0123456789101">
        </div>
        <div class="form-group">
          <label>交易金额-分 (amount)</label>
          <input type="number" v-model="formData.amount" class="form-control" placeholder="例如: 10000">
        </div>
      </div>

      <div class="form-row">
        <div class="form-group">
          <label>商户号 (mchNo)</label>
          <input type="text" v-model="formData.mchNo" class="form-control" placeholder="例如: M1748248715">
        </div>
        <div class="form-group">
          <label>应用ID (appId)</label>
          <input type="text" v-model="formData.appId" class="form-control" placeholder="例如: 6834288be4b08df6d75dbd00">
        </div>
      </div>

      <div class="form-row">
        <div class="form-group">
          <label>支付方式 (wayCode)</label>
          <input type="text" v-model="formData.wayCode" class="form-control" placeholder="例如: WX_LITE">
        </div>
        <div class="form-group">
          <label>货币类型 (currency)</label>
          <input type="text" v-model="formData.currency" class="form-control" placeholder="例如: CNY">
        </div>
      </div>

      <div class="form-row">
        <div class="form-group">
          <label>客户端IP (clientIp)</label>
          <input type="text" v-model="formData.clientIp" class="form-control" placeholder="例如: 192.168.0.111">
        </div>
        <div class="form-group">
          <label>版本号 (version)</label>
          <input type="text" v-model="formData.version" class="form-control" placeholder="例如: 1.0">
        </div>
      </div>

      <div class="form-row">
        <div class="form-group">
          <label>商品标题 (subject)</label>
          <input type="text" v-model="formData.subject" class="form-control" placeholder="例如: 商品标题1">
        </div>
        <div class="form-group">
          <label>商品描述 (body)</label>
          <input type="text" v-model="formData.body" class="form-control" placeholder="例如: 商品描述信息">
        </div>
      </div>

      <div class="form-row">
        <div class="form-group">
          <label>返回地址 (returnUrl)</label>
          <input type="url" v-model="formData.returnUrl" class="form-control" placeholder="例如: https://www.baidu.com">
        </div>
        <div class="form-group">
          <label>通知地址 (notifyUrl)</label>
          <input type="url" v-model="formData.notifyUrl" class="form-control" placeholder="例如: https://www.baidu.com">
        </div>
      </div>

      <div class="form-row">
        <div class="form-group">
          <label>过期时间-秒 (expiredTime)</label>
          <input type="number" v-model="formData.expiredTime" class="form-control" placeholder="例如: 3600">
        </div>
        <div class="form-group">
          <label>分账模式 (divisionMode)</label>
          <input type="number" v-model="formData.divisionMode" class="form-control" placeholder="例如: 0">
        </div>
      </div>

      <div class="form-group">
        <label>渠道扩展参数 (channelExtra)</label>
        <input type="text" v-model="formData.channelExtra" class="form-control" placeholder='例如: {"Openid": "oimSM7dQP4OXVU2GIaBeoxp0ZMOI"}'>
      </div>

      <div class="form-group">
        <label>扩展参数 (extParam)</label>
        <input type="text" v-model="formData.extParam" class="form-control" placeholder="例如: {}">
      </div>

      <div class="form-group">
        <label>请求时间 (reqTime) - 格式: yyyyMMddHHmmss</label>
        <input type="text" v-model="formData.reqTime" class="form-control" placeholder="留空自动生成当前时间">
      </div>

      <div class="form-group">
        <label>待验证的签名 (用于验证现有签名)</label>
        <input type="text" v-model="formData.existingSign" class="form-control" placeholder="粘贴需要验证的签名">
      </div>

      <div class="form-group">
        <label style="display: flex; align-items: center; gap: 10px;">
          <input type="checkbox" v-model="includeSignType" style="margin: 0;" disabled>
          在签名计算中包含 signType 参数 (必需)
        </label>
        <small style="color: #6b7280; margin-top: 5px; display: block;">
          统一下单API必须在签名计算中包含 signType=MD5 参数
        </small>
      </div>

      <div class="form-group">
        <label style="display: flex; align-items: center; gap: 10px;">
          <input type="checkbox" v-model="includePlatId" style="margin: 0;">
          在签名计算中包含 platId 参数
        </label>
        <small style="color: #6b7280; margin-top: 5px; display: block;">
          仅在通用签名示例中使用，统一下单API不需要此参数
        </small>
      </div>

      <div class="form-group">
        <label for="apiType">API类型选择:</label>
        <select id="apiType" v-model="apiType" class="form-control" @change="onApiTypeChange">
          <option value="unified_order">统一下单API</option>
          <option value="generic">通用签名示例</option>
        </select>
        <small style="color: #6b7280; margin-top: 5px; display: block;">
          不同API的签名参数要求不同
        </small>
      </div>

      <div class="form-group">
        <label style="display: flex; align-items: center; gap: 10px;">
          <input type="checkbox" v-model="useIsoTimeFormat" style="margin: 0;">
          使用ISO时间格式 (如: 2023-10-19T12:00:00Z)
        </label>
        <small style="color: #6b7280; margin-top: 5px; display: block;">
          默认使用 yyyyMMddHHmmss 格式，勾选此项使用ISO格式
        </small>
      </div>

      <button type="button" class="btn btn-primary" @click="generateSignature">
        🔒 生成签名
      </button>
      <button type="button" class="btn btn-secondary" @click="clearForm">
        🗑️ 清空表单
      </button>
      <button type="button" class="btn btn-info" @click="toggleServerLogSection" style="margin-left: 10px;">
        📋 服务器日志对比
      </button>
    </div>

    <!-- 服务器日志对比区域 -->
    <div v-show="showServerLogSection" class="form-section">
      <h2>📋 服务器日志对比分析</h2>
      <div class="form-group">
        <label>服务器日志内容:</label>
        <textarea v-model="serverLog" class="form-control" style="min-height: 150px; font-family: 'Courier New', monospace;" placeholder="请粘贴服务器日志，包含 signStr 和 sign 信息...

示例格式:
signStr:amount=1&appId=xxx&...&key=xxx
2025-05-26 15:18:35.165 INFO [xxx] - sign:661582785BF8F33D7C5E6148ECFDB960"></textarea>
      </div>
      <button type="button" class="btn btn-primary" @click="analyzeServerLog">
        🔍 解析并对比
      </button>
      <button type="button" class="btn btn-secondary" @click="clearServerLog" style="margin-left: 10px;">
        🗑️ 清空日志
      </button>
      
      <!-- 服务器日志解析结果 -->
      <div v-if="showServerLogResult" class="result-section" style="margin-top: 20px;">
        <h4>📊 服务器日志解析结果</h4>
        <div class="result-item">
          <label>服务器签名字符串:</label>
          <div class="result-value" style="font-family: 'Courier New', monospace; word-break: break-all;">{{ serverSignStr }}</div>
        </div>
        <div class="result-item">
          <label>服务器签名值:</label>
          <div class="result-value" style="font-family: 'Courier New', monospace;">{{ serverSignValue }}</div>
        </div>
      </div>
    </div>

    <div v-if="showResult" class="result-section">
      <h3>📋 签名结果</h3>
      <div class="result-item">
        <label>生成的签名 (sign):</label>
        <div class="result-value">{{ generatedSignature }}</div>
      </div>
      <div class="result-item">
        <label>完整请求参数 (包含签名):</label>
        <div class="result-value">{{ fullRequestParams }}</div>
      </div>
      <div class="result-item">
        <label>URL编码后的请求参数:</label>
        <div class="result-value">{{ encodedRequestParams }}</div>
      </div>
    </div>

    <div v-if="signatureSteps.length > 0" class="steps-section">
      <h3>🔍 签名生成详细步骤</h3>
      <div v-for="(step, index) in signatureSteps" :key="index" :class="['step', { 'comparison-step': step.isComparison }]">
        <div class="step-title">{{ step.title }}</div>
        <div :class="['step-content', { 'comparison-content': step.isComparison }]" v-html="formatStepContent(step.content)"></div>
      </div>
    </div>

    <div v-if="errorMessage" class="error">
      ❌ {{ errorMessage }}
    </div>

    <div v-if="successMessage" class="success">
      ✅ {{ successMessage }}
    </div>

    <!-- Detailed Comparison Display -->
    <div v-if="showComparison && comparisonResult && comparisonResult.paramComparison" class="comparison-section">
      <h4>签名字符串对比分析 (生成签名 vs 服务端日志):</h4>
      <p v-if="comparisonResult.identical" class="success-message">字符串完全一致！</p>
      <div v-else>
        <p><strong>我们的签名字符串 (长度: {{ comparisonResult.length1 }}):</strong></p>
        <pre class="code-block"><code>{{ lastGeneratedSignContent }}</code></pre>
        <p><strong>服务器的签名字符串 (长度: {{ comparisonResult.length2 }}):</strong></p>
        <pre class="code-block"><code>{{ serverLogSignatureBaseString }}</code></pre>

        <h5>参数逐个对比:</h5>
        <table class="comparison-table">
          <thead>
            <tr>
              <th>参数名</th>
              <th>我们的值 (解码后)</th>
              <th>服务器的值 (解码后)</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in comparisonResult.paramComparison" :key="item.key" :class="{ 'mismatch': !item.match, 'missing-in-one': !item.presentInStr1 || !item.presentInStr2 }">
              <td><code>{{ item.key }}</code></td>
              <td>
                <pre v-if="item.presentInStr1">{{ item.value1 }}</pre>
                <span v-else class="missing-text">N/A</span>
                <div v-if="item.key === 'channelExtra' && item.decodedValue1" class="decoded-value">
                  <small>解码后: {{ item.decodedValue1 }}</small>
                  <small v-if="item.jsonValue1">JSON: {{ JSON.stringify(item.jsonValue1) }}</small>
                  <small v-if="item.jsonError" class="error-text">JSON解析错误: {{item.jsonError}}</small>
                </div>
                <small v-if="item.key === 'channelExtra' && item.decodeError" class="error-text">解码错误: {{item.decodeError}}</small>
              </td>
              <td>
                <pre v-if="item.presentInStr2">{{ item.value2 }}</pre>
                <span v-else class="missing-text">N/A</span>
                <div v-if="item.key === 'channelExtra' && item.decodedValue2" class="decoded-value">
                  <small>解码后: {{ item.decodedValue2 }}</small>
                  <small v-if="item.jsonValue2">JSON: {{ JSON.stringify(item.jsonValue2) }}</small>
                  <small v-if="item.jsonError && !item.jsonValue1" class="error-text">JSON解析错误: {{item.jsonError}}</small>
                </div>
                <small v-if="item.key === 'channelExtra' && item.decodeError && !item.decodedValue1" class="error-text">解码错误: {{item.decodeError}}</small>
              </td>
              <td>
                <span v-if="!item.presentInStr1" class="status-badge missing">我方缺少</span>
                <span v-else-if="!item.presentInStr2" class="status-badge missing">对方缺少</span>
                <span v-else-if="item.match" class="status-badge match">一致</span>
                <span v-else class="status-badge mismatch">不一致</span>
              </td>
            </tr>
          </tbody>
        </table>

        <h5>字符级别差异 (最多显示前10个):</h5>
        <div v-if="comparisonResult.charDifferences && comparisonResult.charDifferences.length > 0">
          <ul>
            <li v-for="(diff, index) in comparisonResult.charDifferences.slice(0, 10)" :key="index">
              索引 {{ diff.index }}: 我方 '<code>{{ diff.char1 }}</code>' vs. 服务端 '<code>{{ diff.char2 }}</code>'
            </li>
          </ul>
          <p v-if="comparisonResult.charDifferences.length > 10">...等共 {{ comparisonResult.charDifferences.length }} 处差异。</p>
        </div>
        <p v-else>无字符级别差异 (若参数值不同, 可能为类型或编码问题)。</p>

        <h5>智能建议:</h5>
        <ul v-if="comparisonResult.suggestions && comparisonResult.suggestions.length > 0">
          <li v-for="(suggestion, index) in comparisonResult.suggestions" :key="index" v-html="formatStepContent(suggestion)"></li>
        </ul>
        <p v-else>暂无智能建议。</p>
      </div>
    </div>

    <!-- Custom Comparison Display -->
    <div v-if="showCustomComparisonResult && customComparisonResult" class="comparison-section">
      <h4>自定义对比分析结果:</h4>
      <p v-if="customComparisonResult.error" class="error-message">{{ customComparisonResult.error }}</p>
      <p v-else-if="customComparisonResult.identical" class="success-message">字符串完全一致！</p>
      <div v-else>
        <p><strong>字符串1 (长度: {{ customComparisonResult.length1 }}):</strong></p>
        <pre class="code-block"><code>{{ customStr1 }}</code></pre>
        <p><strong>字符串2 (长度: {{ customComparisonResult.length2 }}):</strong></p>
        <pre class="code-block"><code>{{ customStr2 }}</code></pre>
        
        <h5>参数逐个对比:</h5>
        <table class="comparison-table">
          <thead>
            <tr>
              <th>参数名</th>
              <th>字符串1的值 (解码后)</th>
              <th>字符串2的值 (解码后)</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in customComparisonResult.paramComparison" :key="item.key" :class="{ 'mismatch': !item.match, 'missing-in-one': !item.presentInStr1 || !item.presentInStr2 }">
              <td><code>{{ item.key }}</code></td>
              <td>
                <pre v-if="item.presentInStr1">{{ item.value1 }}</pre>
                <span v-else class="missing-text">N/A</span>
                <div v-if="item.key === 'channelExtra' && item.decodedValue1" class="decoded-value">
                  <small>解码后: {{ item.decodedValue1 }}</small>
                  <small v-if="item.jsonValue1">JSON: {{ JSON.stringify(item.jsonValue1) }}</small>
                  <small v-if="item.jsonError" class="error-text">JSON解析错误: {{item.jsonError}}</small>
                </div>
                <small v-if="item.key === 'channelExtra' && item.decodeError" class="error-text">解码错误: {{item.decodeError}}</small>
              </td>
              <td>
                <pre v-if="item.presentInStr2">{{ item.value2 }}</pre>
                <span v-else class="missing-text">N/A</span>
                <div v-if="item.key === 'channelExtra' && item.decodedValue2" class="decoded-value">
                  <small>解码后: {{ item.decodedValue2 }}</small>
                  <small v-if="item.jsonValue2">JSON: {{ JSON.stringify(item.jsonValue2) }}</small>
                  <small v-if="item.jsonError && !item.jsonValue1" class="error-text">JSON解析错误: {{item.jsonError}}</small> 
                </div>
                <small v-if="item.key === 'channelExtra' && item.decodeError && !item.decodedValue1" class="error-text">解码错误: {{item.decodeError}}</small>
              </td>
              <td>
                <span v-if="!item.presentInStr1" class="status-badge missing">字符串1缺少</span>
                <span v-else-if="!item.presentInStr2" class="status-badge missing">字符串2缺少</span>
                <span v-else-if="item.match" class="status-badge match">一致</span>
                <span v-else class="status-badge mismatch">不一致</span>
              </td>
            </tr>
          </tbody>
        </table>

        <h5>字符级别差异 (最多显示前10个):</h5>
        <div v-if="customComparisonResult.charDifferences && customComparisonResult.charDifferences.length > 0">
          <ul>
            <li v-for="(diff, index) in customComparisonResult.charDifferences.slice(0, 10)" :key="index">
              索引 {{ diff.index }}: 字符串1 '<code>{{ diff.char1 }}</code>' vs. 字符串2 '<code>{{ diff.char2 }}</code>'
            </li>
          </ul>
          <p v-if="customComparisonResult.charDifferences.length > 10">...等共 {{ customComparisonResult.charDifferences.length }} 处差异。</p>
        </div>
        <p v-else>无字符级别差异 (若参数值不同, 可能为类型或编码问题)。</p>

        <h5>智能建议:</h5>
        <ul v-if="customComparisonResult.suggestions && customComparisonResult.suggestions.length > 0">
          <li v-for="(suggestion, index) in customComparisonResult.suggestions" :key="index" v-html="formatStepContent(suggestion)"></li>
        </ul>
        <p v-else>暂无智能建议。</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import CryptoJS from 'crypto-js'
import { compareSignatureStrings } from './comparisonUtils.js'
import { cleanParameterValue, cleanJsonParameter, formatStepContent } from './stringUtils.js'

// 响应式数据
const showJsonImport = ref(false)
const showUrlImport = ref(false)
const jsonInput = ref('')
const urlInput = ref('')
const showResult = ref(false)
const generatedSignature = ref('')
const fullRequestParams = ref('')
const encodedRequestParams = ref('')
const signatureSteps = ref([])
const errorMessage = ref('')
const successMessage = ref('')
const includeSignType = ref(true)  // 默认包含signType，因为统一下单API需要
const includePlatId = ref(false)  // 默认不包含platId，统一下单API不需要
const apiType = ref('unified_order')  // 默认为统一下单API
const useIsoTimeFormat = ref(false)
const showServerLogSection = ref(false)
const serverLog = ref('')
const showServerLogResult = ref(false)
const serverSignStr = ref('')
const showComparison = ref(false)
const comparisonResult = ref(null)
const showCustomComparisonResult = ref(false)
const customComparisonResult = ref(null)
const customStr1 = ref('')
const customStr2 = ref('')

// 表单数据
const formData = reactive({
  privateKey: '',
  mchOrderNo: '',
  amount: '',
  mchNo: '',
  appId: '',
  wayCode: '',
  currency: 'CNY',
  clientIp: '',
  version: '1.0',
  subject: '',
  body: '',
  returnUrl: '',
  notifyUrl: '',
  expiredTime: '',
  channelExtra: '',
  extParam: '',
  divisionMode: '',
  reqTime: '',
  existingSign: ''
})

// 方法
const loadExample = () => {
  // 统一下单API示例
  formData.privateKey = 'your_private_key_here'
  formData.mchNo = 'M1748248715'
  formData.appId = '6834288be4b08df6d75dbd00'
  formData.mchOrderNo = 'ORDER20250111-02'
  formData.wayCode = 'WX_LITE'
  formData.amount = '1'
  formData.currency = 'CNY'
  formData.clientIp = '192.168.1.1'
  formData.subject = '商品标题1'
  formData.body = '商品描述信息'
  formData.notifyUrl = 'http://example.com/notify'
  formData.returnUrl = 'http://example.com/return'
  formData.expiredTime = '3600'
  formData.channelExtra = '{"Openid": "xxx"}'
  formData.extParam = '{}'
  formData.divisionMode = '0'
  formData.reqTime = '20231019120000'
  formData.version = '1.0'
  
  showSuccess('已加载统一下单API示例数据！')
}

const loadServerLogExample = () => {
  // 根据服务器日志加载数据
  formData.privateKey = 'your_private_key_here'
  formData.amount = '1'
  formData.appId = '6834288be4b08df6d75dbd00'
  formData.body = '商品描述信息'
  formData.clientIp = '192.168.1.1'
  formData.currency = 'CNY'
  formData.divisionMode = '0'
  formData.expiredTime = '3600'
  formData.extParam = '{}'
  formData.mchNo = 'M1748248715'
  formData.mchOrderNo = 'ORDER20250111-02'
  formData.notifyUrl = 'http://example.com/notify'
  formData.reqTime = '20231019120000'
  formData.returnUrl = 'http://example.com/return'
  formData.subject = '商品标题1'
  formData.version = '1.0'
  formData.wayCode = 'WX_LITE'
  formData.existingSign = '请使用您的实际私钥计算'
  
  showSuccess('已加载服务器日志示例数据！')
}

const toggleJsonImport = () => {
  showJsonImport.value = !showJsonImport.value
  if (showJsonImport.value) {
    showUrlImport.value = false
  }
}

const toggleUrlImport = () => {
  showUrlImport.value = !showUrlImport.value
  if (showUrlImport.value) {
    showJsonImport.value = false
  }
}

const cleanJsonFormat = () => {
  try {
    if (!jsonInput.value.trim()) {
      showError('请先输入JSON数据')
      return
    }

    const cleanedJson = jsonInput.value
      .replace(/[\t\r\n]/g, ' ')
      .replace(/\s+/g, ' ')
      .replace(/,\s*}/g, '}')
      .replace(/,\s*]/g, ']')

    const parsed = JSON.parse(cleanedJson)
    const formatted = JSON.stringify(parsed, null, 4)
    
    jsonInput.value = formatted
    showSuccess('✅ JSON格式已清理并重新格式化！')
    
  } catch (error) {
    showError(`JSON清理失败: ${error.message}\n\n💡 请检查JSON语法是否正确`)
  }
}

const importFromJson = () => {
  try {
    if (!jsonInput.value.trim()) {
      showError('请输入JSON数据')
      return
    }

    const cleanedJson = jsonInput.value
      .replace(/[\t\r\n]/g, ' ')
      .replace(/\s+/g, ' ')
      .replace(/,\s*}/g, '}')
      .replace(/,\s*]/g, ']')

    const data = JSON.parse(cleanedJson)
    
    const fieldMapping = {
      'mchNo': 'mchNo',
      'appId': 'appId',
      'mchOrderNo': 'mchOrderNo',
      'amount': 'amount',
      'wayCode': 'wayCode',
      'currency': 'currency',
      'clientIp': 'clientIp',
      'subject': 'subject',
      'body': 'body',
      'returnUrl': 'returnUrl',
      'notifyUrl': 'notifyUrl',
      'reqTime': 'reqTime',
      'version': 'version',
      'expiredTime': 'expiredTime',
      'channelExtra': 'channelExtra',
      'extParam': 'extParam',
      'divisionMode': 'divisionMode'
    }

    let importedCount = 0
    
    Object.keys(data).forEach(key => {
      if (key === 'sign' || key === 'signType') {
        return
      }
      
      const fieldName = fieldMapping[key] || key
      
      if (formData.hasOwnProperty(fieldName)) {
        let value = data[key]
        
        if (typeof value === 'object' && value !== null) {
          value = JSON.stringify(value)
        }
        
        if (typeof value === 'string') {
          if (key === 'channelExtra' || key === 'extParam') {
            value = cleanJsonParameter(value)
          } else {
            value = cleanParameterValue(value)
          }
        }
        
        formData[fieldName] = value
        importedCount++
      }
    })

    if (importedCount > 0) {
      showSuccess(`✅ 成功导入 ${importedCount} 个字段数据！`)
      showJsonImport.value = false
    } else {
      showError('未找到可导入的有效字段')
    }

  } catch (error) {
    console.error('JSON解析错误详情:', error)
    let errorMsg = `JSON解析失败: ${error.message}`
    
    if (error.message.includes('control character')) {
      errorMsg += '\n\n💡 提示：JSON中包含不可见的控制字符（如制表符），请检查并清理JSON格式。'
    } else if (error.message.includes('Unexpected token')) {
      errorMsg += '\n\n💡 提示：JSON格式不正确，请检查括号、引号、逗号是否匹配。'
    } else if (error.message.includes('Unexpected end')) {
      errorMsg += '\n\n💡 提示：JSON不完整，请检查是否缺少结束括号。'
    }
    
    showError(errorMsg)
  }
}

const generateSignature = (isVerification = false) => {
  try {
    if (!formData.privateKey) {
      showError('私钥是必填字段')
      return
    }

    const params = {}
    
    const fieldList = [
      'mchNo', 'appId', 'mchOrderNo', 'amount', 'wayCode', 'currency',
      'clientIp', 'subject', 'body', 'returnUrl', 'notifyUrl', 
      'expiredTime', 'channelExtra', 'extParam', 'divisionMode'
    ]
    
    fieldList.forEach(fieldName => {
      const value = formData[fieldName]?.toString().trim()
      if (value) {
        if (fieldName === 'channelExtra' || fieldName === 'extParam') {
          params[fieldName] = cleanJsonParameter(value)
        } else {
          params[fieldName] = cleanParameterValue(value)
        }
      }
    })
    
    let reqTime = formData.reqTime?.toString().trim()
    if (!reqTime) {
      const now = new Date()
      if (useIsoTimeFormat.value) {
        reqTime = now.toISOString()
      } else {
        reqTime = now.getFullYear().toString() +
                 (now.getMonth() + 1).toString().padStart(2, '0') +
                 now.getDate().toString().padStart(2, '0') +
                 now.getHours().toString().padStart(2, '0') +
                 now.getMinutes().toString().padStart(2, '0') +
                 now.getSeconds().toString().padStart(2, '0')
      }
      formData.reqTime = reqTime
    }
    if (reqTime) params.reqTime = reqTime
    
    const version = formData.version?.toString().trim()
    if (version) params.version = version

    params.signType = 'MD5'

    const steps = []

    steps.push({
      title: '第一步: 收集所有非空参数',
      content: JSON.stringify(params, null, 2)
    })

    const sortedKeys = Object.keys(params).sort()
    steps.push({
      title: '第二步: 按参数名ASCII码排序（字典序）',
      content: `排序后的参数名: [${sortedKeys.join(', ')}]`
    })

    const stringA = sortedKeys.map(key => `${key}=${params[key]}`).join('&')
    steps.push({
      title: '第三步: 构建stringA（URL键值对格式）',
      content: stringA
    })

    const stringSignTemp = `${stringA}&key=${formData.privateKey}`
    steps.push({
      title: '第四步: 在stringA最后拼接私钥',
      content: stringSignTemp
    })

    const signature = CryptoJS.MD5(stringSignTemp).toString().toUpperCase()
    steps.push({
      title: '第五步: MD5运算并转换为大写',
      content: signature
    })

    const finalParams = { ...params, sign: signature }
    const finalParamsString = Object.keys(finalParams)
      .sort()
      .map(key => `${key}=${finalParams[key]}`)
      .join('&')

    const encodedParamsString = Object.keys(finalParams)
      .sort()
      .map(key => `${key}=${encodeURIComponent(finalParams[key])}`)
      .join('&')

    generatedSignature.value = signature
    fullRequestParams.value = finalParamsString
    encodedRequestParams.value = encodedParamsString
    signatureSteps.value = steps
    showResult.value = true
    hideError()
    showSuccess('🎉 签名生成成功！如需对比验证，请使用"服务器日志对比"功能。')

    if (isVerification) {
      const verificationResult = verifySignature(formData.existingSign)
      if (verificationResult) {
        showSuccess('🎉 签名验证通过！计算的签名与输入的签名完全一致！')
      } else {
        showError('❌ 签名验证失败！请检查参数和签名算法是否正确。')
      }
    }

    return signature
  } catch (error) {
    showError(`签名生成失败: ${error.message}`)
  }
}

const clearForm = () => {
  Object.keys(formData).forEach(key => {
    if (key !== 'version' && key !== 'currency') {
      formData[key] = ''
    }
  })
  
  formData.version = '1.0'
  formData.currency = 'CNY'
  includeSignType.value = true
  useIsoTimeFormat.value = false
  
  showResult.value = false
  showJsonImport.value = false
  showUrlImport.value = false
  showServerLogSection.value = false
  showServerLogResult.value = false
  showComparison.value = false
  showCustomComparisonResult.value = false
  signatureSteps.value = []
  jsonInput.value = ''
  urlInput.value = ''
  serverLog.value = ''
  serverSignStr.value = ''
  comparisonResult.value = null
  customComparisonResult.value = null
  
  hideError()
  hideSuccess()
}

const showError = (message) => {
  hideSuccess()
  errorMessage.value = message
}

const showSuccess = (message) => {
  hideError()
  successMessage.value = message
}

const hideError = () => {
  errorMessage.value = ''
}

const hideSuccess = () => {
  successMessage.value = ''
}

const verifySignature = (existingSign) => {
  try {
    if (!formData.privateKey) {
      showError('私钥是必填字段')
      return false
    }

    if (!existingSign) {
      showError('请输入待验证的签名')
      return false
    }

    const params = {}
    
    const fieldList = [
      'mchNo', 'appId', 'mchOrderNo', 'amount', 'wayCode', 'currency',
      'clientIp', 'subject', 'body', 'returnUrl', 'notifyUrl', 
      'expiredTime', 'channelExtra', 'extParam', 'divisionMode'
    ]
    
    fieldList.forEach(fieldName => {
      const value = formData[fieldName]?.toString().trim()
      if (value) {
        params[fieldName] = value
      }
    })
    
    let reqTime = formData.reqTime?.toString().trim()
    if (!reqTime) {
      showError('验证签名时请求时间(reqTime)不能为空')
      return false
    }
    if (reqTime) params.reqTime = reqTime
    
    const version = formData.version?.toString().trim()
    if (version) params.version = version

    params.signType = 'MD5'

    const steps = []

    steps.push({
      title: '第一步: 收集所有非空参数（不包含sign）',
      content: JSON.stringify(params, null, 2)
    })

    const sortedKeys = Object.keys(params).sort()
    steps.push({
      title: '第二步: 按参数名ASCII码排序（字典序）',
      content: `排序后的参数名: [${sortedKeys.join(', ')}]`
    })

    const stringA = sortedKeys.map(key => `${key}=${params[key]}`).join('&')
    steps.push({
      title: '第三步: 构建stringA（URL键值对格式）',
      content: stringA
    })

    const stringSignTemp = `${stringA}&key=${formData.privateKey}`
    steps.push({
      title: '第四步: 在stringA最后拼接私钥',
      content: stringSignTemp
    })

    const calculatedSignature = CryptoJS.MD5(stringSignTemp).toString().toUpperCase()
    steps.push({
      title: '第五步: MD5运算并转换为大写',
      content: calculatedSignature
    })

    const inputSignature = existingSign.toUpperCase()
    steps.push({
      title: '第六步: 签名验证结果',
      content: `计算签名: ${calculatedSignature}\n输入签名: ${inputSignature}\n验证结果: ${calculatedSignature === inputSignature ? '✅ 验证通过' : '❌ 验证失败'}`
    })

    const finalParams = { ...params, sign: calculatedSignature }
    const finalParamsString = Object.keys(finalParams)
      .sort()
      .map(key => `${key}=${finalParams[key]}`)
      .join('&')

    const encodedParamsString = Object.keys(finalParams)
      .sort()
      .map(key => `${key}=${encodeURIComponent(finalParams[key])}`)
      .join('&')

    generatedSignature.value = calculatedSignature
    fullRequestParams.value = finalParamsString
    encodedRequestParams.value = encodedParamsString
    signatureSteps.value = steps
    showResult.value = true
    hideError()

    return calculatedSignature === inputSignature
  } catch (error) {
    showError(`签名验证失败: ${error.message}`)
    return false
  }
}

const importFromUrl = () => {
  try {
    if (!urlInput.value.trim()) {
      showError('请输入URL参数字符串')
      return
    }

    const params = {}
    const pairs = urlInput.value.trim().split('&')
    
    pairs.forEach(pair => {
      const [key, value] = pair.split('=')
      if (key && value !== undefined) {
        params[key] = decodeURIComponent(value)
      }
    })

    const fieldMapping = {
      'mchNo': 'mchNo',
      'appId': 'appId',
      'mchOrderNo': 'mchOrderNo',
      'amount': 'amount',
      'wayCode': 'wayCode',
      'currency': 'currency',
      'clientIp': 'clientIp',
      'subject': 'subject',
      'body': 'body',
      'returnUrl': 'returnUrl',
      'notifyUrl': 'notifyUrl',
      'reqTime': 'reqTime',
      'version': 'version',
      'expiredTime': 'expiredTime',
      'channelExtra': 'channelExtra',
      'extParam': 'extParam',
      'divisionMode': 'divisionMode',
      'sign': 'existingSign'
    }

    let importedCount = 0
    
    Object.keys(params).forEach(key => {
      if (key === 'signType') {
        return
      }
      
      const fieldName = fieldMapping[key] || key
      
      if (formData.hasOwnProperty(fieldName)) {
        formData[fieldName] = params[key]
        importedCount++
      }
    })

    if (importedCount > 0) {
      showSuccess(`✅ 成功导入 ${importedCount} 个字段数据！${params.sign ? '包含待验证签名' : ''}`)
      showUrlImport.value = false
    } else {
      showError('未找到可导入的有效字段')
    }

  } catch (error) {
    console.error('URL参数解析错误:', error)
    showError(`URL参数解析失败: ${error.message}`)
  }
}

const toggleServerLogSection = () => {
  showServerLogSection.value = !showServerLogSection.value
}

const parseServerLog = (logContent) => {
  const result = {
    signStr: '',
    sign: '',
    success: false,
    error: ''
  }

  try {
    const signStrMatch = logContent.match(/signStr:(.+?)(?=\n|$)/);
    if (signStrMatch) {
      result.signStr = signStrMatch[1].trim();
    }

    const signMatch = logContent.match(/sign:([A-F0-9]+)/i);
    if (signMatch) {
      result.sign = signMatch[1].trim();
    }

    if (result.signStr && result.sign) {
      result.success = true;
    } else {
      result.error = '未能解析出完整的签名信息，请检查日志格式';
    }

  } catch (error) {
    result.error = `日志解析失败: ${error.message}`;
  }

  return result;
}

const analyzeServerLog = () => {
  try {
    if (!serverLog.value.trim()) {
      showError('请输入服务器日志')
      return
    }

    const parseResult = parseServerLog(serverLog.value)
    if (!parseResult.success) {
      showError(parseResult.error)
      return
    }

    serverSignStr.value = parseResult.signStr
    serverSignValue.value = parseResult.sign
    showServerLogResult.value = true

    if (generatedSignature.value) {
      performComparison(parseResult)
    } else {
      showSuccess('服务器日志解析成功！请先生成客户端签名，然后可以进行对比分析。')
    }

  } catch (error) {
    showError(`服务器日志分析失败: ${error.message}`)
  }
}

const performComparison = (serverData) => {
  try {
    let clientSignStr = ''
    
    signatureSteps.value.forEach(step => {
      if (step.title.includes('第四步') || step.title.includes('拼接私钥')) {
        clientSignStr = step.content.trim()
      }
    })

    if (!clientSignStr) {
      showError('请先生成客户端签名')
      return
    }

    const comparisonResult = compareSignatureStrings(clientSignStr, serverData.signStr)
    
    signatureSteps.value.push({
      title: '🔍 与服务器日志对比分析',
      content: comparisonResult,
      isComparison: true
    })

    const clientSign = generatedSignature.value
    const serverSign = serverData.sign
    
    const signMatch = clientSign.toUpperCase() === serverSign.toUpperCase()
    signatureSteps.value.push({
      title: '🔐 签名值对比',
      content: `客户端签名: ${clientSign}\n服务器签名: ${serverSign}\n对比结果: ${signMatch ? '✅ 签名匹配' : '❌ 签名不匹配'}`,
      isComparison: true
    })

    if (signMatch) {
      showSuccess('🎉 签名验证成功！客户端和服务器签名完全一致！')
    } else {
      showError('❌ 签名验证失败！请检查参数和签名算法是否正确。')
    }

  } catch (error) {
    showError(`对比分析失败: ${error.message}`)
  }
}

const clearServerLog = () => {
  serverLog.value = ''
  showServerLogResult.value = false
  
  signatureSteps.value = signatureSteps.value.filter(step => !step.isComparison)
  
  hideError()
  hideSuccess()
}

const onApiTypeChange = () => {
  if (apiType.value === 'unified_order') {
    includePlatId.value = false
    includeSignType.value = true
  } else if (apiType.value === 'generic') {
    includePlatId.value = true
    includeSignType.value = true
  }
}
</script>

<style scoped>
.signature-tool-container {
  max-width: 1200px;
  margin: 0 auto;
}

.form-section {
  background: white;
  padding: 30px;
  border-radius: 15px;
  margin-bottom: 30px;
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.08);
  border: 1px solid #e5e7eb;
}

.form-section h2 {
  color: #374151;
  margin-bottom: 20px;
  font-size: 1.5rem;
  font-weight: 600;
  border-bottom: 2px solid #4f46e5;
  padding-bottom: 10px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 600;
  color: #374151;
  font-size: 0.95rem;
}

.form-control {
  width: 100%;
  padding: 12px 16px;
  border: 2px solid #e5e7eb;
  border-radius: 10px;
  font-size: 1rem;
  transition: all 0.3s ease;
  background: #f9fafb;
}

.form-control:focus {
  outline: none;
  border-color: #4f46e5;
  background: white;
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.1);
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

@media (max-width: 768px) {
  .form-row {
    grid-template-columns: 1fr;
  }
}

.btn {
  padding: 12px 30px;
  border: none;
  border-radius: 10px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.btn-primary {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  color: white;
  box-shadow: 0 4px 15px rgba(79, 70, 229, 0.4);
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(79, 70, 229, 0.4);
}

.btn-secondary {
  background: #6b7280;
  color: white;
  margin-left: 10px;
}

.btn-secondary:hover {
  background: #4b5563;
  transform: translateY(-1px);
}

.btn-success {
  background: #10b981;
  color: white;
  margin-left: 10px;
}

.btn-success:hover {
  background: #059669;
  transform: translateY(-1px);
}

.btn-info {
  background: #0ea5e9;
  color: white;
  margin-left: 10px;
}

.btn-info:hover {
  background: #0284c7;
  transform: translateY(-1px);
}

.result-section {
  background: #f8fafc;
  border: 2px solid #e2e8f0;
  border-radius: 15px;
  padding: 25px;
  margin-top: 20px;
}

.result-section h3 {
  color: #1e293b;
  margin-bottom: 15px;
  font-size: 1.3rem;
}

.result-item {
  background: white;
  padding: 15px;
  border-radius: 8px;
  margin-bottom: 15px;
  border-left: 4px solid #4f46e5;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.result-item label {
  font-weight: 600;
  color: #374151;
  display: block;
  margin-bottom: 5px;
}

.result-value {
  font-family: 'Courier New', monospace;
  background: #f1f5f9;
  padding: 10px;
  border-radius: 6px;
  word-break: break-all;
  font-size: 0.9rem;
  border: 1px solid #cbd5e1;
}

.steps-section {
  background: #fef3c7;
  border: 2px solid #fbbf24;
  border-radius: 15px;
  padding: 25px;
  margin-top: 20px;
}

.steps-section h3 {
  color: #92400e;
  margin-bottom: 15px;
}

.step {
  background: white;
  padding: 15px;
  border-radius: 8px;
  margin-bottom: 10px;
  border-left: 4px solid #fbbf24;
}

.step-title {
  font-weight: 600;
  color: #92400e;
  margin-bottom: 5px;
}

.step-content {
  font-family: 'Courier New', monospace;
  font-size: 0.9rem;
  color: #451a03;
  background: #fffbeb;
  padding: 8px;
  border-radius: 4px;
  word-break: break-all;
}

.comparison-step {
  background: #f0f9ff;
  border-left: 4px solid #0ea5e9;
}

.comparison-step .step-title {
  color: #0c4a6e;
}

.comparison-content {
  background: #f8fafc;
  color: #334155;
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', monospace;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: normal;
  overflow-x: auto;
}

.comparison-content strong {
  color: #1e40af;
  font-weight: 700;
}

.error {
  background: #fef2f2;
  border: 2px solid #fca5a5;
  color: #dc2626;
  padding: 15px;
  border-radius: 10px;
  margin-top: 10px;
}

.success {
  background: #f0fdf4;
  border: 2px solid #86efac;
  color: #16a34a;
  padding: 15px;
  border-radius: 10px;
  margin-top: 10px;
}

.example-section {
  background: #eff6ff;
  border: 2px solid #3b82f6;
  border-radius: 15px;
  padding: 25px;
  margin-bottom: 30px;
}

.example-section h3 {
  color: #1e40af;
  margin-bottom: 15px;
}

@media (max-width: 768px) {
  .btn {
    width: 100%;
    margin-bottom: 10px;
    margin-left: 0;
  }
}

.comparison-section {
  margin-top: 20px;
  padding: 15px;
  border: 1px solid #ddd;
  border-radius: 4px;
  background-color: #f9f9f9;
}
.comparison-section h4 {
  margin-top: 0;
  border-bottom: 1px solid #eee;
  padding-bottom: 10px;
  margin-bottom: 15px;
}
.comparison-section h5 {
  margin-top: 15px;
  margin-bottom: 10px;
}
.code-block {
  background-color: #f0f0f0;
  padding: 10px;
  border-radius: 4px;
  overflow-x: auto;
  word-break: break-all;
}
.comparison-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 15px;
}
.comparison-table th, .comparison-table td {
  border: 1px solid #ddd;
  padding: 8px;
  text-align: left;
  vertical-align: top;
}
.comparison-table th {
  background-color: #f2f2f2;
}
.comparison-table .mismatch td {
  background-color: #ffe0e0; /* Light red for mismatches */
}
.comparison-table .missing-in-one td {
  background-color: #fff5e0; /* Light orange for missing params */
}
.status-badge {
  padding: 3px 7px;
  border-radius: 10px;
  font-size: 0.85em;
  font-weight: bold;
}
.status-badge.match {
  background-color: #d4edda; /* Green */
  color: #155724;
}
.status-badge.mismatch {
  background-color: #f8d7da; /* Red */
  color: #721c24;
}
.status-badge.missing {
  background-color: #fff3cd; /* Yellow */
  color: #856404;
}
.error-message {
  color: #D8000C; /* Red */
  background-color: #FFD2D2;
  border: 1px solid;
  padding: 10px;
  border-radius: 3px;
}
.success-message {
  color: #270; /* Green */
  background-color: #DFF2BF;
  border: 1px solid;
  padding: 10px;
  border-radius: 3px;
}
.missing-text {
  color: #888;
  font-style: italic;
}
.decoded-value {
  margin-top: 5px;
  font-size: 0.9em;
}
.decoded-value small {
  display: block;
  margin-top: 3px;
  color: #555;
  word-break: break-all;
}
.error-text {
  color: #D8000C;
  font-weight: bold;
}
</style> 