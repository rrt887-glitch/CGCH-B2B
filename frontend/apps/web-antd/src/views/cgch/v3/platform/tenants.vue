<script lang="ts" setup>
/**
 * 客户管理（只有平台账号能用）：给新客户开户、改账号数和到期时间、停用，以及「进入客户后台」。
 * - 开户只建客户的管理员账号；系统同时为这家公司建好「成员」角色，剩下的账号由客户管理员自己添加。
 * - 平台默认看不到客户的业务数据。客户需要支持时点「进入后台」并填写原因，2 小时内有效，每次进入都有记录，
 *   客户管理员在「设置 › 成员 › 平台访问记录」里也能看到。
 */
import type { Dayjs } from 'dayjs';

import type { EvolutionProxy, TenantConsoleRow, TenantVisitLog } from '#/api/system/tenant-visit';

import { onMounted, reactive, ref } from 'vue';

import { Button, DatePicker, Form, FormItem, Input, InputNumber, InputPassword, message, Modal, Select, Switch, Table, Tag, Textarea } from 'ant-design-vue';
import dayjs from 'dayjs';

import {
  createTenant,
  enterTenant,
  getEvolutionProxy,
  getTenantConsolePage,
  getTenantPackageSimpleList,
  getVisitLogPage,
  saveEvolutionProxy,
  updateTenant,
} from '#/api/system/tenant-visit';
import { router } from '#/router';
import { fmtDateTime } from '#/utils/datetime';
import { isStrongPassword, PASSWORD_TIP } from '#/utils/password';
import { startVisit } from '#/utils/tenant-visit';

defineOptions({ name: 'CgchV3PlatformTenants' });

/** 客户标准版（数据库升级 034 建好）；没有时用第一个套餐 */
const DEFAULT_PACKAGE_ID = 200;
const ACCOUNT_RE = /^(?:[\w.%+-]+@[a-z0-9.-]+\.[a-z]{2,}|\+?\d{6,20})$/i;

const rows = ref<TenantConsoleRow[]>([]);
const total = ref(0);
const page = reactive({ no: 1, size: 20 });
const keyword = ref('');
const loading = ref(false);
const packages = ref<{ id: number; name: string }[]>([]);

async function load() {
  loading.value = true;
  try {
    const r = await getTenantConsolePage({ pageNo: page.no, pageSize: page.size, name: keyword.value.trim() || undefined });
    rows.value = r.list ?? [];
    total.value = r.total ?? 0;
  } finally {
    loading.value = false;
  }
}

function expired(row: any) {
  return row.expireTime && dayjs(row.expireTime).isBefore(dayjs());
}

