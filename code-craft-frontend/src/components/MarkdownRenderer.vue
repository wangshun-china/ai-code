<template>
  <div class="markdown-body" v-html="renderedMarkdown"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'

// 引入样式
import 'highlight.js/styles/github.css'
import 'github-markdown-css/github-markdown-light.css'

interface Props {
  content: string
}

const props = defineProps<Props>()

// 配置 markdown-it 实例
const md: MarkdownIt = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true,
  highlight: function (str: string, lang: string): string {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return (
          '<pre class="hljs"><code>' +
          hljs.highlight(str, { language: lang, ignoreIllegals: true }).value +
          '</code></pre>'
        )
      } catch {
        // 忽略错误，使用默认处理
      }
    }
    return '<pre class="hljs"><code>' + md.utils.escapeHtml(str) + '</code></pre>'
  },
})

// 计算渲染后的 Markdown
const renderedMarkdown = computed(() => {
  return md.render(props.content)
})
</script>

<style scoped>
/* 基于 github-markdown-css 的自定义覆盖 */
.markdown-body {
  line-height: 1.6;
  font-size: 15px;
  color: #000000;
}

.markdown-body :deep(p) {
  color: #000000;
  margin-bottom: 12px;
}

.markdown-body :deep(li) {
  color: #000000;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  color: #000000;
  font-weight: 600;
}

/* 代码块样式优化 */
.markdown-body :deep(pre) {
  background-color: #f6f8fa;
  border-radius: 6px;
  padding: 16px;
  overflow-x: auto;
}

.markdown-body :deep(code) {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.9em;
}

.markdown-body :deep(pre code) {
  background-color: transparent;
  padding: 0;
}

/* 行内代码样式 */
.markdown-body :deep(:not(pre) > code) {
  background-color: rgba(175, 184, 193, 0.2);
  padding: 0.2em 0.4em;
  border-radius: 6px;
}

/* 代码高亮优化 */
.markdown-body :deep(.hljs) {
  background-color: #f6f8fa !important;
}
</style>