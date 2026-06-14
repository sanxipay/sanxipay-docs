import { defineConfig } from 'vitepress'

export default defineConfig({
  title: '三希智付 API 文档',
  description: '三希智付支付系统接口文档',
  lang: 'zh-CN',
  
  head: [
    ['link', { rel: 'icon', href: '/favicon.ico' }],
    ['meta', { name: 'theme-color', content: '#3c82f6' }]
  ],
  
  markdown: {
    attrs: {
      disable: true
    },
    lineNumbers: true
  },
  
  vue: {
    template: {
      compilerOptions: {
        isCustomElement: (tag) => tag.includes('-')
      }
    }
  },
  
  themeConfig: {
    nav: [
      { text: '首页', link: '/' },
      { text: 'API 接口', link: '/0_signature-rules' },
      { text: 'POS 接口', link: '/pos/1_common-params' },
      {
        text: '相关链接',
        items: [
          { text: '官方网站', link: 'https://pay.sanxipay.com' },
          { text: '商户后台', link: 'https://mch.sanxipay.com' }
        ]
      }
    ],

    sidebar: [
      {
        text: '基础规范',
        collapsed: false,
        items: [
          { text: '签名规则', link: '/0_signature-rules' },
          { text: '签名测试工具', link: '/signature-test-tool' },
          { text: '常见错误', link: '/常见错误' }
        ]
      },
      {
        text: '支付接口',
        collapsed: false,
        items: [
          { text: '支付接口', link: '/1_payment-api' },
          { text: '退款接口', link: '/2_refund-api' },
          { text: '转账接口', link: '/3_transfer-api' },
          { text: '分账接口', link: '/4_split-api' }
        ]
      },
      {
        text: '扫码POS接口',
        collapsed: false,
        items: [
          { text: '公共参数&签名', link: '/pos/1_common-params' },
          { text: '设备初始化', link: '/pos/device-init' },
          { text: '设备支付', link: '/pos/device-payment' },
          { text: '支付订单查询', link: '/pos/payment-query' },
          { text: '发起退款', link: '/pos/refund' },
          { text: '退款订单查询', link: '/pos/refund-query' },
          { text: '汇总查询', link: '/pos/summary-query' },
          { text: '交易记录查询', link: '/pos/transaction-query' }
        ]
      }
    ],

    editLink: {
      pattern: 'https://github.com/sanxipay/sanxipay-docs/edit/main/docs/:path',
      text: '在 GitHub 上编辑此页'
    },

    lastUpdated: {
      text: '最后更新于',
      formatOptions: {
        dateStyle: 'short',
        timeStyle: 'medium'
      }
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/sanxipay/sanxipay-docs' }
    ],

    footer: {
      message: '三希智付 API Documentation',
      copyright: 'Copyright © 2026 徐州三希智付信息科技有限公司 | <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener">苏ICP备2026012613号-1</a>'
    },

    search: {
      provider: 'local',
      options: {
        locales: {
          zh: {
            translations: {
              button: {
                buttonText: '搜索文档',
                buttonAriaLabel: '搜索文档'
              },
              modal: {
                noResultsText: '无法找到相关结果',
                resetButtonTitle: '清除查询条件',
                footer: {
                  selectText: '选择',
                  navigateText: '切换'
                }
              }
            }
          }
        }
      }
    },

    outline: {
      level: [2, 3],
      label: '页面导航'
    }
  }
}) 