// ===== 开户 / 修改 =====
const open = ref(false);
const saving = ref(false);
const form = reactive({
  id: undefined as number | undefined,
  name: '',
  contactName: '',
  contactMobile: '',
  username: '',
  password: '',
  accountCount: 5,
  expire: dayjs().add(1, 'year') as Dayjs,
  packageId: DEFAULT_PACKAGE_ID as number | undefined,
  status: 0,
});
function openForm(row?: any) {
  const defaultPkg = packages.value.find((p) => p.id === DEFAULT_PACKAGE_ID)?.id ?? packages.value[0]?.id;
  Object.assign(form, {
    id: row?.id,
    name: row?.name ?? '',
    contactName: row?.contactName ?? '',
    contactMobile: row?.contactMobile ?? '',
    username: row?.adminUsername ?? '',
    password: '',
    accountCount: row?.accountCount ?? 5,
    expire: row?.expireTime ? dayjs(row.expireTime) : dayjs().add(1, 'year'),
    packageId: row?.packageId ?? defaultPkg,
    status: row?.status ?? 0,
  });
  open.value = true;
}
async function save() {
  if (!form.name.trim()) return message.warning('请填写客户公司名称');
  if (!form.contactName.trim()) return message.warning('请填写联系人');
  if (!form.packageId) return message.warning('请选择套餐');
  if (!form.accountCount || form.accountCount < 1) return message.warning('账号数至少 1 个');
  if (!form.expire) return message.warning('请选择到期时间');
  if (!form.id) {
    if (!ACCOUNT_RE.test(form.username.trim())) return message.warning('管理员登录账号请填写邮箱或手机号');
    if (!isStrongPassword(form.password)) return message.warning(PASSWORD_TIP);
  }
  saving.value = true;
  try {
    const data: any = {
      id: form.id,
      name: form.name.trim(),
      contactName: form.contactName.trim(),
      contactMobile: form.contactMobile.trim() || undefined,
      status: form.status,
      packageId: form.packageId,
      accountCount: form.accountCount,
      expireTime: form.expire.endOf('day').valueOf(),
    };
    if (form.id) {
      await updateTenant(data);
      message.success('已保存');
    } else {
      await createTenant({ ...data, username: form.username.trim(), password: form.password });
      Modal.success({
        title: '开户成功',
        content: `请把登录地址、管理员账号「${form.username.trim()}」和密码交给客户。客户登录后在「设置 › 成员」里添加员工账号（共 ${form.accountCount} 个，含管理员）。`,
      });
    }
    open.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function toggleStatus(row: any, on: boolean) {
  await updateTenant({
    id: row.id, name: row.name, contactName: row.contactName || row.name, contactMobile: row.contactMobile,
    status: on ? 0 : 1, packageId: row.packageId, accountCount: row.accountCount, expireTime: dayjs(row.expireTime).valueOf(),
  });
  row.status = on ? 0 : 1;
  message.success(on ? '已启用' : '已停用，这家客户的所有账号将无法登录');
}

// ===== 进入客户后台 =====
const enterOpen = ref(false);
const enterRow = ref<TenantConsoleRow>();
const reason = ref('');
const entering = ref(false);
function openEnter(row: any) {
  enterRow.value = row;
  reason.value = '';
  enterOpen.value = true;
}
async function doEnter() {
  if (!reason.value.trim()) return message.warning('请填写进入原因，客户能看到这条记录');
  entering.value = true;
  try {
    const r = await enterTenant(enterRow.value!.id, reason.value.trim());
    startVisit({ tenantId: r.tenantId, tenantName: r.tenantName, expireTime: dayjs(r.expireTime).valueOf() });
    enterOpen.value = false;
    // 重新加载页面，确保各页面都按这家客户的数据重新读取
    await router.replace('/v3/dashboard');
    window.location.reload();
  } finally {
    entering.value = false;
  }
}

// ===== 出口代理：扫码接入的号码走这家客户自己的 IP =====
const PROTOCOLS = [
  { label: 'SOCKS5', value: 'socks5' },
  { label: 'SOCKS4', value: 'socks4' },
  { label: 'HTTP', value: 'http' },
  { label: 'HTTPS', value: 'https' },
];
const proxyOpen = ref(false);
const proxySaving = ref(false);
const proxyRow = ref<TenantConsoleRow>();
const proxyInfo = ref<EvolutionProxy>({ enabled: false });
const proxyForm = reactive({ enabled: false, host: '', port: 1080 as number | undefined, protocol: 'socks5', username: '', password: '', hasPassword: false });
async function openProxy(row: any) {
  proxyRow.value = row;
  const info = await getEvolutionProxy(row.id).catch(() => ({ enabled: false }) as EvolutionProxy);
  proxyInfo.value = info;
  Object.assign(proxyForm, {
    enabled: !!info.enabled,
    host: info.host ?? '',
    port: info.port ?? 1080,
    protocol: info.protocol || 'socks5',
    username: info.username ?? '',
    password: '',
    hasPassword: !!info.hasPassword,
  });
  proxyOpen.value = true;
}
async function saveProxyFor() {
  if (!proxyRow.value) return;
  if (proxyForm.enabled) {
    if (!proxyForm.host.trim()) return message.warning('请填写代理主机');
    if (!proxyForm.port) return message.warning('请填写代理端口');
  }
  proxySaving.value = true;
  try {
    const failed = await saveEvolutionProxy({
      tenantId: proxyRow.value.id,
      enabled: proxyForm.enabled,
      host: proxyForm.host.trim() || undefined,
      port: proxyForm.port,
      protocol: proxyForm.protocol,
      username: proxyForm.username.trim() || undefined,
      password: proxyForm.password || undefined,
      applyNow: true,
    });
    if (failed && failed.length > 0) {
      message.warning(`代理已保存，但有 ${failed.length} 个号码没设置成功：${failed.join('；')}`, 8);
    } else {
      message.success(proxyForm.enabled ? '已保存，并设置到这家客户已有的号码上' : '已关闭出口代理，号码改回直连');
    }
    proxyOpen.value = false;
  } finally {
    proxySaving.value = false;
  }
}

// ===== 进入记录 =====
const logOpen = ref(false);
const logRow = ref<TenantConsoleRow>();
const logs = ref<TenantVisitLog[]>([]);
async function openLogs(row?: any) {
  logRow.value = row;
  const r = await getVisitLogPage({ pageNo: 1, pageSize: 50, visitTenantId: row?.id });
  logs.value = r.list ?? [];
  logOpen.value = true;
}

const columns = [
  { title: '客户', dataIndex: 'name', width: 200 },
  { title: '管理员账号', dataIndex: 'adminUsername', width: 220 },
  { title: '账号数', dataIndex: 'accountCount', width: 100 },
  { title: '套餐', dataIndex: 'packageName', width: 140 },
  { title: '到期', dataIndex: 'expireTime', width: 130 },
  { title: '启用', dataIndex: 'status', width: 80 },
  { title: '操作', key: 'actions', width: 240 },
];
const logColumns = [
  { title: '进入时间', dataIndex: 'enterTime', width: 160 },
  { title: '客户', dataIndex: 'visitTenantName', width: 160 },
  { title: '人员', dataIndex: 'nickname', width: 120 },
  { title: '原因', dataIndex: 'reason', ellipsis: true },
  { title: '退出', dataIndex: 'exitTime', width: 160 },
];

onMounted(async () => {
  packages.value = (await getTenantPackageSimpleList().catch(() => [])) ?? [];
  await load();
});
</script>

<template>
  <div class="flex h-full flex-col gap-4 overflow-auto rounded-xl bg-white p-3 sm:p-5">
    <div class="flex flex-wrap items-start gap-3">
      <div class="min-w-0 flex-1 basis-[260px]">
        <h2 class="m-0 text-lg font-semibold text-[#1D1D1F]">客户管理</h2>
        <p class="m-0 mt-1 text-[13px] text-[#6E6E73]">
          每家客户的数据互相隔离。开户时只建客户的管理员，剩下的账号由客户自己添加。平台默认看不到客户的数据，需要帮客户排查时点「进入后台」并填写原因（2 小时内有效，客户能看到记录）。
        </p>
      </div>
      <Input.Search v-model:value="keyword" allow-clear placeholder="搜索客户名称" class="w-full sm:w-56" @search="page.no = 1; load()" />
      <Button @click="openLogs()">进入记录</Button>
      <Button type="primary" @click="openForm()">开户</Button>
    </div>

    <Table
      row-key="id"
      size="middle"
      :loading="loading"
      :data-source="rows"
      :columns="columns"
      :scroll="{ x: 1000 }"
      :pagination="{ current: page.no, pageSize: page.size, total, showTotal: (t: number) => `共 ${t} 家`, onChange: (n: number, s: number) => { page.no = n; page.size = s; load(); } }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'name'">
          {{ record.name }}<Tag v-if="record.platform" color="blue" class="ml-1">平台</Tag>
          <div v-if="record.contactName" class="text-xs text-[#6E6E73]">{{ record.contactName }} {{ record.contactMobile || '' }}</div>
        </template>
        <template v-else-if="column.dataIndex === 'adminUsername'">
          {{ record.adminUsername || '—' }}
          <div v-if="record.adminNickname" class="text-xs text-[#6E6E73]">{{ record.adminNickname }}</div>
        </template>
        <template v-else-if="column.dataIndex === 'accountCount'">
          <span :class="{ 'text-[#c93400]': (record.usedAccountCount ?? 0) >= record.accountCount }">
            {{ record.usedAccountCount ?? 0 }} / {{ record.accountCount }}
          </span>
        </template>
        <template v-else-if="column.dataIndex === 'expireTime'">
          <span :class="{ 'text-[#c93400]': expired(record) }">{{ fmtDateTime(record.expireTime, 'YYYY-MM-DD') }}</span>
          <Tag v-if="expired(record)" color="red" class="ml-1">已到期</Tag>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <Switch size="small" :checked="record.status === 0" :disabled="record.platform" @change="(v: any) => toggleStatus(record, !!v)" />
        </template>
        <template v-else-if="column.key === 'actions'">
          <div v-if="!record.platform" class="flex flex-wrap gap-1">
            <Button type="link" size="small" @click="openForm(record)">修改</Button>
            <Button type="link" size="small" :disabled="record.status !== 0" @click="openEnter(record)">进入后台</Button>
            <Button type="link" size="small" @click="openProxy(record)">出口代理</Button>
            <Button type="link" size="small" @click="openLogs(record)">记录</Button>
          </div>
          <span v-else class="text-xs text-[#6E6E73]">平台自己</span>
        </template>
      </template>
    </Table>

    <Modal v-model:open="open" :title="form.id ? '修改客户' : '开户'" :confirm-loading="saving" ok-text="保存" @ok="save">
      <Form layout="vertical" class="mt-3">
        <FormItem label="客户公司名称" required><Input v-model:value="form.name" :maxlength="30" placeholder="例如：Acme 外贸有限公司" /></FormItem>
        <div class="grid grid-cols-2 gap-3">
          <FormItem label="联系人" required><Input v-model:value="form.contactName" :maxlength="30" /></FormItem>
          <FormItem label="联系手机"><Input v-model:value="form.contactMobile" :maxlength="20" /></FormItem>
        </div>
        <template v-if="!form.id">
          <FormItem label="管理员登录账号" required extra="邮箱或手机号，全平台唯一；客户用它登录，不用选公司">
            <Input v-model:value="form.username" :maxlength="64" placeholder="例如：boss@acme.com" />
          </FormItem>
          <FormItem label="管理员初始密码" required :extra="PASSWORD_TIP"><InputPassword v-model:value="form.password" :maxlength="64" /></FormItem>
        </template>
        <div class="grid grid-cols-2 gap-3">
          <FormItem label="账号数（含管理员）" required><InputNumber v-model:value="form.accountCount" :min="1" :max="9999" class="w-full" /></FormItem>
          <FormItem label="到期时间" required><DatePicker v-model:value="form.expire" class="w-full" :allow-clear="false" /></FormItem>
        </div>
        <FormItem label="套餐" required>
          <Select v-model:value="form.packageId" :options="packages.map((p) => ({ label: p.name, value: p.id }))" />
        </FormItem>
      </Form>
    </Modal>

    <Modal v-model:open="enterOpen" :title="`进入「${enterRow?.name}」的后台`" :confirm-loading="entering" ok-text="进入" @ok="doEnter">
      <p class="mt-3 text-[13px] text-[#6E6E73]">
        进入后可以查看和操作这家客户的数据，2 小时后自动失效。这次进入会记录下来，客户管理员能看到时间和原因。
      </p>
      <Textarea v-model:value="reason" :maxlength="200" :rows="3" show-count placeholder="进入原因，例如：客户反馈 WhatsApp 收不到消息，协助排查" />
    </Modal>

    <Modal v-model:open="proxyOpen" :title="`「${proxyRow?.name}」的 WhatsApp 出口代理`" :confirm-loading="proxySaving"
           ok-text="保存并应用" width="560px" @ok="saveProxyFor">
      <p class="mt-3 text-[13px] text-[#6E6E73]">
        扫码接入的号码都跑在同一台 WhatsApp 服务上。给这家客户配一个自己的出口 IP，一家群发被风控时不会牵连同服务器上的其他客户。
        保存后会设置到这家客户已有的号码上（正在连接的号码需要重新连接才生效）。
      </p>
      <Form layout="vertical" class="mt-3">
        <FormItem label="使用自己的出口 IP">
          <Switch v-model:checked="proxyForm.enabled" />
          <span class="ml-2 text-xs text-[#6E6E73]">关闭时直接从平台服务器 IP 出去</span>
        </FormItem>
        <template v-if="proxyForm.enabled">
          <div class="grid grid-cols-3 gap-3">
            <FormItem label="代理主机" required class="col-span-2">
              <Input v-model:value="proxyForm.host" :maxlength="255" placeholder="proxy.example.com 或 1.2.3.4" />
            </FormItem>
            <FormItem label="端口" required><InputNumber v-model:value="proxyForm.port" :min="1" :max="65535" class="w-full" /></FormItem>
          </div>
          <FormItem label="协议" required>
            <Select v-model:value="proxyForm.protocol" :options="PROTOCOLS" />
          </FormItem>
          <div class="grid grid-cols-2 gap-3">
            <FormItem label="代理账号"><Input v-model:value="proxyForm.username" :maxlength="128" placeholder="可空" /></FormItem>
            <FormItem label="代理密码" :extra="proxyForm.hasPassword ? '留空表示不修改' : '可空'">
              <InputPassword v-model:value="proxyForm.password" :maxlength="255" placeholder="可空" />
            </FormItem>
          </div>
        </template>
        <div v-if="proxyInfo.lastAppliedTime || proxyInfo.lastError" class="mt-1 text-xs">
          <span v-if="proxyInfo.lastAppliedTime" class="text-[#6E6E73]">上次设置：{{ fmtDateTime(proxyInfo.lastAppliedTime) }}</span>
          <span v-if="proxyInfo.lastError" class="text-[#c93400]">　失败：{{ proxyInfo.lastError }}</span>
        </div>
      </Form>
    </Modal>

    <Modal v-model:open="logOpen" :title="logRow ? `「${logRow.name}」的进入记录` : '进入记录'" :footer="null" width="820px">
      <Table row-key="id" size="small" class="mt-3" :data-source="logs" :columns="logColumns" :pagination="false" :scroll="{ x: 700, y: 420 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'enterTime'">{{ fmtDateTime(record.enterTime) }}</template>
          <template v-else-if="column.dataIndex === 'exitTime'">{{ record.exitTime ? fmtDateTime(record.exitTime) : '到期自动结束' }}</template>
        </template>
      </Table>
    </Modal>
  </div>
</template>
