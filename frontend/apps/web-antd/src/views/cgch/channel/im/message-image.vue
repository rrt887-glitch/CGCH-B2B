<script lang="ts" setup>
import type { ChannelWaApi } from '#/api/channel/whatsapp';

import { computed, ref } from 'vue';

import { useIntersectionObserver } from '@vueuse/core';

import { getWaMedia } from '#/api/channel/whatsapp';

const props = defineProps<{ message: ChannelWaApi.Message }>();

const preview = ref<HTMLElement>();
const fetchedUrl = ref<string>();
const failedUrl = ref<string>();
const loading = ref(false);
const downloadFailed = ref(false);
const url = computed(() => props.message.fileUrl || fetchedUrl.value);
const canDownload = computed(() => !!props.message.mediaId);
const failed = computed(() =>
  url.value ? url.value === failedUrl.value : downloadFailed.value,
);

async function loadImage() {
  if (loading.value) return;
  failedUrl.value = undefined;
  downloadFailed.value = false;
  if (url.value || !canDownload.value) return;

  loading.value = true;
  try {
    const media = await getWaMedia(props.message.id, {
      showErrorMessage: false,
    });
    fetchedUrl.value = media.fileUrl;
    downloadFailed.value = !media.fileUrl;
  } catch {
    downloadFailed.value = true;
  } finally {
    loading.value = false;
  }
}

// 只下载滚入可见区域的图片。每条消息首次进入时尝试一次，失败后由用户重试。
const { stop } = useIntersectionObserver(preview, ([entry]) => {
  if (!entry?.isIntersecting) return;
  stop();
  if (!url.value && canDownload.value) void loadImage();
});
</script>

<template>
  <div
    ref="preview"
    class="mb-1 flex aspect-[4/3] w-60 max-w-full items-center justify-center overflow-hidden rounded-lg bg-black/5"
  >
    <a
      v-if="url && !failed"
      :href="url"
      target="_blank"
      rel="noopener"
      class="block size-full"
    >
      <img
        :src="url"
        :alt="message.fileName || 'WhatsApp 图片'"
        class="block size-full object-contain"
        loading="lazy"
        @error="failedUrl = url"
      />
    </a>
    <div
      v-else
      class="flex flex-col items-center gap-1 px-2 text-center text-xs"
    >
      <span role="status">{{
        failed ? '图片加载失败' : canDownload ? '正在加载图片…' : '图片暂不可用'
      }}</span>
      <button
        v-if="failed"
        type="button"
        class="cursor-pointer border-0 bg-transparent font-medium text-inherit underline"
        @click="loadImage"
      >
        重试
      </button>
    </div>
  </div>
</template>
