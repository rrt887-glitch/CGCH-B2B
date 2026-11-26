<script lang="ts" setup>
/**
 * 言语边界（违禁词拦截）：
 * AI 回复草稿命中违禁词（不区分大小写）时自动拦截转为人工待审核草稿，避免对外做出违规承诺。
 */
import type { ChannelAiApi } from '#/api/channel/ai';

import { onMounted, reactive, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Input,
  message,
  Modal,
  Popconfirm,
  Switch,
  Table,
} from 'ant-design-vue';

import {
  deleteForbidden,
  getForbiddenList,
  saveForbidden,
} from '#/api/channel/ai';

defineOptions({ name: 'CgchAgentForbidden' });

const QUICK_EXAMPLES = [
  { word: '100% guarantee', remark: '避免绝对化质量承诺引发索赔' },
  { word: 'free sample', remark: '免费寄样须经业务员审批' },
  { word: 'exclusive agency', remark: '独家代理权须管理层评估' },
  { word: 'full refund', remark: '全额退款承诺须人工核实' },
];

const loading = ref(false);
const list = ref<ChannelAiApi.ForbiddenWord[]>([]);
const columns = [
  { title: '拦截词条（不区分大小写）', dataIndex: 'word', key: 'word', width: 240 },
  { title: '风控原因说明', dataIndex: 'remark', key: 'remark' },
  { title: '启用拦截', key: 'enabled', width: 92 },
  { title: '操作', key: 'action', width: 110, align: 'right' as const },
];

async function load() {
  loading.value = true;
  try {
    list.value = await getForbiddenList();
  } finally {
    loading.value = false;
  }
}

const open = ref(false);
const form = reactive<ChannelAiApi.ForbiddenWord>({
  word: '',
  remark: '',
  enabled: true,
});

function openForm(row?: Partial<ChannelAiApi.ForbiddenWord>) {
  Object.assign(
    form,
    { id: undefined, word: '', remark: '', enabled: true },
    row ?? {},
  );
  open.value = true;
}

async function save() {
  if (!form.word.trim()) {
    message.warning('请填写违禁词');
    return;
  }
  await saveForbidden({ ...form });
  message.success('违禁词已保存');
  open.value = false;
  await load();
}

async function toggle(row: ChannelAiApi.ForbiddenWord, enabled: boolean) {
  await saveForbidden({ ...row, enabled });
  row.enabled = enabled;
}

async function remove(row: ChannelAiApi.ForbiddenWord) {
  await deleteForbidden(row.id!);
  message.success('已删除');
  await load();
}

onMounted(load);
</script>

<template>
  <div class="fb">
    <div class="fb__head">
      <div class="flex min-w-0 items-center gap-3">
        <span class="fb__icon">
          <IconifyIcon icon="lucide:shield-ban" class="size-5" />
        </span>
        <div>
          <div class="text-[15px] font-bold text-[#1D1D1F]">敏感承诺与违禁词红线</div>
          <div class="mt-0.5 text-xs text-[#6E6E73]">
            AI 拟定的回复一旦包含以下词条（不区分大小写），将自动拦截并留作草稿交由业务员把关
          </div>
        </div>
      </div>

      <button type="button" class="fb__add-btn" @click="openForm()">
        <IconifyIcon icon="lucide:plus" class="size-3.5" />
        <span>新增违禁词</span>
      </button>
    </div>

    <!-- 快捷推荐示例 -->
    <div class="fb__presets">
      <span class="text-xs font-medium text-[#6E6E73]">常用风控词条（点击快速填入）：</span>
      <button
        v-for="ex in QUICK_EXAMPLES"
        :key="ex.word"
        type="button"
        class="fb__preset-chip"
        @click="openForm({ word: ex.word, remark: ex.remark })"
      >
        <IconifyIcon icon="lucide:plus" class="size-3" />
        <span>{{ ex.word }}</span>
      </button>
    </div>

    <Table
      :columns="columns"
      :data-source="list"
      :loading="loading"
      :pagination="false"
      row-key="id"
      size="middle"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'word'">
          <span class="ap-crm-pill ap-crm-pill--red font-mono">
            {{ record.word }}
          </span>
        </template>
        <template v-else-if="column.key === 'remark'">
          <span class="text-xs text-[#6E6E73]">{{ record.remark || '—' }}</span>
        </template>
        <template v-else-if="column.key === 'enabled'">
          <Switch
            :checked="record.enabled"
            size="small"
            @change="(v: any) => toggle(record as any, !!v)"
          />
        </template>
        <template v-else-if="column.key === 'action'">
          <div class="flex items-center justify-end gap-2.5">
            <a class="ap-crm-link" @click="openForm(record as any)">编辑</a>
            <Popconfirm
              title="确定移除这个违禁词？"
              ok-text="移除"
              cancel-text="取消"
              @confirm="remove(record as any)"
            >
              <a class="ap-crm-link ap-crm-link--danger">删除</a>
            </Popconfirm>
          </div>
        </template>
      </template>
      <template #emptyText>暂未配置违禁词，可点击上方常用词条快速添加</template>
    </Table>

    <Modal
      v-model:open="open"
      :title="form.id ? '编辑违禁词' : '新增违禁词'"
      :width="480"
      ok-text="保存"
      cancel-text="取消"
      wrap-class-name="ap-modal-sheet"
      @ok="save"
    >
      <div class="ap-sheet-lead">
        <span class="ap-sheet-icon" style="color: #d70015; background: #ffeceb">
          <IconifyIcon icon="lucide:shield-alert" class="size-5" />
        </span>
        <div class="ap-sheet-subtitle">
          匹配时不区分英文大小写，命中后消息不会自动发出，转为人工审核草稿。
        </div>
      </div>

      <div class="ap-sheet-body">
        <div class="ap-sheet-field">
          <div class="ap-sheet-label"><span>违禁词或短语 *</span></div>
          <Input
            v-model:value="form.word"
            :maxlength="100"
            placeholder="例如：guarantee / free sample / exclusive"
          />
        </div>
        <div class="ap-sheet-field">
          <div class="ap-sheet-label">
            <span>风控备注</span>
            <span class="ap-sheet-optional">选填</span>
          </div>
          <Input
            v-model:value="form.remark"
            :maxlength="200"
            placeholder="例如：不能未经审批承诺独家代理"
          />
        </div>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.fb {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.fb__head {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 14px;
  border-bottom: 0.5px solid var(--ap-separator);
}

.fb__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 42px;
  height: 42px;
  color: #d70015;
  background: #ffeceb;
  border-radius: 12px;
}

.fb__add-btn {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 32px;
  padding: 0 14px;
  font-size: 12.5px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
  background: linear-gradient(180deg, #1a88ff 0%, #007aff 100%);
  border: 0;
  border-radius: 999px;
  box-shadow: 0 2px 6px rgb(0 122 255 / 22%);
}

.fb__presets {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  padding: 10px 14px;
  background: #f7f8fb;
  border-radius: 12px;
}

.fb__preset-chip {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  height: 25px;
  padding: 0 9px;
  font-size: 12px;
  font-weight: 500;
  color: #3a3a3c;
  cursor: pointer;
  background: #fff;
  border: 0;
  border-radius: 999px;
  box-shadow: 0 1px 2px rgb(0 0 0 / 5%);
  transition: all var(--ap-dur-fast);
}

.fb__preset-chip:hover {
  color: #d70015;
  background: #ffeceb;
}
</style>
