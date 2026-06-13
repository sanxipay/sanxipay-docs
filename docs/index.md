---
# https://vitepress.dev/reference/default-theme-home-page
layout: home

hero:
  name: "三希智付"
  text: "支付系统接口文档"
  tagline: 完整的支付、退款、转账、分账接口文档
  actions:
    - theme: brand
      text: 开始使用
      link: /0_signature-rules
    - theme: alt
      text: 查看 GitHub
      link: https://github.com/sanxipay/sanxipay-docs

features:
  - title: 📋 签名规则
    details: 详细的API签名算法和参数规范说明
    link: /0_signature-rules
  - title: 💳 支付接口
    details: 完整的支付、退款、转账、分账接口文档
    link: /1_payment-api
  - title: 📱 扫码POS接口
    details: 专用的POS设备接口文档，包含设备初始化、支付、查询等功能
    link: /pos/1_common-params
  - title: 🔍 搜索功能
    details: 内置搜索功能，快速找到所需的接口文档
  - title: 📖 完整文档
    details: 包含请求参数、返回参数、示例代码等完整信息
  - title: 🚀 快速部署
    details: 基于 VitePress 构建，支持快速部署和自定义主题
--- 