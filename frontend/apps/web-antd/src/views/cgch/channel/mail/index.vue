<script lang="ts" setup>
/**
 * 邮件工作台：左侧邮箱与文件夹，中间邮件列表，右侧正文、往来记录与回复。
 * 邮件 HTML 放在禁用脚本的 iframe 里展示，避免来信中的脚本执行。
 */
import type { ChannelMailApi } from '#/api/channel/mail';
import type { ChannelAiApi } from '#/api/channel/ai';
import { closeDraft, draftMail, DRAFT_REASON, getPendingDraft } from '#/api/channel/ai';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  Badge,
  Button,
  Empty,
  Input,
  InputSearch,
  List,
  ListItem,
  message,
  Modal,
  Pagination,
  Popconfirm,
  Select,
  Spin,
  Tag,
  Textarea,
} from 'ant-design-vue';

import {
  formatSize,
  getMail,
  getMailAccountList,
  getMailPage,
  getMailThread,
  sendMail,
  syncMailAccount,
  trashMail,
  uploadMailAttachment,
} from '#/api/channel/mail';

defineOptions({ name: 'CgchChannelMail' });

/** 嵌入「会话」页时：邮箱由左栏选择，隐藏本页的邮箱下拉 */
const props = defineProps<{ accountId?: number; embedded?: boolean; openId?: number }>();

const router = useRouter();
const route = useRoute();

const VIEWS: { key: ChannelMailApi.View; label: string }[] = [
  { key: 'inbox', label: '收件箱' },
  { key: 'unreplied', label: '未回复' },
  { key: 'sent', label: '已发送' },
  { key: 'trash', label: '已删除' },
];

const accounts = ref<ChannelMailApi.Account[]>([]);
const accountId = ref<number | undefined>(props.accountId); // undefined = 全部邮箱
watch(
  () => props.accountId,
  (v) => {
    accountId.value = v;
  },
);
const view = ref<ChannelMailApi.View>('inbox');
const keyword = ref('');
const pageNo = ref(1);
const pageSize = 20;
const total = ref(0);
const list = ref<ChannelMailApi.Message[]>([]);
const listLoading = ref(false);

const current = ref<ChannelMailApi.Message>();
const thread = ref<ChannelMailApi.Message[]>([]);
const detailLoading = ref(false);

const accountOptions = computed(() => [
  { label: '全部邮箱', value: undefined as any },
  ...accounts.value.map((a) => ({ label: a.email, value: a.id })),
]);
const unrepliedTotal = computed(() => accounts.value.reduce((n, a) => n + (a.unrepliedCount ?? 0), 0));

async function loadAccounts() {
  accounts.value = await getMailAccountList();
}

async function loadList() {
  listLoading.value = true;
  try {
    const res = await getMailPage({
      pageNo: pageNo.value,
      pageSize,
      accountId: accountId.value,
      view: view.value,
      keyword: keyword.value || undefined,
    });
    list.value = res.list;
    total.value = res.total;
  } finally {
    listLoading.value = false;
  }
}

watch([accountId, view], () => {
  pageNo.value = 1;
  current.value = undefined;
  loadList();
});

async function open(item: ChannelMailApi.Message) {
  detailLoading.value = true;
  try {
    const [detail, history] = await Promise.all([getMail(item.id), getMailThread(item.id)]);
    current.value = detail;
    thread.value = history;
    item.readStatus = true;
    resetReply();
    aiDraft.value = detail.direction === 1 ? ((await getPendingDraft('mail', detail.id)) ?? undefined) : undefined;
  } finally {
    detailLoading.value = false;
  }
}

const syncing = ref(false);
async function syncAll() {
  syncing.value = true;
  try {
    const ids = accountId.value ? [accountId.value] : accounts.value.map((a) => a.id!);
    let n = 0;
    for (const id of ids) n += await syncMailAccount(id);
    message.success(n > 0 ? `收到 ${n} 封新邮件` : '没有新邮件');
    await Promise.all([loadAccounts(), loadList()]);
  } finally {
    syncing.value = false;
  }
}

async function trash() {
  if (!current.value) return;
  await trashMail(current.value.id);
  message.success('已移到已删除');
  current.value = undefined;
  await loadList();
}

// ===== 附件（回复与写邮件共用） =====
const MAX_ATTACHMENT = 25 * 1024 * 1024;
const uploading = ref(false);
async function pickFiles(target: ChannelMailApi.Attachment[]) {
  const input = document.createElement('input');
  input.type = 'file';
  input.multiple = true;
  input.onchange = async () => {
    const files = [...(input.files ?? [])];
    const tooBig = files.filter((f) => f.size > MAX_ATTACHMENT);
    if (tooBig.length > 0) message.warning(`单个附件不能超过 25MB：${tooBig.map((f) => f.name).join('、')}`);
    uploading.value = true;
    try {
      for (const f of files.filter((x) => x.size <= MAX_ATTACHMENT)) target.push(await uploadMailAttachment(f));
    } finally {
      uploading.value = false;
    }
  };
  input.click();
}
function removeFile(target: ChannelMailApi.Attachment[], id: number) {
  const i = target.findIndex((a) => a.id === id);
  if (i !== -1) target.splice(i, 1);
}

