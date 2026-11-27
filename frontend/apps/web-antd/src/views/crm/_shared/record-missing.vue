<script setup lang="ts">
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { useTabs } from '@vben/hooks';

import { Button, Card, Empty } from 'ant-design-vue';

import { crmBackTarget } from './back';

const props = defineProps<{
  kind: 'business' | 'clue' | 'customer';
  label: string;
}>();

const router = useRouter();
const tabs = useTabs();

function back() {
  const target = crmBackTarget(
    router,
    props.kind === 'clue' ? 'lead' : props.kind,
  );
  tabs.closeCurrentTab();
  router.push(target);
}
</script>

<template>
  <Page auto-content-height :title="`${label}不存在`">
    <Card class="flex h-full items-center justify-center">
      <div class="flex flex-col items-center gap-4 py-16">
        <Empty
          :image="Empty.PRESENTED_IMAGE_SIMPLE"
          :description="`${label}不存在或已被删除，可能已被移除或链接已失效`"
        />
        <Button type="primary" @click="back">返回列表</Button>
      </div>
    </Card>
  </Page>
</template>
