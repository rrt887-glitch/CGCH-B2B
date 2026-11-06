<script lang="ts" setup>
import type { CrmBusinessApi } from '#/api/crm/business';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { updateBusinessStatus } from '#/api/crm/business';
import { DEFAULT_STATUSES, getBusinessStatusSimpleList } from '#/api/crm/business/status';
import { $t } from '#/locales';

import { useStatusFormSchema } from '../data';

const emit = defineEmits(['success']);

const formData = ref<CrmBusinessApi.Business>();

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 120,
  },
  layout: 'horizontal',
  schema: useStatusFormSchema(formData),
  showDefaultActions: false,
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    // 提交表单
    const data = (await formApi.getValues()) as CrmBusinessApi.Business;
    try {
      if (!data.status) {
        return;
      }
      await updateBusinessStatus({
        id: data.id,
        statusId: data.status > 0 ? data.status : undefined,
        endStatus: data.status < 0 ? -data.status : undefined,
      });
      // 关闭并提示
      await modalApi.close();
      emit('success');
      message.success($t('ui.actionMessage.operationSuccess'));
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      return;
    }
    // 加载数据
    const data = modalApi.getData() as CrmBusinessApi.Business;
    if (!data || !data.id) {
      return;
    }
    // 先交给表单：阶段下拉的选项要按这个商机的阶段组加载
    formData.value = data;
    // 已结束的商机用结束状态的负数；处于和结束状态同名的阶段（如预置的「赢单」）时也显示为对应的结束状态
    if (data.endStatus == null) {
      data.status = data.statusId;
      const stage = (await getBusinessStatusSimpleList(data.statusTypeId ?? 0).catch(() => [])).find((x) => x.id === data.statusId);
      const end = stage && DEFAULT_STATUSES.find((x) => x.name === stage.name);
      if (end) data.status = -end.endStatus;
    } else {
      data.status = -data.endStatus;
    }
    modalApi.lock();
    try {
      // 设置到 values
      await formApi.setValues(formData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal title="变更商机状态" class="w-2/5">
    <Form class="mx-4" />
  </Modal>
</template>