// ===== AI 起草 =====
const aiDraft = ref<ChannelAiApi.Draft>();
const aiLoading = ref(false);
const usedDraftId = ref<number>();
async function aiCompose() {
  if (!current.value) return;
  aiLoading.value = true;
  try {
    const d = await draftMail(current.value.id);
    if (d.reason === 'handoff') {
      aiDraft.value = d;
      message.warning('AI 判断这封邮件需要人工处理');
      return;
    }
    replyText.value = d.content;
    usedDraftId.value = d.id;
    aiDraft.value = undefined;
  } finally {
    aiLoading.value = false;
  }
}
function useDraft() {
  if (!aiDraft.value) return;
  replyText.value = aiDraft.value.content;
  usedDraftId.value = aiDraft.value.id;
  aiDraft.value = undefined;
}
async function discardDraft() {
  if (!aiDraft.value) return;
  await closeDraft(aiDraft.value.id, false);
  aiDraft.value = undefined;
}

// ===== 回复 =====
const replyText = ref('');
const replySending = ref(false);
const replyFiles = ref<ChannelMailApi.Attachment[]>([]);
function resetReply() {
  replyText.value = '';
  replyFiles.value = [];
}
function quote(m: ChannelMailApi.Message) {
  const body = (m.textBody || '').split('\n').map((l) => `> ${l}`).join('\n');
  return `\n\nOn ${m.sentTime}, ${m.fromName || m.fromAddress} wrote:\n${body}`;
}
async function reply() {
  const m = current.value;
  if (!m || !replyText.value.trim()) return;
  replySending.value = true;
  try {
    const subject = /^re:/i.test(m.subject || '') ? m.subject : `Re: ${m.subject || ''}`;
    await sendMail({
      accountId: m.accountId,
      to: [m.fromAddress!],
      subject,
      text: replyText.value + quote(m),
      replyToId: m.id,
      attachmentIds: replyFiles.value.map((a) => a.id),
    });
    if (usedDraftId.value) await closeDraft(usedDraftId.value, true);
    usedDraftId.value = undefined;
    message.success('已发送');
    resetReply();
    await Promise.all([open(m), loadList(), loadAccounts()]);
  } finally {
    replySending.value = false;
  }
}

// ===== 写邮件 =====
const composeOpen = ref(false);
const compose = reactive({ accountId: undefined as number | undefined, to: '', cc: '', subject: '', text: '' });
const composeFiles = ref<ChannelMailApi.Attachment[]>([]);
function openCompose() {
  Object.assign(compose, { accountId: accountId.value ?? accounts.value[0]?.id, to: '', cc: '', subject: '', text: '' });
  composeFiles.value = [];
  composeOpen.value = true;
}
const split = (s: string) => s.split(/[,;\s]+/).map((x) => x.trim()).filter(Boolean);
async function sendCompose() {
  if (!compose.accountId || !compose.to.trim()) {
    message.warning('请选择发件邮箱并填写收件人');
    return;
  }
  await sendMail({
    accountId: compose.accountId,
    to: split(compose.to),
    cc: split(compose.cc),
    subject: compose.subject,
    text: compose.text,
    attachmentIds: composeFiles.value.map((a) => a.id),
  });
  message.success('已发送');
  composeOpen.value = false;
  if (view.value === 'sent') await loadList();
}

const iframeDoc = (html: string) =>
  `<!doctype html><html><head><meta charset="utf-8"><base target="_blank"><style>body{font:14px/1.6 -apple-system,Segoe UI,Arial,sans-serif;margin:0;color:#222;word-break:break-word}img{max-width:100%}</style></head><body>${html}</body></html>`;

onMounted(async () => {
  await loadAccounts();
  await loadList();
  // 从工作报告等页面跳转过来时，直接打开指定邮件：/channel-msg/mail?id=123
  const id = props.openId || Number(route.query.id);
  if (id) await open({ id } as ChannelMailApi.Message);
});
</script>

