<script lang="ts" setup>
/**
 * 成员：每家公司只有两种账号。
 * - 管理员：全部功能，看得到本公司所有人的客户和会话，可以添加成员、配置大模型和渠道等公司级设置；
 * - 成员：日常接待和跟进客户，只看自己负责的数据，以及同事「公开给团队」的会话。
 * 登录账号用邮箱或手机号（全平台唯一），登录时不用选公司。
 * 角色按「编码」识别（每家公司的角色编号都不一样）：管理员 tenant_admin（平台里是 super_admin），成员 cgch_sales。
 */
import type { TenantVisitLog } from '#/api/system/tenant-visit';
import type { SystemUserApi } from '#/api/system/user';

import { computed, onMounted, reactive, ref } from 'vue';

import { useUserStore } from '@vben/stores';

import { Button, Collapse, CollapsePanel, Form, FormItem, Input, InputPassword, message, Modal, Radio, RadioGroup, Switch, Table, Tag } from 'ant-design-vue';

import { assignUserRole, getUserRoleList } from '#/api/system/permission';
import { getSimpleRoleList } from '#/api/system/role';
import { getMyVisitLogPage } from '#/api/system/tenant-visit';
import { createUser, deleteUser, getUserPage, resetUserPassword, updateUser, updateUserStatus } from '#/api/system/user';
import { isStrongPassword, PASSWORD_TIP } from '#/utils/password';
import { fmtDateTime } from '#/utils/datetime';

defineOptions({ name: 'CgchV3Members' });

/** 角色编码：客户公司的管理员是 tenant_admin，平台自己是 super_admin；成员是 cgch_sales */
const ADMIN_CODES = ['tenant_admin', 'super_admin'];
const MEMBER_CODE = 'cgch_sales';
/** 登录账号：邮箱或手机号（可带 +国家码） */
const ACCOUNT_RE = /^(?:[\w.%+-]+@[a-z0-9.-]+\.[a-z]{2,}|\+?\d{6,20})$/i;

type RoleKind = 'admin' | 'other' | 'sales';
const ROLE_LABEL: Record<RoleKind, { color: string; label: string }> = {
  admin: { color: 'blue', label: '管理员' },
  sales: { color: 'default', label: '成员' },
  other: { color: 'orange', label: '未设置角色' },
};

const userStore = useUserStore();
const myId = computed(() => Number((userStore.userInfo as any)?.id ?? (userStore.userInfo as any)?.userId));

/** 本公司的管理员 / 成员角色编号（按编码查到） */
const adminRoleIds = ref<number[]>([]);
const adminRoleId = ref<number>();
const memberRoleId = ref<number>();
async function loadRoles() {
  const roles = ((await getSimpleRoleList()) ?? []) as any[];
  adminRoleIds.value = roles.filter((r) => ADMIN_CODES.includes(r.code)).map((r) => r.id);
  adminRoleId.value = (roles.find((r) => r.code === 'tenant_admin') ?? roles.find((r) => r.code === 'super_admin'))?.id;
  memberRoleId.value = roles.find((r) => r.code === MEMBER_CODE)?.id;
}

interface Row extends SystemUserApi.User {
  role?: RoleKind;
}
const rows = ref<Row[]>([]);
const total = ref(0);
const page = reactive({ no: 1, size: 20 });
const keyword = ref('');
const loading = ref(false);
const adminCount = computed(() => rows.value.filter((r) => r.role === 'admin').length);

async function load() {
  loading.value = true;
  try {
    const r = await getUserPage({ pageNo: page.no, pageSize: page.size, nickname: keyword.value.trim() || undefined } as any);
    const list = (r.list ?? []) as Row[];
    await Promise.all(
      list.map(async (u) => {
        const roles: number[] = await getUserRoleList(u.id!).catch(() => []);
        u.role = roles.some((id) => adminRoleIds.value.includes(id))
          ? 'admin'
          : memberRoleId.value && roles.includes(memberRoleId.value) ? 'sales' : 'other';
      }),
    );
    rows.value = list;
    total.value = r.total ?? 0;
  } finally {
    loading.value = false;
  }
}

