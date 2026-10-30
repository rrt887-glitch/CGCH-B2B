<script lang="ts" setup>
/** 翻译设置（与原网站一致）：聊天消息实时翻译、输入框双语模式。个人偏好，保存在本机浏览器。 */
import { reactive, watch } from 'vue';

import { Switch } from 'ant-design-vue';

import { loadTranslatePrefs, saveTranslatePrefs } from '#/api/channel/translate';

const prefs = reactive(loadTranslatePrefs());
watch(prefs, (v) => saveTranslatePrefs({ ...v }));
</script>

<template>
  <div class="tr">
    <h2 class="m-0 mb-8 text-lg font-bold text-gray-900">翻译设置</h2>

    <div class="tr__row">
      <Switch v-model:checked="prefs.chat" />
      <div>
        <div class="tr__name">实时翻译聊天消息，同时显示原文和中文</div>
        <div class="mt-3 text-sm text-gray-400">预览效果</div>
        <div class="mt-2 flex items-start gap-3">
          <span class="tr__avatar">D</span>
          <div class="tr__bubble">
            <div>Hello, my friend</div>
            <div class="tr__sub">你好，朋友</div>
          </div>
        </div>
      </div>
    </div>

    <div class="tr__row">
      <Switch v-model:checked="prefs.input" />
      <div>
        <div class="tr__name">开启输入框双语模式</div>
        <div class="mt-1 text-sm text-gray-400">开启后，输入框将支持实时翻译，一边输入一边翻译，可一键替换成客户使用的语言</div>
      </div>
    </div>

    <p class="text-xs text-gray-400">翻译使用「设置 → AI 接待设置」里配置的大模型，按字数消耗少量智点；同一段文字只翻译一次。</p>
  </div>
</template>

<style scoped>
.tr {
  padding: 18px 24px;
}

.tr__row {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  margin-bottom: 36px;
}

.tr__name {
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}

.tr__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  font-weight: 700;
  color: #2563EB;
  background: #BFDBFE;
  border-radius: 50%;
}

.tr__bubble {
  padding: 8px 12px;
  font-size: 14px;
  color: #1f2937;
  background: #f3f4f6;
  border-radius: 8px;
}

.tr__sub {
  padding-top: 6px;
  margin-top: 6px;
  border-top: 1px solid #e5e7eb;
}
</style>
