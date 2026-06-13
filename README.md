# 三希智付 API 文档

基于 VitePress 构建的三希智付支付系统接口文档。

## 🚀 快速开始

### 安装依赖
```bash
npm install
```

### 开发模式
```bash
npm run docs:dev
```
访问 `http://localhost:5173` 查看文档

### 构建生产版本
```bash
npm run docs:build
```

### 预览生产版本
```bash
npm run docs:preview
```

## 📁 文档结构

```
docs/
├── .vitepress/
│   └── config.js          # VitePress 配置文件
├── index.md               # 首页
├── signature-rules.md     # 签名规则
├── payment-api.md         # 支付接口
├── refund-api.md          # 退款接口
├── transfer-api.md        # 转账接口
├── split-api.md           # 分账接口
└── pos/                   # 扫码POS接口
    ├── common-params.md   # 公共参数&签名
    ├── device-init.md     # 设备初始化
    ├── device-payment.md  # 设备支付
    ├── payment-query.md   # 支付订单查询
    ├── refund.md          # 发起退款
    ├── refund-query.md    # 退款订单查询
    ├── summary-query.md   # 汇总查询
    └── transaction-query.md # 交易记录查询
```

## 🔧 配置说明

VitePress 配置文件位于 `docs/.vitepress/config.js`，包含：

- 站点标题和描述
- 导航菜单配置
- 侧边栏配置
- 搜索功能配置
- 主题配置

## 📚 功能特性

- ✅ 响应式设计，支持移动端
- ✅ 内置搜索功能
- ✅ 自动生成侧边栏导航
- ✅ 支持中文文档
- ✅ 代码高亮
- ✅ 快速部署

## 🚀 部署

### GitHub Pages
1. 将代码推送到 GitHub 仓库
2. 在仓库设置中启用 GitHub Pages
3. 选择 GitHub Actions 作为构建源
4. 创建 `.github/workflows/deploy.yml` 文件

### Vercel
1. 连接 Vercel 到你的 GitHub 仓库
2. 设置构建命令为 `npm run docs:build`
3. 设置输出目录为 `docs/.vitepress/dist`

### Netlify
1. 连接 Netlify 到你的 GitHub 仓库
2. 设置构建命令为 `npm run docs:build`
3. 设置发布目录为 `docs/.vitepress/dist`

## 📝 更新文档

1. 直接编辑 `docs/` 目录下的 Markdown 文件
2. 在开发模式下预览更改
3. 提交更改并推送到仓库
4. 自动部署（如果配置了 CI/CD）

## 🎨 自定义主题

VitePress 支持自定义主题，你可以：

1. 修改 `docs/.vitepress/config.js` 中的主题配置
2. 添加自定义 CSS 样式
3. 创建自定义组件
4. 配置插件和扩展

更多详情请参考 [VitePress 官方文档](https://vitepress.dev/)。 