// ===== 新增 / 编辑 =====
const open = ref(false);
const saving = ref(false);
const form = reactive({ id: undefined as number | undefined, username: '', nickname: '', password: '', mobile: '', email: '', role: 'sales' as 'admin' | 'sales' });
function openForm(row?: any) {
  Object.assign(form, {
    id: row?.id,
    username: row?.username ?? '',
    nickname: row?.nickname ?? '',
    password: '',
    mobile: row?.mobile ?? '',
    email: row?.email ?? '',
    role: row?.role === 'admin' ? 'admin' : 'sales',
  });
  open.value = true;
}
async function save() {
  if (!form.nickname.trim()) return message.warning('请填写姓名');
  const account = form.username.trim();
  if (!form.id && !ACCOUNT_RE.test(account)) return message.warning('登录账号请填写邮箱或手机号');
  if (!form.id && !isStrongPassword(form.password)) return message.warning(PASSWORD_TIP);
  if (form.id === myId.value && form.role !== 'admin') return message.warning('不能把自己改成成员，请让其他管理员操作');
  const roleId = form.role === 'admin' ? adminRoleId.value : memberRoleId.value;
  if (!roleId) return message.error('本公司还没有这个角色，请联系平台管理员');
  saving.value = true;
  try {
    const data = {
      id: form.id,
      username: form.id ? form.username : account,
      nickname: form.nickname.trim(),
      mobile: form.mobile.trim() || undefined,
      email: form.email.trim() || undefined,
      password: form.id ? undefined : form.password,
    } as any;
    let id = form.id;
    if (id) {
      // 编辑时带上原有的其他资料，避免被清空
      const old = rows.value.find((r) => r.id === id);
      await updateUser({ ...old, ...data, postIds: old?.postIds ?? [] });
    } else {
      id = (await createUser(data)) as unknown as number;
    }
    await assignUserRole({ userId: id!, roleIds: [roleId] } as any);
    message.success(form.id ? '已保存' : '已添加，可以用这个账号登录了');
    open.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function toggleStatus(row: any, on: boolean) {
  if (row.id === myId.value) return message.warning('不能停用自己的账号');
  await updateUserStatus(row.id!, on ? 0 : 1);
  row.status = on ? 0 : 1;
  message.success(on ? '已启用' : '已停用，这个账号将无法登录');
}

// ===== 重置密码 =====
const pwdOpen = ref(false);
const pwdRow = ref<Row>();
const pwd = ref('');
function openPwd(row: any) {
  pwdRow.value = row;
  pwd.value = '';
  pwdOpen.value = true;
}
async function savePwd() {
  if (!isStrongPassword(pwd.value)) return message.warning(PASSWORD_TIP);
  await resetUserPassword(pwdRow.value!.id!, pwd.value);
  message.success('密码已重置');
  pwdOpen.value = false;
}

function remove(row: any) {
  if (row.id === myId.value) return message.warning('不能删除自己的账号');
  Modal.confirm({
    title: `删除成员「${row.nickname}」？`,
    content: '删除后这个账号无法登录。他负责的客户和会话不会删除，可以在客户、会话里转给其他人。',
    okText: '删除',
    okButtonProps: { danger: true },
    onOk: async () => {
      await deleteUser(row.id!);
      message.success('已删除');
      await load();
    },
  });
}

const columns = [
  { title: '姓名', dataIndex: 'nickname', width: 160 },
  { title: '登录账号', dataIndex: 'username', width: 200 },
  { title: '角色', dataIndex: 'role', width: 100 },
  { title: '手机', dataIndex: 'mobile', width: 140 },
  { title: '邮箱', dataIndex: 'email', ellipsis: true },
  { title: '启用', dataIndex: 'status', width: 90 },
  { title: '操作', key: 'actions', width: 200 },
];

// ===== 平台访问记录：平台客服进入本公司后台的记录（每次进入都要填写原因） =====
const visits = ref<TenantVisitLog[]>([]);
const visitTotal = ref(0);
async function loadVisits() {
  const r = await getMyVisitLogPage({ pageNo: 1, pageSize: 20 }).catch(() => null);
  visits.value = r?.list ?? [];
  visitTotal.value = r?.total ?? 0;
}
const visitColumns = [
  { title: '进入时间', dataIndex: 'enterTime', width: 170 },
  { title: '平台人员', dataIndex: 'nickname', width: 140 },
  { title: '原因', dataIndex: 'reason', ellipsis: true },
  { title: '退出时间', dataIndex: 'exitTime', width: 170 },
];

onMounted(async () => {
  await loadRoles();
  await load();
  loadVisits();
});
</script>

<template>
  <div class="flex h-full flex-col gap-4 overflow-auto rounded-xl bg-white p-3 sm:p-5">
    <div class="flex flex-wrap items-start gap-3">
      <div class="min-w-0 flex-1 basis-[240px]">
        <h2 class="m-0 text-lg font-semibold text-[#1D1D1F]">成员</h2>
        <p class="m-0 mt-1 text-[13px] text-[#6E6E73]">
          管理员可以使用全部功能、看到本公司所有人的客户和会话；成员只看自己负责的客户和会话，以及同事公开给团队的会话，不能修改大模型、知识库等公司设置。
          账号数量上限以开户时为准，需要增加请联系平台。
        </p>
      </div>
      <Input.Search v-model:value="keyword" allow-clear placeholder="搜索姓名" class="w-full sm:w-56" @search="page.no = 1; load()" />
      <Button type="primary" @click="openForm()">添加成员</Button>
    </div>

    <Table
      row-key="id"
      size="middle"
      :loading="loading"
      :data-source="rows"
      :columns="columns"
      :scroll="{ x: 900 }"
      :pagination="{ current: page.no, pageSize: page.size, total, showTotal: (t: number) => `共 ${t} 人`, onChange: (n: number, s: number) => { page.no = n; page.size = s; load(); } }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'nickname'">
          {{ record.nickname }}<Tag v-if="record.id === myId" class="ml-1">我</Tag>
        </template>
        <template v-else-if="column.dataIndex === 'role'">
          <Tag :color="ROLE_LABEL[record.role as RoleKind]?.color" class="m-0">{{ ROLE_LABEL[record.role as RoleKind]?.label }}</Tag>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <Switch size="small" :checked="record.status === 0" :disabled="record.id === myId" @change="(v: any) => toggleStatus(record, !!v)" />
        </template>
        <template v-else-if="column.key === 'actions'">
          <div class="flex gap-1">
            <Button type="link" size="small" @click="openForm(record)">编辑</Button>
            <Button type="link" size="small" @click="openPwd(record)">重置密码</Button>
            <Button type="link" size="small" danger :disabled="record.id === myId" @click="remove(record)">删除</Button>
          </div>
        </template>
      </template>
    </Table>

    <Collapse ghost>
      <CollapsePanel key="visits" :header="`平台访问记录（${visitTotal}）`">
        <p class="m-0 mb-2 text-[13px] text-[#6E6E73]">
          平台客服默认看不到你公司的数据。只有在你需要帮助时，客服登记原因后才能进入，每次进入都会记录在这里，2 小时后自动失效。
        </p>
        <Table row-key="id" size="small" :data-source="visits" :columns="visitColumns" :pagination="false" :scroll="{ x: 600 }">
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'enterTime'">{{ fmtDateTime(record.enterTime) }}</template>
            <template v-else-if="column.dataIndex === 'exitTime'">{{ record.exitTime ? fmtDateTime(record.exitTime) : '到期自动结束' }}</template>
          </template>
        </Table>
      </CollapsePanel>
    </Collapse>

    <Modal v-model:open="open" :title="form.id ? '编辑成员' : '添加成员'" :confirm-loading="saving" ok-text="保存" @ok="save">
      <Form layout="vertical" class="mt-3">
        <FormItem label="姓名" required><Input v-model:value="form.nickname" :maxlength="30" placeholder="例如：张三" /></FormItem>
        <FormItem label="登录账号" required extra="邮箱或手机号，用来登录，添加后不能修改；不能和其他公司的账号重复">
          <Input v-model:value="form.username" :maxlength="64" :disabled="!!form.id" placeholder="例如：zhangsan@example.com" />
        </FormItem>
        <FormItem v-if="!form.id" label="登录密码" required :extra="PASSWORD_TIP"><InputPassword v-model:value="form.password" :maxlength="64" /></FormItem>
        <FormItem label="角色">
          <RadioGroup v-model:value="form.role" :disabled="form.id === myId">
            <Radio value="sales">成员（只看自己负责的客户和会话）</Radio>
            <Radio value="admin">管理员（全部功能和本公司全部数据）</Radio>
          </RadioGroup>
          <div v-if="form.role === 'admin' && form.id !== myId && adminCount >= 1" class="mt-1 text-xs text-[#c93400]">
            管理员能看到本公司所有客户和会话、修改公司设置，请只给负责人。
          </div>
        </FormItem>
        <FormItem label="手机（可选）"><Input v-model:value="form.mobile" :maxlength="20" /></FormItem>
        <FormItem label="邮箱（可选）"><Input v-model:value="form.email" :maxlength="50" /></FormItem>
      </Form>
    </Modal>

    <Modal v-model:open="pwdOpen" :title="`重置「${pwdRow?.nickname}」的密码`" ok-text="确定" @ok="savePwd">
      <InputPassword v-model:value="pwd" class="mt-3" :maxlength="64" placeholder="新密码" />
      <div class="mt-1 text-xs text-[#6E6E73]">{{ PASSWORD_TIP }}。重置后，这个账号因输错密码造成的临时锁定也会一并解除。</div>
    </Modal>
  </div>
</template>
