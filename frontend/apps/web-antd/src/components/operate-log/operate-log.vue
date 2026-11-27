<script setup lang="ts">
import type { OperateLogProps } from './typing';

import { DICT_TYPE } from '@vben/constants';
import { getDictLabel, getDictObj } from '@vben/hooks';
import { formatDateTime } from '@vben/utils';

import { Empty, Tag, Timeline } from 'ant-design-vue';

defineOptions({ name: 'OperateLogV2' });

withDefaults(defineProps<OperateLogProps>(), {
  logList: () => [],
});

/** 获得 userType 颜色 */
function getUserTypeColor(userType: number) {
  const dict = getDictObj(DICT_TYPE.USER_TYPE, userType);
  switch (dict?.colorType) {
    case 'danger': {
      return '#F56C6C';
    }
    case 'info': {
      return '#909399';
    }
    case 'success': {
      return '#67C23A';
    }
    case 'warning': {
      return '#E6A23C';
    }
  }
  return '#409EFF';
}
</script>

<template>
  <div class="pt-5">
    <Empty
      v-if="!logList || logList.length === 0"
      :image="Empty.PRESENTED_IMAGE_SIMPLE"
      description="还没有操作日志"
    />
    <Timeline v-else>
      <Timeline.Item v-for="log in logList" :key="log.id">
        <template #dot>
          <span
            :style="{ backgroundColor: getUserTypeColor(log.userType) }"
            class="flex h-5 w-5 items-center justify-center rounded-full text-[10px] text-white"
          >
            {{ getDictLabel(DICT_TYPE.USER_TYPE, log.userType)[0] }}
          </span>
        </template>
        <div class="ml-2 flex flex-wrap items-center gap-2 leading-[22px]">
          <span class="w-[140px] shrink-0 text-[13px] text-gray-400">
            {{ formatDateTime(log.createTime) }}
          </span>
          <Tag color="success" class="!mr-0">{{ log.userName }}</Tag>
          <span>{{ log.action }}</span>
        </div>
      </Timeline.Item>
    </Timeline>
  </div>
</template>
