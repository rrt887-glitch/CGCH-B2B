<script lang="ts" setup>
/**
 * 成员：只有两种账号。
 * - 超级管理员：全部功能，看得到所有人的客户和会话，可以添加成员、配置大模型和渠道等公司级设置；
 * - 业务员：日常接待和跟进客户，只看自己负责的数据。
 * 不需要部门和岗位：每家公司单独部署一套，超级管理员在这里添加业务员即可。
 */
import type { SystemUserApi } from '#/api/system/user';

import { computed, onMounted, reactive, ref } from 'vue';

import { useUserStore } from '@vben/stores';

import { Button, Form, FormItem, Input, InputPassword, message, Modal, Radio, RadioGroup, Switch, Table, Tag } from 'ant-design-vue';

import { assignUserRole, getUserRoleList } from '#/api/system/permission';
import { createUser, deleteUser, getUserPage, resetUserPassword, updateUser, updateUserStatus } from '#/api/system/user';

defineOptions({ name: 'CgchV3Members' });

/** 角色编号：超级管理员（系统内置）与业务员（数据库升级 032 创建） */
const ROLE_ADMIN = 1;
const ROLE_SALES = 300;
type RoleKind = 'admin' | 'other' | 'sales';
const ROLE_LABEL: Record<RoleKind, { color: string; label: string }> = {
  admin: { color: 'blue', label: '超级管理员' },
  sales: { color: 'default', label: '业务员' },
  other: { color: 'orange', label: '未设置角色' },
};

const userStore = useUserStore();
const myId = computed(() => Number((userStore.userInfo as any)?.id ?? (userStore.userInfo as any)?.userId));

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
        u.role = roles.includes(ROLE_ADMIN) ? 'admin' : roles.includes(ROLE_SALES) ? 'sales' : 'other';
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
  if (!/^[a-zA-Z0-9]{4,30}$/.test(form.username)) return message.warning('登录账号为 4~30 位字母或数字');
  if (!form.id && form.password.length < 4) return message.warning('请设置至少 4 位的登录密码');
  if (form.id === myId.value && form.role !== 'admin') return message.warning('不能把自己改成业务员，请让其他超级管理员操作');
  saving.value = true;
  try {
    const data = {
      id: form.id,
      username: form.username,
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
    await assignUserRole({ userId: id!, roleIds: [form.role === 'admin' ? ROLE_ADMIN : ROLE_SALES] } as any);
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
  if (pwd.value.length < 4) return message.warning('请设置至少 4 位的新密码');
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
  { title: '登录账号', dataIndex: 'username', width: 160 },
  { title: '角色', dataIndex: 'role', width: 120 },
  { title: '手机', dataIndex: 'mobile', width: 140 },
  { title: '邮箱', dataIndex: 'email', ellipsis: true },
  { title: '启用', dataIndex: 'status', width: 90 },
  { title: '操作', key: 'actions', width: 200 },
];

onMounted(load);
</script>

<template>
  <div class="flex h-full flex-col gap-4 overflow-auto rounded-xl bg-white p-3 sm:p-5">
    <div class="flex flex-wrap items-start gap-3">
      <div class="min-w-0 flex-1 basis-[240px]">
        <h2 class="m-0 text-lg font-semibold text-[#1D1D1F]">成员</h2>
        <p class="m-0 mt-1 text-[13px] text-[#6E6E73]">
          超级管理员可以使用全部功能、看到所有人的客户和会话；业务员只看自己负责的客户和会话，不能修改大模型、知识库等公司设置。
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

    <Modal v-model:open="open" :title="form.id ? '编辑成员' : '添加成员'" :confirm-loading="saving" ok-text="保存" @ok="save">
      <Form layout="vertical" class="mt-3">
        <FormItem label="姓名" required><Input v-model:value="form.nickname" :maxlength="30" placeholder="例如：张三" /></FormItem>
        <FormItem label="登录账号" required extra="4~30 位字母或数字，添加后不能修改">
          <Input v-model:value="form.username" :maxlength="30" :disabled="!!form.id" placeholder="例如：zhangsan" />
        </FormItem>
        <FormItem v-if="!form.id" label="登录密码" required><InputPassword v-model:value="form.password" :maxlength="64" /></FormItem>
        <FormItem label="角色">
          <RadioGroup v-model:value="form.role" :disabled="form.id === myId">
            <Radio value="sales">业务员（只看自己负责的客户和会话）</Radio>
            <Radio value="admin">超级管理员（全部功能和数据）</Radio>
          </RadioGroup>
          <div v-if="form.role === 'admin' && form.id !== myId && adminCount >= 1" class="mt-1 text-xs text-[#c93400]">
            超级管理员能看到所有客户和会话、修改公司设置，请只给负责人。
          </div>
        </FormItem>
        <FormItem label="手机（可选）"><Input v-model:value="form.mobile" :maxlength="20" /></FormItem>
        <FormItem label="邮箱（可选）"><Input v-model:value="form.email" :maxlength="50" /></FormItem>
      </Form>
    </Modal>

    <Modal v-model:open="pwdOpen" :title="`重置「${pwdRow?.nickname}」的密码`" ok-text="确定" @ok="savePwd">
      <InputPassword v-model:value="pwd" class="mt-3" :maxlength="64" placeholder="新密码，至少 4 位" />
    </Modal>
  </div>
</template>