<template>
  <Page auto-content-height>
    <div v-if="accounts.length === 0" class="flex h-full items-center justify-center rounded bg-white">
      <Empty description="还没有绑定邮箱">
        <Button type="primary" @click="router.push('/v3/channel')">去绑定邮箱</Button>
      </Empty>
    </div>

    <div v-else class="flex h-full gap-3">
      <!-- 左：邮箱与文件夹 -->
      <div class="flex w-52 shrink-0 flex-col gap-3 rounded bg-white p-3">
        <Button type="primary" block @click="openCompose">写邮件</Button>
        <Select v-if="!embedded" v-model:value="accountId" :options="accountOptions" />
        <div class="flex flex-col">
          <div
            v-for="v in VIEWS"
            :key="v.key"
            class="flex cursor-pointer items-center justify-between rounded px-3 py-2"
            :class="view === v.key ? 'bg-blue-50 font-medium text-blue-600' : 'hover:bg-gray-50'"
            @click="view = v.key"
          >
            <span>{{ v.label }}</span>
            <Badge v-if="v.key === 'unreplied' && unrepliedTotal" :count="unrepliedTotal" />
          </div>
        </div>
        <Button block :loading="syncing" @click="syncAll">立即收信</Button>
      </div>

      <!-- 中：邮件列表 -->
      <div class="flex w-96 shrink-0 flex-col rounded bg-white">
        <div class="p-3">
          <InputSearch v-model:value="keyword" allow-clear placeholder="搜索主题、发件人" @search="() => { pageNo = 1; loadList(); }" />
        </div>
        <div class="flex-1 overflow-auto">
          <Spin :spinning="listLoading">
            <List :data-source="list" :locale="{ emptyText: '暂无邮件' }">
              <template #renderItem="{ item }">
                <ListItem
                  class="cursor-pointer !px-3"
                  :class="current?.id === item.id ? 'bg-blue-50' : 'hover:bg-gray-50'"
                  @click="open(item)"
                >
                  <div class="w-full min-w-0">
                    <div class="flex items-center justify-between gap-2">
                      <span class="truncate" :class="item.readStatus ? '' : 'font-semibold'">
                        {{ item.direction === 2 ? `发给 ${item.toAddresses}` : item.fromName || item.fromAddress }}
                      </span>
                      <span class="shrink-0 text-xs text-gray-400">{{ item.sentTime?.slice(5, 16) }}</span>
                    </div>
                    <div class="truncate text-sm" :class="item.readStatus ? 'text-gray-700' : 'font-semibold'">
                      {{ item.subject || '（无主题）' }}
                    </div>
                    <div class="truncate text-xs text-gray-400">{{ item.snippet }}</div>
                    <div class="mt-1 flex gap-1">
                      <Tag v-if="item.customerName" color="blue">{{ item.customerName }}</Tag>
                      <Tag v-else-if="item.clueName" color="cyan">线索 · {{ item.clueName }}</Tag>
                      <Tag v-if="item.direction === 1 && item.replied" color="green">已回复</Tag>
                      <Tag v-if="item.hasAttachments">📎 附件</Tag>
                      <Tag v-if="item.aiGenerated" color="purple">AI 回复</Tag>
                    </div>
                  </div>
                </ListItem>
              </template>
            </List>
          </Spin>
        </div>
        <div class="flex justify-center border-t p-2">
          <Pagination v-model:current="pageNo" :page-size="pageSize" :total="total" size="small" simple @change="loadList" />
        </div>
      </div>

      <!-- 右：正文与回复 -->
      <div class="flex min-w-0 flex-1 flex-col rounded bg-white">
        <Spin :spinning="detailLoading" class="h-full">
          <div v-if="!current" class="flex h-full items-center justify-center p-10">
            <Empty description="选择一封邮件查看" />
          </div>
          <div v-else class="flex h-full flex-col">
            <div class="border-b p-4">
              <div class="flex items-start justify-between gap-3">
                <h3 class="text-lg font-semibold">{{ current.subject || '（无主题）' }}</h3>
                <Popconfirm v-if="current.folder !== 'TRASH'" title="移到已删除？" @confirm="trash">
                  <Button size="small" danger>删除</Button>
                </Popconfirm>
              </div>
              <div class="mt-1 text-sm text-gray-600">
                {{ current.fromName }} &lt;{{ current.fromAddress }}&gt; → {{ current.toAddresses }}
                <span v-if="current.ccAddresses">，抄送 {{ current.ccAddresses }}</span>
              </div>
              <div class="mt-1 flex items-center gap-2 text-xs text-gray-400">
                <span>{{ current.sentTime }}</span>
                <Tag v-if="current.customerName" color="blue" class="cursor-pointer" @click="router.push(`/crm/customer/detail/${current.customerId}`)">
                  客户：{{ current.customerName }}
                </Tag>
                <Tag v-else-if="current.clueName" color="cyan" class="cursor-pointer" @click="router.push(`/crm/clue/detail/${current.clueId}`)">
                  线索：{{ current.clueName }}
                </Tag>
                <Tag v-else-if="current.direction === 1">未关联客户</Tag>
              </div>
            </div>

            <div class="flex-1 overflow-auto p-4">
              <iframe
                v-if="current.htmlBody"
                :srcdoc="iframeDoc(current.htmlBody)"
                sandbox="allow-popups allow-popups-to-escape-sandbox"
                class="h-[420px] w-full border-0"
              ></iframe>
              <pre v-else class="whitespace-pre-wrap font-sans text-sm">{{ current.textBody }}</pre>

              <div v-if="current.attachments?.length" class="mt-4 flex flex-wrap gap-2">
                <a
                  v-for="a in current.attachments"
                  :key="a.id"
                  :href="a.fileUrl"
                  target="_blank"
                  rel="noopener"
                  class="flex items-center gap-2 rounded border px-3 py-2 text-sm hover:bg-gray-50"
                >
                  <span>📎 {{ a.fileName }}</span>
                  <span class="text-xs text-gray-400">{{ formatSize(a.fileSize) }}</span>
                </a>
              </div>

              <div v-if="thread.length > 1" class="mt-6">
                <div class="mb-2 text-sm font-medium text-gray-500">往来记录（{{ thread.length }} 封）</div>
                <div
                  v-for="t in thread"
                  :key="t.id"
                  class="mb-2 rounded border-l-4 p-2 text-sm"
                  :class="t.direction === 2 ? 'border-green-400 bg-green-50' : 'border-blue-400 bg-blue-50'"
                >
                  <div class="text-xs text-gray-500">
                    {{ t.direction === 2 ? '我方' : t.fromName || t.fromAddress }} · {{ t.sentTime }}
                  </div>
                  <div class="truncate">{{ t.snippet }}</div>
                </div>
              </div>
            </div>

            <div v-if="current.direction === 1 && current.folder !== 'TRASH'" class="border-t p-4">
              <div v-if="aiDraft" class="mb-2 rounded border border-orange-200 bg-orange-50 p-3 text-sm">
                <div class="mb-1 font-medium text-orange-700">
                  AI 已准备回复，未自动发出：{{ DRAFT_REASON[aiDraft.reason || ''] ?? aiDraft.reason }}
                  <span v-if="aiDraft.reasonDetail">（{{ aiDraft.reasonDetail }}）</span>
                </div>
                <div v-if="aiDraft.content" class="line-clamp-3 whitespace-pre-wrap text-gray-700">{{ aiDraft.content }}</div>
                <div class="mt-2 flex gap-2">
                  <Button v-if="aiDraft.content" size="small" type="primary" ghost @click="useDraft">填入回复框</Button>
                  <Button size="small" @click="discardDraft">忽略</Button>
                </div>
              </div>
              <Textarea v-model:value="replyText" :rows="5" placeholder="输入回复内容，原邮件会自动引用在下方" />
              <div v-if="replyFiles.length" class="mt-2 flex flex-wrap gap-2">
                <Tag v-for="a in replyFiles" :key="a.id" closable @close="removeFile(replyFiles, a.id)">
                  📎 {{ a.fileName }} {{ formatSize(a.fileSize) }}
                </Tag>
              </div>
              <div class="mt-2 flex justify-between">
                <div class="flex gap-2">
                  <Button :loading="uploading" @click="pickFiles(replyFiles)">添加附件</Button>
                  <Button :loading="aiLoading" @click="aiCompose">AI 起草</Button>
                </div>
                <Button type="primary" :loading="replySending" :disabled="!replyText.trim() || uploading" @click="reply">发送回复</Button>
              </div>
            </div>
          </div>
        </Spin>
      </div>
    </div>

    <Modal v-model:open="composeOpen" title="写邮件" :width="680" ok-text="发送" @ok="sendCompose">
      <div class="flex flex-col gap-3">
        <Select
          v-model:value="compose.accountId"
          :options="accounts.map((a) => ({ label: a.email, value: a.id }))"
          placeholder="发件邮箱"
        />
        <Input v-model:value="compose.to" placeholder="收件人，多个用逗号分隔" />
        <Input v-model:value="compose.cc" placeholder="抄送（可选）" />
        <Input v-model:value="compose.subject" placeholder="主题" />
        <Textarea v-model:value="compose.text" :rows="10" placeholder="正文" />
        <div class="flex flex-wrap items-center gap-2">
          <Button size="small" :loading="uploading" @click="pickFiles(composeFiles)">添加附件</Button>
          <Tag v-for="a in composeFiles" :key="a.id" closable @close="removeFile(composeFiles, a.id)">
            📎 {{ a.fileName }} {{ formatSize(a.fileSize) }}
          </Tag>
        </div>
      </div>
    </Modal>
  </Page>
</template>
