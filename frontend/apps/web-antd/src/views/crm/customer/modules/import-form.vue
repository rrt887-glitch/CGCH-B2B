<script lang="ts" setup>
import type { FileType } from 'ant-design-vue/es/upload/interface';

import { useVbenModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Button, message, Upload } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { importCustomer, importCustomerTemplate } from '#/api/crm/customer';
import { $t } from '#/locales';

import { useImportFormSchema } from '../data';

const emit = defineEmits(['success']);

const [Form, formApi] = useVbenForm({
  commonConfig: {
    formItemClass: 'col-span-2',
    labelWidth: 120,
  },
  layout: 'horizontal',
  schema: useImportFormSchema(),
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
    const data = await formApi.getValues();
    try {
      await importCustomer({
        ownerUserId: data.ownerUserId,
        file: data.file,
        updateSupport: data.updateSupport,
      });
      // 关闭并提示
      await modalApi.close();
      emit('success');
      message.success($t('ui.actionMessage.operationSuccess'));
    } finally {
      modalApi.unlock();
    }
  },
});

/** 上传前 */
function beforeUpload(file: FileType) {
  formApi.setFieldValue('file', file);
  return false;
}

/** 下载模版 */
async function handleDownload() {
  const data = await importCustomerTemplate();
  downloadFileFromBlobPart({ fileName: '客户导入模板.xls', source: data });
}
</script>

<template>
  <Modal title="批量导入客户" class="ap-vben-modal w-[500px] max-w-[92vw]">
    <Form class="mx-2">
      <template #file>
        <div class="w-full">
          <Upload
            :max-count="1"
            accept=".xls,.xlsx"
            :before-upload="beforeUpload"
            class="block w-full"
          >
            <div
              class="flex cursor-pointer flex-col items-center justify-center gap-1.5 rounded-2xl border border-dashed border-[#D2D2D7] bg-[#F8F8FA] px-4 py-5 text-center transition-colors hover:border-[#007AFF] hover:bg-[#F0F7FF]"
            >
              <IconifyIcon
                icon="lucide:file-spreadsheet"
                class="size-6 text-[#007AFF]"
              />
              <span class="text-[13px] font-semibold text-[#1D1D1F]">
                点击选择 Excel 表格文件
              </span>
              <span class="text-xs text-[#86868B]">
                支持 .xls / .xlsx 格式，请按标准模板填写列头
              </span>
            </div>
          </Upload>
        </div>
      </template>
    </Form>
    <template #prepend-footer>
      <div class="flex flex-auto items-center">
        <Button shape="round" @click="handleDownload">
          <IconifyIcon icon="lucide:download" class="mr-1 inline size-3.5 align-[-2px]" />下载导入模板
        </Button>
      </div>
    </template>
  </Modal>
</template>
