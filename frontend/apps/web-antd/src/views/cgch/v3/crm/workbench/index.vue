<script lang="ts" setup>
/**
 * 外贸 B2B 客户管理主工作台（含租户管理员全套分流派单与巡检能力）：
 * - 筛选栏支持「按负责人筛选」（选同事时自动切换全团队可见范围），看板卡片显示负责人归属角标与一键派单浮窗
 * - 新建客户弹窗支持直接指定归属负责人
 * - 右上角「分流与规则」抽屉集成：询盘自动分流规则 + 彩色客户标签 + 公海自动回收规则
 */
import type { LatestResearch } from '#/api/channel/research';
import type { CgchCrmApi } from '#/api/crm/cgch';

import {
  computed,
  defineAsyncComponent,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import {
  Drawer,
  Dropdown,
  Empty,
  Input,
  InputNumber,
  Menu,
  MenuItem,
  message,
  Modal,
  Popover,
  Select,
  Spin,
  Table,
  Tooltip,
  Upload,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import { createResearch, getLatestResearch } from '#/api/channel/research';
import { getTagList } from '#/api/crm/cgch';
import { getCluePage } from '#/api/crm/clue';
import {
  createCustomer,
  exportCustomer,
  getCustomerPage,
  importCustomer,
  importCustomerTemplate,
  putCustomerPool,
  transferCustomer,
  updateCustomerStage,
} from '#/api/crm/customer';
import { getSimpleUserList } from '#/api/system/user';
import { COUNTRY_OPTIONS } from '#/views/cgch/crm/shared/countries';
import {
  countryName,
  crmAvatarBg,
  crmInitial,
} from '#/views/cgch/v3/crm/components/shared';

import SettingsView from '../settings.vue';
import {
  buildSafeCustomerPayload,
  quickCreateFollowUp,
  saveCustomerDealBoard,
  toggleCustomerStarred,
} from './crm-actions';
import CustomerDrawer from './customer-drawer.vue';
import type { CurrencyCode } from './deal-meta';
import {
  analyzeCustomerHealth,
  corporateDomain,
  countryFlag,
  CURRENCIES,
  formatDealMoney,
  getBuyerLocalClock,
  getStageMeta,
  parseDealBoard,
  PIPELINE_STAGES,
  serializeDealBoard,
  toUsdAmount,
} from './deal-meta';
import OrdersPanel from './orders-panel.vue';
import PoolPanel from './pool-panel.vue';
import QuickOrderModal from './quick-order-modal.vue';
import TriageDrawer from './triage-drawer.vue';

defineOptions({ name: 'CgchCrmWorkbench' });

const ClawView = defineAsyncComponent(() => import('../claw.vue'));

type MainTab = 'order' | 'pipeline' | 'pool';
type ViewMode = 'kanban' | 'table';
type QuickChip = 'all' | 'focus' | 'pool_soon' | 'stagnant' | 'urgent';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const myUserId = computed(() => userStore.userInfo?.id);

/** 兼容左侧主导航「CRM 智枢」（/v3/crm?tab=salesmind） */
const isSalesmind = computed(() => route.query.tab === 'salesmind');

const mainTab = ref<MainTab>('pipeline');
const viewMode = ref<ViewMode>(
  (localStorage.getItem('cgch_crm_view_mode') as ViewMode) === 'table'
    ? 'table'
    : 'kanban',
);
function setViewMode(m: ViewMode) {
  viewMode.value = m;
  localStorage.setItem('cgch_crm_view_mode', m);
}

// 客户数据、成员列表与标签
const loading = ref(false);
const allCustomers = ref<any[]>([]);
const researchMap = ref<Record<number, LatestResearch>>({});
const tags = ref<CgchCrmApi.TreeNode[]>([]);
const simpleUsers = ref<{ id: number; nickname: string }[]>([]);

// 每分钟刷新买家当地日夜时钟
const clockTick = ref(Date.now());
let clockTimer: null | ReturnType<typeof setInterval> = null;

// 管道筛选状态（含按负责人筛选）
const sceneScope = ref<'all' | 'mine' | 'sub'>('mine');
const activeStage = ref<'archived' | null | number>(null);
const quickChip = ref<QuickChip>('all');
const keyword = ref('');
const filterTagId = ref<number | undefined>();
const filterOwnerId = ref<number | undefined>();
const tablePage = ref(1);

// 看板拖拽状态 & 卡片行内快捷派单浮窗
const draggingId = ref<null | number>(null);
const dragOverStage = ref<null | number>(null);
const cardAssignPopId = ref<null | number>(null);

// 抽屉状态：待甄别来信 / 分流与规则设置 / 360° 客户全景
const triageOpen = ref(false);
const triageCount = ref(0);
const settingsOpen = ref(false);

const drawerOpen = ref(false);
const activeCustomerId = ref<null | number>(null);
const drawerRef = ref<InstanceType<typeof CustomerDrawer> | null>(null);
const ordersPanelRef = ref<InstanceType<typeof OrdersPanel> | null>(null);
const poolPanelRef = ref<InstanceType<typeof PoolPanel> | null>(null);

// 极简登记/修改订单弹窗
const orderModalOpen = ref(false);
const orderModalCustomer = ref<null | Record<string, any>>(null);
const orderModalRecord = ref<null | Record<string, any>>(null);

// 行内 5 秒快速记跟进 Popover
const quickFollowRowId = ref<null | number>(null);
const quickFollowText = ref('');
const quickFollowDays = ref(3);
const quickFollowSubmitting = ref(false);

// 表格多选与批量操作
const selectedIds = ref<number[]>([]);
const transferOpen = ref(false);
const transferTo = ref<number | undefined>();
const transferring = ref(false);

// 导入客户弹窗
const importOpen = ref(false);
const importFile = ref<File>();
const importFileList = ref<any[]>([]);
const importUpdate = ref(false);
const importing = ref(false);

// 极简新建外贸客户弹窗（支持管理员直接指定负责人）
const createOpen = ref(false);
const creating = ref(false);
const newCustForm = reactive({
  name: '',
  companyName: '',
  countryCode: 'US' as string | undefined,
  whatsapp: '',
  email: '',
  website: '',
  source: undefined as number | undefined,
  stage: 1,
  ownerUserId: undefined as number | undefined,
  currency: 'USD' as CurrencyCode,
  amount: undefined as number | undefined,
  productQty: '',
});

const sourceOptions = computed(() =>
  getDictOptions(DICT_TYPE.CRM_CUSTOMER_SOURCE, 'number'),
);

/** 同企业域名映射表：自动检测不同联系人是否属于同一家海外买家公司 */
const domainOwnersMap = computed(() => {
  const map = new Map<string, any[]>();
  for (const c of allCustomers.value) {
    const d = corporateDomain(c.email, c.website);
    if (!d) continue;
    const arr = map.get(d) ?? [];
    arr.push(c);
    map.set(d, arr);
  }
  return map;
});

/** 派生每条客户的商务黑板、健康度、当地时钟与防撞单提醒 */
const enrichedCustomers = computed(() => {
  const nowDate = new Date(clockTick.value);
  return allCustomers.value.map((raw) => {
    const board = parseDealBoard(raw.remark);
    const stageMeta = getStageMeta(raw.stage, raw.dealStatus);
    const health = analyzeCustomerHealth(raw, board);
    const clock = getBuyerLocalClock(raw.countryCode, nowDate);
    const domain = corporateDomain(raw.email, raw.website);
    const peers = domain
      ? (domainOwnersMap.value.get(domain) ?? []).filter((p) => p.id !== raw.id)
      : [];
    const peerHint =
      board.collisionHint ||
      (peers.length > 0
        ? `同企域名 ${domain} 另有 ${peers[0].name}${peers[0].ownerUserName ? `（${peers[0].ownerUserName}）` : ''}`
        : '');
    const usdValue = toUsdAmount(board.amount, board.currency);
    return {
      ...raw,
      _board: board,
      _stage: stageMeta.value,
      _stageMeta: stageMeta,
      _health: health,
      _clock: clock,
      _peerHint: peerHint,
      _usd: usdValue,
    };
  });
});

const activePool = computed(() =>
  enrichedCustomers.value.filter((c) => !c._board.archived),
);
const archivedPool = computed(() =>
  enrichedCustomers.value.filter((c) => c._board.archived),
);

const stageFunnelStats = computed(() =>
  PIPELINE_STAGES.map((st) => {
    const items = activePool.value.filter((c) => c._stage === st.value);
    const usdSum = items.reduce((s, c) => s + c._usd, 0);
    return {
      ...st,
      count: items.length,
      usdSum,
    };
  }),
);

const totalActiveUsd = computed(() =>
  activePool.value.reduce((s, c) => s + c._usd, 0),
);

const chipCounts = computed(() => {
  const base = activePool.value;
  return {
    urgent: base.filter(
      (c) =>
        c._health.overdueDays > 0 ||
        c._health.awaitingFirstReply ||
        c._health.dueToday,
    ).length,
    focus: base.filter((c) => Boolean(c.starred)).length,
    stagnant: base.filter((c) => c._health.stagnant).length,
    pool_soon: base.filter(
      (c) =>
        c.poolDay !== undefined &&
        c.poolDay !== null &&
        c.poolDay <= 3 &&
        !c.dealStatus &&
        !c.lockStatus,
    ).length,
  };
});

const filteredCustomers = computed(() => {
  let list =
    activeStage.value === 'archived' ? archivedPool.value : activePool.value;

  if (typeof activeStage.value === 'number') {
    list = list.filter((c) => c._stage === activeStage.value);
  }

  if (filterOwnerId.value) {
    list = list.filter((c) => c.ownerUserId === filterOwnerId.value);
  }

  if (quickChip.value === 'urgent') {
    list = list.filter(
      (c) =>
        c._health.overdueDays > 0 ||
        c._health.awaitingFirstReply ||
        c._health.dueToday,
    );
  } else if (quickChip.value === 'focus') {
    list = list.filter((c) => Boolean(c.starred));
  } else if (quickChip.value === 'stagnant') {
    list = list.filter((c) => c._health.stagnant);
  } else if (quickChip.value === 'pool_soon') {
    list = list.filter(
      (c) =>
        c.poolDay !== undefined &&
        c.poolDay !== null &&
        c.poolDay <= 3 &&
        !c.dealStatus &&
        !c.lockStatus,
    );
  }

  if (filterTagId.value) {
    list = list.filter(
      (c) => Array.isArray(c.tagIds) && c.tagIds.includes(filterTagId.value),
    );
  }

  const q = keyword.value.trim().toLowerCase();
  if (q) {
    list = list.filter((c) =>
      [
        c.name,
        c.companyName,
        c.email,
        c.whatsapp,
        c.ownerUserName,
        c.countryCode,
        countryName(c.countryCode),
        c._board.productQty,
        c._board.quoteTerms,
        c._board.note,
      ]
        .filter(Boolean)
        .some((v) => String(v).toLowerCase().includes(q)),
    );
  }

  return [...list].sort((a, b) => {
    const score = (x: any) =>
      (x._health.overdueDays > 0 ? 100 : 0) +
      (x._health.awaitingFirstReply ? 80 : 0) +
      (x._health.dueToday ? 50 : 0) +
      (x.starred ? 20 : 0);
    const diff = score(b) - score(a);
    if (diff !== 0) return diff;
    return (
      dayjs(b.updateTime || b.createTime).valueOf() -
      dayjs(a.updateTime || a.createTime).valueOf()
    );
  });
});

const hasActiveFilter = computed(
  () =>
    activeStage.value !== null ||
    quickChip.value !== 'all' ||
    Boolean(filterTagId.value) ||
    Boolean(filterOwnerId.value) ||
    Boolean(keyword.value.trim()),
);

function resetFilters() {
  activeStage.value = null;
  quickChip.value = 'all';
  filterTagId.value = undefined;
  filterOwnerId.value = undefined;
  keyword.value = '';
}

function onSelectOwnerFilter(uid?: number) {
  filterOwnerId.value = uid || undefined;
  // 若选了别的同事而当前还在「我负责的」，自动切至「全部可见」以直接拉出该成员数据
  if (uid && uid !== myUserId.value && sceneScope.value === 'mine') {
    sceneScope.value = 'all';
  }
}

const kanbanColumns = computed(() =>
  PIPELINE_STAGES.map((st) => {
    const cards = filteredCustomers.value.filter((c) => c._stage === st.value);
    const usd = cards.reduce((s, c) => s + c._usd, 0);
    return {
      ...st,
      cards,
      usd,
    };
  }),
);

const activeIndexInList = computed(() =>
  filteredCustomers.value.findIndex((c) => c.id === activeCustomerId.value),
);

function inspectCustomer(id: number) {
  activeCustomerId.value = id;
  drawerOpen.value = true;
}

function stepDrawer(delta: -1 | 1) {
  const nextIdx = activeIndexInList.value + delta;
  const target = filteredCustomers.value[nextIdx];
  if (target?.id) {
    activeCustomerId.value = target.id;
  }
}

async function loadPipeline() {
  loading.value = true;
  try {
    const sceneType =
      sceneScope.value === 'mine' ? 1 : sceneScope.value === 'sub' ? 3 : undefined;
    const first: any = await getCustomerPage({
      pageNo: 1,
      pageSize: 200,
      sceneType,
    } as any);
    const list = first?.list ?? [];
    if ((first?.total ?? 0) > 200 && list.length === 200) {
      const second: any = await getCustomerPage({
        pageNo: 2,
        pageSize: 200,
        sceneType,
      } as any).catch(() => ({ list: [] }));
      list.push(...(second?.list ?? []));
    }
    allCustomers.value = list;

    const ids = list.slice(0, 150).map((c: any) => c.id).filter(Boolean);
    if (ids.length) {
      const rList = await getLatestResearch(ids).catch(() => [] as LatestResearch[]);
      const map: Record<number, LatestResearch> = {};
      for (const r of rList || []) {
        if (r?.customerId) map[r.customerId] = r;
      }
      researchMap.value = map;
    }
  } finally {
    loading.value = false;
  }
}

async function loadTriageBadge() {
  try {
    const res: any = await getCluePage({
      pageNo: 1,
      pageSize: 100,
      transformStatus: false,
    } as any);
    triageCount.value = (res?.list ?? []).filter(
      (c: any) => !c.transformStatus,
    ).length;
  } catch {
    triageCount.value = 0;
  }
}

async function loadTaxonomyAndUsers() {
  const [tList, uList] = await Promise.all([
    getTagList().catch(() => []),
    getSimpleUserList().catch(() => []),
  ]);
  tags.value = tList || [];
  simpleUsers.value = (uList as any[]) || [];
}

/** 看板卡片一键转派给团队成员 */
async function handleCardQuickAssign(card: any, newOwnerId: number) {
  cardAssignPopId.value = null;
  if (!newOwnerId || card.ownerUserId === newOwnerId) return;
  await transferCustomer({
    id: card.id,
    newOwnerUserId: newOwnerId,
  } as any);
  const targetName =
    simpleUsers.value.find((u) => u.id === newOwnerId)?.nickname || '该成员';
  message.success(`已将「${card.name}」指派给 ${targetName}`);
  await loadPipeline();
}

function onCardDragStart(e: DragEvent, card: any) {
  draggingId.value = card.id;
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', String(card.id));
  }
}

function onCardDragEnd() {
  draggingId.value = null;
  dragOverStage.value = null;
}

async function onColumnDrop(targetStage: number) {
  const cid = draggingId.value;
  draggingId.value = null;
  dragOverStage.value = null;
  if (!cid) return;
  const targetCard = allCustomers.value.find((c) => c.id === cid);
  if (!targetCard) return;
  const currentSt = getStageMeta(targetCard.stage, targetCard.dealStatus).value;
  if (currentSt === targetStage && !parseDealBoard(targetCard.remark).archived) {
    return;
  }
  await applyStageChange(targetCard, targetStage, true);
}

async function applyStageChange(
  row: any,
  nextStage: number,
  promptOrderWhenWon = false,
) {
  const prevStage = row.stage;
  row.stage = nextStage;
  try {
    if (parseDealBoard(row.remark).archived) {
      await saveCustomerDealBoard(row, { archived: false, lostReason: '' });
    }
    await updateCustomerStage(row.id, nextStage);
    const meta = getStageMeta(nextStage);
    message.success(`已将「${row.name}」推进至 ${meta.label}`);
    if (promptOrderWhenWon && nextStage === 5) {
      openCreateOrderModal(row);
    }
  } catch {
    row.stage = prevStage;
  }
}

async function onInlineStar(row: any) {
  const next = !row.starred;
  row.starred = next;
  try {
    await toggleCustomerStarred(row.id, next);
  } catch {
    row.starred = !next;
  }
}

async function submitInlineFollow(row: any) {
  if (!quickFollowText.value.trim()) {
    message.warning('请输入跟进要点');
    return;
  }
  quickFollowSubmitting.value = true;
  try {
    const nextMoment = dayjs()
      .add(quickFollowDays.value, 'day')
      .hour(10)
      .minute(0)
      .second(0);
    await quickCreateFollowUp({
      customerId: row.id,
      content: quickFollowText.value.trim(),
      nextTime: nextMoment.toDate(),
    });
    row.contactNextTime = nextMoment.toISOString();
    row.contactLastTime = new Date().toISOString();
    row.contactLastContent = quickFollowText.value.trim();
    quickFollowText.value = '';
    quickFollowRowId.value = null;
    message.success(`已记跟进，下次提醒：${nextMoment.format('MM-DD')}`);
  } finally {
    quickFollowSubmitting.value = false;
  }
}

function openCreateOrderModal(cust?: null | Record<string, any>) {
  orderModalRecord.value = null;
  orderModalCustomer.value = cust ?? null;
  orderModalOpen.value = true;
}

function openEditOrderModal(
  ord: Record<string, any>,
  cust?: null | Record<string, any>,
) {
  orderModalRecord.value = ord;
  orderModalCustomer.value = cust ?? null;
  orderModalOpen.value = true;
}

function handleOrderSaved() {
  void loadPipeline();
  drawerRef.value?.reloadOrders();
  ordersPanelRef.value?.reload();
}

function openCreateCustomerModal() {
  newCustForm.ownerUserId = userStore.userInfo?.id;
  createOpen.value = true;
}

async function handleCreateCustomer() {
  if (!newCustForm.name.trim() && !newCustForm.companyName.trim()) {
    message.warning('请填写买家姓名或海外公司名称');
    return;
  }
  creating.value = true;
  try {
    const targetOwner = newCustForm.ownerUserId || userStore.userInfo?.id || 1;
    const remark = serializeDealBoard({
      currency: newCustForm.currency,
      amount: newCustForm.amount,
      productQty: newCustForm.productQty,
      quoteTerms: '',
      sampleInfo: '',
      note: '',
    });
    const payload = buildSafeCustomerPayload(
      {},
      {
        name: newCustForm.name.trim() || newCustForm.companyName.trim(),
        companyName: newCustForm.companyName.trim() || undefined,
        countryCode: newCustForm.countryCode,
        whatsapp: newCustForm.whatsapp.trim() || undefined,
        email: newCustForm.email.trim() || undefined,
        website: newCustForm.website.trim() || undefined,
        source: newCustForm.source,
        remark,
        ownerUserId: targetOwner,
      },
    );
    const createdId: any = await createCustomer(payload as any);
    if (typeof createdId === 'number' && createdId > 0) {
      if (newCustForm.stage > 1) {
        await updateCustomerStage(createdId, newCustForm.stage).catch(() => undefined);
      }
      // 若指定给了其他成员，确保权限同步移交
      if (targetOwner !== myUserId.value) {
        await transferCustomer({
          id: createdId,
          newOwnerUserId: targetOwner,
        } as any).catch(() => undefined);
      }
    }
    message.success('新客户已建档入管道');
    createOpen.value = false;
    Object.assign(newCustForm, {
      name: '',
      companyName: '',
      countryCode: 'US',
      whatsapp: '',
      email: '',
      website: '',
      source: undefined,
      stage: 1,
      ownerUserId: userStore.userInfo?.id,
      currency: 'USD',
      amount: undefined,
      productQty: '',
    });
    await loadPipeline();
    if (
      typeof createdId === 'number' &&
      createdId > 0 &&
      targetOwner === myUserId.value
    ) {
      inspectCustomer(createdId);
    }
  } finally {
    creating.value = false;
  }
}

async function openBatchTransfer() {
  if (!simpleUsers.value.length) {
    simpleUsers.value = ((await getSimpleUserList().catch(() => [])) as any[]) || [];
  }
  transferTo.value = undefined;
  transferOpen.value = true;
}

async function confirmBatchTransfer() {
  if (!transferTo.value) {
    message.warning('请选择新的负责人');
    return;
  }
  transferring.value = true;
  let ok = 0;
  try {
    for (const id of selectedIds.value) {
      try {
        await transferCustomer({ id, newOwnerUserId: transferTo.value } as any);
        ok++;
      } catch {
        // 跳过单条异常
      }
    }
    message.success(`已成功转交 ${ok} 位客户`);
    transferOpen.value = false;
    selectedIds.value = [];
    await loadPipeline();
  } finally {
    transferring.value = false;
  }
}

async function handleBatchResearch() {
  const targets = allCustomers.value.filter((c) =>
    selectedIds.value.includes(c.id),
  );
  if (!targets.length) return;
  let count = 0;
  for (const c of targets.slice(0, 10)) {
    try {
      await createResearch({
        customerId: c.id,
        companyName: c.companyName || c.name,
        website: c.website || undefined,
      });
      count++;
    } catch {
      // 跳过异常
    }
  }
  selectedIds.value = [];
  message.success(`已为 ${count} 位客户后台启动 AI 深度背调`);
}

async function handleBatchPutPool() {
  let ok = 0;
  for (const id of selectedIds.value) {
    try {
      await putCustomerPool(id);
      ok++;
    } catch {
      // 跳过
    }
  }
  selectedIds.value = [];
  message.success(`已将 ${ok} 位客户放回公海池`);
  await loadPipeline();
  poolPanelRef.value?.reload();
}

function pickImportFile(file: File) {
  importFile.value = file;
  importFileList.value = [file as any];
  return false;
}

async function handleDoImport() {
  if (!importFile.value) {
    message.warning('请先选择 Excel 文件');
    return;
  }
  importing.value = true;
  try {
    await importCustomer({
      file: importFile.value,
      updateSupport: importUpdate.value,
      ownerUserId: userStore.userInfo?.id ?? 1,
    } as any);
    message.success('客户批量导入成功');
    importOpen.value = false;
    importFile.value = undefined;
    importFileList.value = [];
    await loadPipeline();
  } finally {
    importing.value = false;
  }
}

async function handleExportCustomers() {
  const sceneType =
    sceneScope.value === 'mine' ? 1 : sceneScope.value === 'sub' ? 3 : undefined;
  await exportCustomer({
    sceneType,
    stage: typeof activeStage.value === 'number' ? activeStage.value : undefined,
    name: keyword.value.trim() || undefined,
  });
}

function tagNameById(id: number) {
  return tags.value.find((t) => t.id === id)?.name;
}

function tagColorById(id: number) {
  return tags.value.find((t) => t.id === id)?.color || '#0071E3';
}

const tableColumns = [
  { title: '海外买家 / 公司', key: 'buyer', width: 260 },
  { title: '国家与当地时区', key: 'country', width: 175 },
  { title: '销售阶段', key: 'stage', width: 145 },
  { title: '商务黑板（单值 / 意向产品）', key: 'board', width: 235 },
  { title: 'AI 背调', key: 'research', width: 150 },
  { title: '跟进状态与预警', key: 'next', width: 180 },
  { title: '负责人', key: 'owner', width: 100 },
  { title: '', key: 'quick', width: 92, align: 'right' as const },
];

watch(
  () => route.query,
  (q) => {
    if (q.tab === 'salesmind') return;
    if (q.tab === 'pool') mainTab.value = 'pool';
    else if (q.tab === 'order') mainTab.value = 'order';
    else if (q.tab === 'settings') settingsOpen.value = true;
    else if (q.tab === 'agenda') {
      mainTab.value = 'pipeline';
      quickChip.value = 'urgent';
    }
    if (q.customerId && Number(q.customerId) > 0) {
      inspectCustomer(Number(q.customerId));
    }
    if (q.triage === '1') {
      triageOpen.value = true;
    }
  },
  { immediate: true },
);

watch(sceneScope, (sc) => {
  tablePage.value = 1;
  if (sc === 'mine' && filterOwnerId.value && filterOwnerId.value !== myUserId.value) {
    filterOwnerId.value = undefined;
  }
  void loadPipeline();
});

onMounted(() => {
  if (!isSalesmind.value) {
    void loadPipeline();
    void loadTriageBadge();
    void loadTaxonomyAndUsers();
  }
  clockTimer = setInterval(() => {
    clockTick.value = Date.now();
  }, 60_000);
});

watch(isSalesmind, (sm) => {
  if (!sm && allCustomers.value.length === 0) {
    void loadPipeline();
    void loadTriageBadge();
    void loadTaxonomyAndUsers();
  }
});

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer);
});
</script>

<template>
  <!-- 路由为 /v3/crm?tab=salesmind 时无缝呈现 CRM 智枢工作台 -->
  <ClawView v-if="isSalesmind" />

  <div v-else class="wb-page">
    <!-- ==================== 1. 顶部精简导航与上下文动作栏 ==================== -->
    <header class="wb-top">
      <div class="flex flex-wrap items-center gap-3">
        <nav class="wb-main-tabs">
          <button
            v-for="item in [
              { k: 'pipeline', l: '客户管道', icon: 'lucide:kanban' },
              { k: 'pool', l: '公海淘金', icon: 'lucide:globe' },
              { k: 'order', l: '成交订单', icon: 'lucide:receipt' },
            ]"
            :key="item.k"
            type="button"
            class="wb-main-tab"
            :class="{ on: mainTab === item.k }"
            @click="mainTab = item.k as MainTab"
          >
            <IconifyIcon :icon="item.icon" class="size-3.5" />
            <span>{{ item.l }}</span>
          </button>
        </nav>

        <div
          v-if="mainTab === 'pipeline' && totalActiveUsd > 0"
          class="wb-pipeline-sum"
        >
          <span>活跃管道预估总额</span>
          <strong>{{ formatDealMoney(totalActiveUsd, 'USD') }}</strong>
        </div>
      </div>

      <!-- 右侧按当前板块智能切换动作按钮 -->
      <div class="flex flex-wrap items-center gap-2">
        <template v-if="mainTab === 'pipeline'">
          <button
            type="button"
            class="wb-triage-pill"
            :class="{ active: triageCount > 0 }"
            @click="triageOpen = true"
          >
            <span v-if="triageCount > 0" class="wb-pulse-dot" />
            <IconifyIcon icon="lucide:inbox" class="size-3.5" />
            <span>待甄别来信</span>
            <span class="wb-triage-count">{{ triageCount }}</span>
          </button>

          <button
            type="button"
            class="wb-primary-btn"
            @click="openCreateCustomerModal"
          >
            <IconifyIcon icon="lucide:plus" class="size-4" />
            <span>新建客户</span>
          </button>

          <Dropdown trigger="click" placement="bottomRight">
            <button
              type="button"
              class="wb-icon-pill"
              title="导入与导出"
            >
              <IconifyIcon icon="lucide:ellipsis" class="size-4" />
            </button>
            <template #overlay>
              <Menu>
                <MenuItem key="import" @click="importOpen = true">
                  <span class="inline-flex items-center gap-2">
                    <IconifyIcon icon="lucide:upload" class="size-3.5" />
                    Excel 批量导入客户
                  </span>
                </MenuItem>
                <MenuItem key="export" @click="handleExportCustomers">
                  <span class="inline-flex items-center gap-2">
                    <IconifyIcon icon="lucide:download" class="size-3.5" />
                    导出当前筛选客户
                  </span>
                </MenuItem>
                <MenuItem key="tpl" @click="importCustomerTemplate">
                  <span class="inline-flex items-center gap-2">
                    <IconifyIcon icon="lucide:file-spreadsheet" class="size-3.5" />
                    下载标准导入模板
                  </span>
                </MenuItem>
              </Menu>
            </template>
          </Dropdown>
        </template>

        <template v-else-if="mainTab === 'order'">
          <button
            type="button"
            class="wb-primary-btn"
            @click="openCreateOrderModal(null)"
          >
            <IconifyIcon icon="lucide:plus" class="size-4" />
            <span>登记成交订单 / PI</span>
          </button>
        </template>

        <Tooltip title="配置新询盘自动轮流分派、彩色标签与公海回收规则">
          <button
            type="button"
            class="wb-ghost-btn"
            @click="settingsOpen = true"
          >
            <IconifyIcon icon="lucide:git-fork" class="size-3.5" />
            <span>分流与规则</span>
          </button>
        </Tooltip>
      </div>
    </header>

    <!-- ==================== 2A. 客户管道主工作台 ==================== -->
    <template v-if="mainTab === 'pipeline'">
      <!-- 6 步外贸销售漏斗条 + 暂缓归档胶囊 -->
      <section class="wb-funnel">
        <button
          type="button"
          class="wb-stage-pill"
          :class="{ on: activeStage === null }"
          @click="activeStage = null"
        >
          <span class="wb-stage-bar" style="background: #0071e3" />
          <div class="flex items-center justify-between gap-2">
            <span class="wb-stage-name">全部活跃</span>
            <span class="wb-stage-num">{{ activePool.length }}</span>
          </div>
          <div class="wb-stage-usd">
            {{ totalActiveUsd > 0 ? formatDealMoney(totalActiveUsd, 'USD') : '全流程总览' }}
          </div>
        </button>

        <button
          v-for="(st, idx) in stageFunnelStats"
          :key="st.value"
          type="button"
          class="wb-stage-pill"
          :class="{ on: activeStage === st.value }"
          :style="
            activeStage === st.value
              ? { borderColor: st.color, background: st.bg }
              : undefined
          "
          @click="activeStage = activeStage === st.value ? null : st.value"
        >
          <span class="wb-stage-bar" :style="{ background: st.color }" />
          <div class="flex items-center justify-between gap-1.5">
            <span class="wb-stage-name">{{ idx + 1 }}. {{ st.label }}</span>
            <span class="wb-stage-num">{{ st.count }}</span>
          </div>
          <div
            class="wb-stage-usd"
            :class="{ 'font-semibold text-[#1D1D1F]': st.usdSum > 0 }"
          >
            {{ st.usdSum > 0 ? formatDealMoney(st.usdSum, 'USD') : st.defaultAction }}
          </div>
        </button>

        <button
          type="button"
          class="wb-stage-pill wb-stage-pill--archived"
          :class="{ on: activeStage === 'archived' }"
          @click="
            () => {
              activeStage = activeStage === 'archived' ? null : 'archived';
              if (activeStage === 'archived') setViewMode('table');
            }
          "
        >
          <span class="wb-stage-bar" style="background: #8e8e93" />
          <div class="flex items-center justify-between gap-1.5">
            <span class="wb-stage-name">⏸ 暂缓/流失</span>
            <span class="wb-stage-num">{{ archivedPool.length }}</span>
          </div>
          <div class="wb-stage-usd">沉淀唤醒池</div>
        </button>
      </section>

      <!-- 筛选栏（含管理员按负责人筛选） + 看板/表格双视图切换 -->
      <section class="wb-filter-bar">
        <div class="wb-scope-seg">
          <button
            v-for="sc in [
              { k: 'mine', l: '我负责的' },
              { k: 'sub', l: '下属团队' },
              { k: 'all', l: '全部可见' },
            ]"
            :key="sc.k"
            type="button"
            class="wb-scope-btn"
            :class="{ on: sceneScope === sc.k }"
            @click="sceneScope = sc.k as any"
          >
            {{ sc.l }}
          </button>
        </div>

        <div class="flex flex-wrap items-center gap-1.5">
          <button
            type="button"
            class="wb-chip"
            :class="{ 'wb-chip--red': quickChip === 'urgent' }"
            @click="quickChip = quickChip === 'urgent' ? 'all' : 'urgent'"
          >
            <span>🔴 今日待办与首响</span>
            <b v-if="chipCounts.urgent">{{ chipCounts.urgent }}</b>
          </button>

          <button
            type="button"
            class="wb-chip"
            :class="{ 'wb-chip--amber': quickChip === 'focus' }"
            @click="quickChip = quickChip === 'focus' ? 'all' : 'focus'"
          >
            <span>⭐ 星标重点</span>
            <b v-if="chipCounts.focus">{{ chipCounts.focus }}</b>
          </button>

          <button
            type="button"
            class="wb-chip"
            :class="{ 'wb-chip--amber': quickChip === 'stagnant' }"
            @click="quickChip = quickChip === 'stagnant' ? 'all' : 'stagnant'"
          >
            <span>⏳ 停滞需追单</span>
            <b v-if="chipCounts.stagnant">{{ chipCounts.stagnant }}</b>
          </button>

          <button
            v-if="chipCounts.pool_soon > 0"
            type="button"
            class="wb-chip"
            :class="{ 'wb-chip--amber': quickChip === 'pool_soon' }"
            @click="quickChip = quickChip === 'pool_soon' ? 'all' : 'pool_soon'"
          >
            <span>⚠️ 快掉公海</span>
            <b>{{ chipCounts.pool_soon }}</b>
          </button>

          <button
            v-if="hasActiveFilter"
            type="button"
            class="cursor-pointer border-none bg-transparent px-1.5 text-[12px] font-medium text-[#0071E3] hover:underline"
            @click="resetFilters"
          >
            清除筛选
          </button>
        </div>

        <div class="ml-auto flex flex-wrap items-center gap-2">
          <Input
            v-model:value="keyword"
            allow-clear
            placeholder="搜索客户、公司、产品、国家…"
            class="w-52"
          >
            <template #prefix>
              <IconifyIcon icon="lucide:search" class="size-3.5 text-[#86868B]" />
            </template>
          </Input>

          <!-- 按负责人筛选（管理员巡检或筛出自己名下待派发询盘） -->
          <Select
            v-if="simpleUsers.length > 1"
            :value="filterOwnerId"
            allow-clear
            show-search
            option-filter-prop="label"
            placeholder="按负责人"
            :options="simpleUsers.map((u) => ({ label: u.nickname, value: u.id }))"
            class="w-32"
            @change="(v: any) => onSelectOwnerFilter(v ? Number(v) : undefined)"
          />

          <Select
            v-if="tags.length"
            v-model:value="filterTagId"
            allow-clear
            placeholder="按标签"
            :options="tags.map((t) => ({ label: t.name, value: t.id }))"
            class="w-28"
          />

          <div class="wb-scope-seg">
            <button
              type="button"
              class="wb-scope-btn"
              :class="{ on: viewMode === 'kanban' && activeStage !== 'archived' }"
              :disabled="activeStage === 'archived'"
              @click="setViewMode('kanban')"
            >
              <IconifyIcon icon="lucide:columns-3" class="size-3.5" />
              <span>看板</span>
            </button>
            <button
              type="button"
              class="wb-scope-btn"
              :class="{ on: viewMode === 'table' || activeStage === 'archived' }"
              @click="setViewMode('table')"
            >
              <IconifyIcon icon="lucide:table-2" class="size-3.5" />
              <span>表格</span>
            </button>
          </div>
        </div>
      </section>

      <!-- 新手零客户引导态 -->
      <div
        v-if="!loading && allCustomers.length === 0 && !keyword"
        class="wb-onboarding"
      >
        <div class="wb-ob-head">
          <span class="wb-ob-icon">
            <IconifyIcon icon="lucide:sparkles" class="size-5" />
          </span>
          <div>
            <h3 class="m-0 text-[16px] font-bold text-[#1D1D1F]">
              欢迎使用全流程外贸客户管道
            </h3>
            <p class="m-0 text-[12.5px] text-[#6E6E73]">
              渠道询盘自动识别分流 → 六步可视化看板推进 → 滑出式全景抽屉跟进 → 极简 PI 订单沉淀
            </p>
          </div>
        </div>
        <div class="mt-4 grid grid-cols-3 gap-3">
          <div class="wb-ob-step">
            <b>① 接入渠道与自动分流</b>
            <p>绑定邮箱或 WhatsApp，并可在右上角「分流与规则」按国家配置自动轮流派单给业务员。</p>
            <button
              type="button"
              class="wb-ghost-btn mt-auto self-start"
              @click="router.push('/v3/channel')"
            >
              去绑定渠道 ›
            </button>
          </div>
          <div class="wb-ob-step">
            <b>② 甄别存疑或导入老客</b>
            <p>历史未转化线索可在右上角「待甄别来信」一键立项并直接指派成员，或 Excel 导入名片。</p>
            <div class="mt-auto flex gap-2">
              <button
                v-if="triageCount > 0"
                type="button"
                class="wb-primary-btn"
                @click="triageOpen = true"
              >
                处理待甄别 ({{ triageCount }})
              </button>
              <button
                type="button"
                class="wb-ghost-btn"
                @click="importOpen = true"
              >
                Excel 导入
              </button>
            </div>
          </div>
          <div class="wb-ob-step">
            <b>③ 手动新建并指派买家</b>
            <p>填写买家称呼、国家与 WhatsApp/邮箱，支持直接指派给任意团队业务员跟进。</p>
            <button
              type="button"
              class="wb-primary-btn mt-auto self-start"
              @click="openCreateCustomerModal"
            >
              + 新建第一位客户
            </button>
          </div>
        </div>
      </div>

      <!-- ========== 视图 1：6 列拖拽看板视图 ========== -->
      <Spin
        v-else-if="viewMode === 'kanban' && activeStage !== 'archived'"
        :spinning="loading"
        wrapper-class-name="wb-kanban-spin"
      >
        <div class="wb-kanban">
          <div
            v-for="col in kanbanColumns"
            :key="col.value"
            class="wb-col"
            :class="{
              'wb-col--drop': dragOverStage === col.value,
              'wb-col--dim':
                typeof activeStage === 'number' && activeStage !== col.value,
            }"
            @dragover.prevent="dragOverStage = col.value"
            @dragleave="
              () => {
                if (dragOverStage === col.value) dragOverStage = null;
              }
            "
            @drop.prevent="onColumnDrop(col.value)"
          >
            <div class="wb-col-head">
              <div class="flex items-center gap-1.5">
                <span class="wb-col-dot" :style="{ background: col.color }" />
                <span class="text-[13px] font-bold text-[#1D1D1F]">
                  {{ col.label }}
                </span>
                <span class="wb-col-count">{{ col.cards.length }}</span>
              </div>
              <span
                v-if="col.usd > 0"
                class="text-[11.5px] font-bold tabular-nums text-[#3A3A3C]"
              >
                {{ formatDealMoney(col.usd, 'USD') }}
              </span>
            </div>

            <div class="wb-col-body">
              <div v-if="col.cards.length === 0" class="wb-col-empty">
                拖拽卡片至此推进阶段
              </div>

              <article
                v-for="card in col.cards"
                :key="card.id"
                draggable="true"
                class="wb-card"
                :class="{
                  'wb-card--dragging': draggingId === card.id,
                  'wb-card--urgent': card._health.nextTone === 'red',
                }"
                @dragstart="(e) => onCardDragStart(e, card)"
                @dragend="onCardDragEnd"
                @click="inspectCustomer(card.id)"
              >
                <!-- 第一行：头像 + 客户名 + 锁标 + 星标 -->
                <div class="flex items-start justify-between gap-2">
                  <div class="flex min-w-0 items-center gap-2">
                    <span
                      class="wb-card-avatar"
                      :style="{ background: crmAvatarBg(card.name || card.id) }"
                    >
                      {{ crmInitial(card.name) }}
                    </span>
                    <div class="min-w-0">
                      <div class="flex items-center gap-1">
                        <span class="truncate text-[13.5px] font-bold text-[#1D1D1F]">
                          {{ card.name }}
                        </span>
                        <IconifyIcon
                          v-if="card.lockStatus"
                          icon="lucide:lock"
                          class="size-3 shrink-0 text-[#0071E3]"
                          title="已开启公海保护锁"
                        />
                      </div>
                      <div class="truncate text-[11.5px] text-[#6E6E73]">
                        {{ card.companyName || card.email || card.whatsapp || '待补充公司信息' }}
                      </div>
                    </div>
                  </div>
                  <button
                    type="button"
                    class="wb-star"
                    :class="{ on: card.starred }"
                    @click.stop="onInlineStar(card)"
                  >
                    <IconifyIcon icon="lucide:star" class="size-3.5" />
                  </button>
                </div>

                <!-- 第二行：国旗 + 当地日夜时钟 + 负责人归属角标（团队视角或多人协作一目了然） -->
                <div class="mt-2 flex flex-wrap items-center gap-1.5 text-[11px]">
                  <span
                    v-if="card.countryCode"
                    class="wb-mini-tag"
                    :class="{ 'wb-mini-tag--awake': card._clock?.period === 'day' }"
                  >
                    {{ countryFlag(card.countryCode) }}
                    {{ countryName(card.countryCode) }}
                    <template v-if="card._clock">
                      · {{ card._clock.icon }} {{ card._clock.time }}
                    </template>
                  </span>

                  <span
                    v-if="card.ownerUserName && (sceneScope !== 'mine' || filterOwnerId)"
                    class="wb-mini-tag wb-mini-tag--owner"
                  >
                    <IconifyIcon icon="lucide:user" class="size-2.5" />
                    {{ card.ownerUserName }}
                  </span>

                  <span
                    v-if="researchMap[card.id]?.status === 'done'"
                    class="wb-mini-tag wb-mini-tag--blue"
                  >
                    ✦ 已背调
                  </span>
                  <span
                    v-if="card._peerHint"
                    class="wb-mini-tag wb-mini-tag--warn"
                    :title="card._peerHint"
                  >
                    ⚠️ 同企防撞
                  </span>
                </div>

                <!-- 第三行：商务黑板单值与产品摘要 -->
                <div
                  v-if="card._board.amount || card._board.productQty || card._board.quoteTerms"
                  class="wb-card-deal"
                >
                  <span
                    v-if="card._board.amount"
                    class="shrink-0 font-bold tabular-nums text-[#0071E3]"
                  >
                    {{ formatDealMoney(card._board.amount, card._board.currency) }}
                  </span>
                  <span class="truncate text-[#3A3A3C]">
                    {{ card._board.productQty || card._board.quoteTerms }}
                  </span>
                </div>

                <!-- 彩色业务标签 -->
                <div
                  v-if="Array.isArray(card.tagIds) && card.tagIds.length"
                  class="mt-1.5 flex flex-wrap gap-1"
                >
                  <span
                    v-for="tid in card.tagIds.slice(0, 3)"
                    :key="tid"
                    class="rounded-full px-2 py-0.5 text-[10px] font-semibold"
                    :style="{
                      color: tagColorById(tid),
                      background: `${tagColorById(tid)}15`,
                    }"
                  >
                    {{ tagNameById(tid) || `#${tid}` }}
                  </span>
                </div>

                <!-- 底部：健康度提醒 + 快捷操作组（速记跟进 / 一键派单 / 记订单） -->
                <div class="mt-2.5 flex items-center justify-between gap-1 border-t border-[#F2F2F7] pt-2">
                  <span
                    class="wb-health-tag"
                    :class="`wb-health-tag--${card._health.nextTone}`"
                  >
                    {{ card._health.nextLabel }}
                  </span>

                  <div class="wb-card-actions" @click.stop>
                    <!-- 一键指派 / 转交浮窗 -->
                    <Tooltip v-if="simpleUsers.length > 1" title="派单 / 转交成员">
                      <Popover
                        :open="cardAssignPopId === card.id"
                        trigger="click"
                        placement="bottomRight"
                        @open-change="(v) => (cardAssignPopId = v ? card.id : null)"
                      >
                        <template #content>
                          <div class="w-44 p-1" @click.stop>
                            <div class="mb-1 px-1.5 text-[11.5px] font-bold text-[#1D1D1F]">
                              指派给团队成员：
                            </div>
                            <div class="flex max-h-52 flex-col gap-0.5 overflow-y-auto">
                              <button
                                v-for="u in simpleUsers"
                                :key="u.id"
                                type="button"
                                class="flex cursor-pointer items-center justify-between rounded-lg border-none px-2.5 py-1.5 text-left text-[12px]"
                                :class="
                                  card.ownerUserId === u.id
                                    ? 'bg-[#E8F2FF] font-semibold text-[#0071E3]'
                                    : 'bg-transparent text-[#1D1D1F] hover:bg-[#F2F2F7]'
                                "
                                @click="handleCardQuickAssign(card, u.id)"
                              >
                                <span>{{ u.nickname }}</span>
                                <span v-if="card.ownerUserId === u.id" class="text-[10px]">
                                  当前
                                </span>
                              </button>
                            </div>
                          </div>
                        </template>
                        <button type="button" class="wb-card-act">
                          <IconifyIcon icon="lucide:user-check" class="size-3.5" />
                        </button>
                      </Popover>
                    </Tooltip>

                    <Tooltip title="速记跟进">
                      <Popover
                        :open="quickFollowRowId === card.id"
                        trigger="click"
                        placement="bottomRight"
                        @open-change="
                          (v) => {
                            quickFollowRowId = v ? card.id : null;
                            if (v) quickFollowText = '';
                          }
                        "
                      >
                        <template #content>
                          <div class="w-64 p-1" @click.stop>
                            <div class="mb-1.5 text-[12px] font-bold text-[#1D1D1F]">
                              5 秒速记跟进 · {{ card.name }}
                            </div>
                            <Input.TextArea
                              v-model:value="quickFollowText"
                              :rows="2"
                              placeholder="记一句沟通进展…"
                            />
                            <div class="mt-2 flex items-center justify-between">
                              <Select
                                v-model:value="quickFollowDays"
                                size="small"
                                class="w-28"
                                :options="[
                                  { label: '明天再跟', value: 1 },
                                  { label: '3 天后再跟', value: 3 },
                                  { label: '7 天后再跟', value: 7 },
                                  { label: '14 天后再跟', value: 14 },
                                ]"
                              />
                              <button
                                type="button"
                                class="wb-primary-btn !h-7 !px-2.5 !text-[11.5px]"
                                :disabled="quickFollowSubmitting"
                                @click="submitInlineFollow(card)"
                              >
                                保存
                              </button>
                            </div>
                          </div>
                        </template>
                        <button type="button" class="wb-card-act">
                          <IconifyIcon icon="lucide:notebook-pen" class="size-3.5" />
                        </button>
                      </Popover>
                    </Tooltip>

                    <Tooltip title="登记成交订单">
                      <button
                        type="button"
                        class="wb-card-act"
                        @click="openCreateOrderModal(card)"
                      >
                        <IconifyIcon icon="lucide:receipt" class="size-3.5" />
                      </button>
                    </Tooltip>
                  </div>
                </div>
              </article>
            </div>
          </div>
        </div>
      </Spin>

      <!-- ========== 视图 2：高密度可交互表格视图 ========== -->
      <div v-else class="wb-table-wrap">
        <div v-if="selectedIds.length > 0" class="wb-batch-bar">
          <span class="text-[12.5px] font-semibold text-[#1D1D1F]">
            已勾选 <b class="text-[#0071E3]">{{ selectedIds.length }}</b> 位客户
          </span>
          <button type="button" class="wb-ghost-btn !h-8" @click="openBatchTransfer">
            <IconifyIcon icon="lucide:arrow-right-left" class="size-3.5" />
            <span>批量转交成员</span>
          </button>
          <button type="button" class="wb-ghost-btn !h-8" @click="handleBatchResearch">
            <IconifyIcon icon="lucide:radar" class="size-3.5" />
            <span>批量 AI 背调</span>
          </button>
          <button type="button" class="wb-ghost-btn !h-8" @click="handleBatchPutPool">
            <IconifyIcon icon="lucide:globe" class="size-3.5" />
            <span>放回公海</span>
          </button>
          <button
            type="button"
            class="ml-auto cursor-pointer border-none bg-transparent text-[12px] text-[#6E6E73] hover:text-[#1D1D1F]"
            @click="selectedIds = []"
          >
            取消选择
          </button>
        </div>

        <Spin :spinning="loading">
          <Table
            :columns="tableColumns"
            :data-source="filteredCustomers"
            :row-key="(r: any) => r.id"
            :row-selection="{
              selectedRowKeys: selectedIds,
              onChange: (keys: any[]) => {
                selectedIds = keys.map(Number);
              },
            }"
            :pagination="{
              current: tablePage,
              pageSize: 20,
              total: filteredCustomers.length,
              showSizeChanger: false,
              onChange: (p: number) => {
                tablePage = p;
              },
            }"
            :custom-row="
              (record: any) => ({
                onClick: () => inspectCustomer(record.id),
                style: 'cursor: pointer',
              })
            "
            size="middle"
          >
            <template #emptyText>
              <Empty
                :image="Empty.PRESENTED_IMAGE_SIMPLE"
                description="没有符合当前筛选条件的客户"
              />
            </template>

            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'buyer'">
                <div class="flex items-center gap-2.5">
                  <button
                    type="button"
                    class="wb-star"
                    :class="{ on: record.starred }"
                    @click.stop="onInlineStar(record)"
                  >
                    <IconifyIcon icon="lucide:star" class="size-3.5" />
                  </button>
                  <span
                    class="wb-card-avatar"
                    :style="{ background: crmAvatarBg(record.name || record.id) }"
                  >
                    {{ crmInitial(record.name) }}
                  </span>
                  <div class="min-w-0">
                    <div class="flex items-center gap-1.5">
                      <span class="truncate font-semibold text-[#1D1D1F]">
                        {{ record.name }}
                      </span>
                      <IconifyIcon
                        v-if="record.lockStatus"
                        icon="lucide:lock"
                        class="size-3 shrink-0 text-[#0071E3]"
                      />
                      <span
                        v-if="record._peerHint"
                        class="rounded bg-[#FFF2ED] px-1.5 py-0.5 text-[10px] font-semibold text-[#C93400]"
                        :title="record._peerHint"
                      >
                        同企防撞
                      </span>
                    </div>
                    <div class="truncate text-[11.5px] text-[#6E6E73]">
                      {{ record.companyName || record.email || record.whatsapp || '—' }}
                    </div>
                  </div>
                </div>
              </template>

              <template v-else-if="column.key === 'country'">
                <div v-if="record.countryCode" class="text-[12.5px] text-[#3A3A3C]">
                  <div>
                    {{ countryFlag(record.countryCode) }}
                    {{ countryName(record.countryCode) }}
                  </div>
                  <div
                    v-if="record._clock"
                    class="text-[11px]"
                    :class="
                      record._clock.period === 'day'
                        ? 'font-medium text-[#1E7E34]'
                        : 'text-[#86868B]'
                    "
                  >
                    {{ record._clock.icon }} 当地 {{ record._clock.time }}
                  </div>
                </div>
                <span v-else class="text-[#C7C7CC]">—</span>
              </template>

              <template v-else-if="column.key === 'stage'">
                <div @click.stop>
                  <Select
                    size="small"
                    class="w-32"
                    :value="record._stage"
                    :options="
                      PIPELINE_STAGES.map((s) => ({ label: s.label, value: s.value }))
                    "
                    @change="(v: any) => applyStageChange(record, Number(v), true)"
                  />
                </div>
              </template>

              <template v-else-if="column.key === 'board'">
                <div class="text-[12px]">
                  <span
                    v-if="record._board.amount"
                    class="mr-1.5 font-bold tabular-nums text-[#0071E3]"
                  >
                    {{ formatDealMoney(record._board.amount, record._board.currency) }}
                  </span>
                  <span class="text-[#3A3A3C]">
                    {{
                      record._board.productQty ||
                      record._board.quoteTerms ||
                      record.contactLastContent ||
                      '—'
                    }}
                  </span>
                </div>
              </template>

              <template v-else-if="column.key === 'research'">
                <span
                  v-if="researchMap[record.id]?.status === 'done'"
                  class="line-clamp-2 text-[11.5px] text-[#3A3A3C]"
                  :title="researchMap[record.id]?.summary"
                >
                  ✦ {{ researchMap[record.id]?.summary || '已完成背调' }}
                </span>
                <span
                  v-else-if="researchMap[record.id]?.status === 'running'"
                  class="text-[11.5px] text-[#0071E3]"
                >
                  背调分析中…
                </span>
                <span v-else class="text-[11.5px] text-[#C7C7CC]">未背调</span>
              </template>

              <template v-else-if="column.key === 'next'">
                <span
                  class="wb-health-tag"
                  :class="`wb-health-tag--${record._health.nextTone}`"
                >
                  {{ record._health.nextLabel }}
                </span>
              </template>

              <template v-else-if="column.key === 'owner'">
                <span class="text-[12px] font-medium text-[#3A3A3C]">
                  {{ record.ownerUserName || '—' }}
                </span>
              </template>

              <template v-else-if="column.key === 'quick'">
                <button
                  type="button"
                  class="wb-ghost-btn !h-7 !px-2.5 !text-[11.5px]"
                  @click.stop="inspectCustomer(record.id)"
                >
                  全景 ›
                </button>
              </template>
            </template>
          </Table>
        </Spin>
      </div>
    </template>

    <!-- ==================== 2B. 公海淘金与防撞单查重 ==================== -->
    <PoolPanel
      v-else-if="mainTab === 'pool'"
      ref="poolPanelRef"
      @claimed="loadPipeline"
      @inspect="inspectCustomer"
    />

    <!-- ==================== 2C. 成交订单台账 ==================== -->
    <OrdersPanel
      v-else
      ref="ordersPanelRef"
      @create-order="openCreateOrderModal(null)"
      @edit-order="(ord) => openEditOrderModal(ord, null)"
      @inspect-customer="inspectCustomer"
    />

    <!-- ==================== 抽屉与弹窗组 ==================== -->
    <!-- 1. 灵动待甄别来信抽屉（支持立项直接指派成员） -->
    <TriageDrawer
      v-model:open="triageOpen"
      @count-change="(n) => (triageCount = n)"
      @promoted="
        (newId) => {
          loadPipeline();
          if (newId) inspectCustomer(newId);
        }
      "
    />

    <!-- 2. 右侧滑出式 360° 客户全景抽屉 -->
    <CustomerDrawer
      ref="drawerRef"
      v-model:open="drawerOpen"
      :customer-id="activeCustomerId"
      :tags="tags"
      :has-prev="activeIndexInList > 0"
      :has-next="
        activeIndexInList >= 0 &&
        activeIndexInList < filteredCustomers.length - 1
      "
      @prev="stepDrawer(-1)"
      @next="stepDrawer(1)"
      @changed="loadPipeline"
      @open-order-modal="openCreateOrderModal"
      @edit-order-modal="openEditOrderModal"
    />

    <!-- 3. 🔀 分流、标签与公海规则抽屉 -->
    <Drawer
      v-model:open="settingsOpen"
      :width="660"
      placement="right"
      :closable="false"
    >
      <div class="wb-set-drawer">
        <header class="wb-set-head">
          <div class="flex items-center gap-2.5">
            <span class="wb-set-orb">
              <IconifyIcon icon="lucide:git-fork" class="size-4" />
            </span>
            <div>
              <h3 class="m-0 text-[16px] font-bold text-[#1D1D1F]">
                询盘分流、标签与公海规则
              </h3>
              <p class="m-0 text-[12px] text-[#86868B]">
                统一管理新询盘自动轮流派单、彩色业务标签与公海回收机制
              </p>
            </div>
          </div>
          <button
            type="button"
            class="wb-set-close"
            @click="settingsOpen = false"
          >
            <IconifyIcon icon="lucide:x" class="size-4" />
          </button>
        </header>
        <div class="wb-set-body">
          <SettingsView @tags-changed="loadTaxonomyAndUsers" />
        </div>
      </div>
    </Drawer>

    <!-- 4. 极简外贸订单 / PI 登记与修改弹窗 -->
    <QuickOrderModal
      v-model:open="orderModalOpen"
      :customer="orderModalCustomer"
      :order="orderModalRecord"
      @success="handleOrderSaved"
    />

    <!-- 5. 极简新建外贸客户弹窗（支持指定归属负责人） -->
    <Modal
      v-model:open="createOpen"
      title="新建外贸买家档案"
      :width="540"
      centered
      :confirm-loading="creating"
      ok-text="建档并进入管道"
      cancel-text="取消"
      wrap-class-name="ap-modal-sheet"
      @ok="handleCreateCustomer"
    >
      <div class="grid grid-cols-2 gap-3 pt-2">
        <div>
          <label class="wb-modal-lbl">买家联系人姓名 <span>*</span></label>
          <Input v-model:value="newCustForm.name" placeholder="例如：Michael Miller" />
        </div>
        <div>
          <label class="wb-modal-lbl">海外公司名称</label>
          <Input
            v-model:value="newCustForm.companyName"
            placeholder="例如：Nordic Home GmbH"
          />
        </div>
        <div>
          <label class="wb-modal-lbl">国家 / 地区（自动显示当地时钟）</label>
          <Select
            v-model:value="newCustForm.countryCode"
            show-search
            option-filter-prop="label"
            :options="COUNTRY_OPTIONS"
            class="w-full"
          />
        </div>
        <div>
          <label class="wb-modal-lbl">归属跟进负责人</label>
          <Select
            v-model:value="newCustForm.ownerUserId"
            show-search
            option-filter-prop="label"
            placeholder="默认归属自己"
            :options="simpleUsers.map((u) => ({ label: u.nickname, value: u.id }))"
            class="w-full"
          />
        </div>
        <div>
          <label class="wb-modal-lbl">WhatsApp / 国际号码</label>
          <Input v-model:value="newCustForm.whatsapp" placeholder="+49 151 23456789" />
        </div>
        <div>
          <label class="wb-modal-lbl">买家企业邮箱</label>
          <Input v-model:value="newCustForm.email" placeholder="purchasing@company.com" />
        </div>
        <div>
          <label class="wb-modal-lbl">预估单值（选填）</label>
          <div class="flex gap-1.5">
            <Select
              v-model:value="newCustForm.currency"
              class="w-24"
              :options="CURRENCIES.map((c) => ({ label: c.code, value: c.code }))"
            />
            <InputNumber
              v-model:value="newCustForm.amount"
              :min="0"
              placeholder="如 15000"
              class="flex-1"
            />
          </div>
        </div>
        <div>
          <label class="wb-modal-lbl">意向产品与采购量（选填）</label>
          <Input
            v-model:value="newCustForm.productQty"
            placeholder="如：便携咖啡机 · 1000 pcs"
          />
        </div>
        <div>
          <label class="wb-modal-lbl">初始销售阶段</label>
          <Select
            v-model:value="newCustForm.stage"
            :options="PIPELINE_STAGES.map((s) => ({ label: s.label, value: s.value }))"
            class="w-full"
          />
        </div>
        <div>
          <label class="wb-modal-lbl">询盘来源渠道</label>
          <Select
            v-model:value="newCustForm.source"
            allow-clear
            placeholder="选择来源"
            :options="sourceOptions"
            class="w-full"
          />
        </div>
      </div>
    </Modal>

    <!-- 6. 批量转交弹窗 -->
    <Modal
      v-model:open="transferOpen"
      title="批量转交负责人"
      :width="420"
      centered
      :confirm-loading="transferring"
      ok-text="确认转交"
      cancel-text="取消"
      wrap-class-name="ap-modal-sheet"
      @ok="confirmBatchTransfer"
    >
      <div class="py-2">
        <p class="mb-2 text-[12.5px] text-[#6E6E73]">
          将选中的 {{ selectedIds.length }} 位客户统一移交给：
        </p>
        <Select
          v-model:value="transferTo"
          show-search
          option-filter-prop="label"
          placeholder="选择新的负责人"
          :options="simpleUsers.map((u) => ({ label: u.nickname, value: u.id }))"
          class="w-full"
        />
      </div>
    </Modal>

    <!-- 7. Excel 导入客户弹窗 -->
    <Modal
      v-model:open="importOpen"
      title="Excel 批量导入客户"
      :width="460"
      centered
      :confirm-loading="importing"
      ok-text="开始导入"
      cancel-text="取消"
      wrap-class-name="ap-modal-sheet"
      @ok="handleDoImport"
    >
      <div class="flex flex-col gap-3 py-2">
        <div class="rounded-xl bg-[#F5F5F7] p-3 text-[12.5px] text-[#48484A]">
          支持标准 `.xlsx` 表格导入（如广交会/海外展名片表、海关数据表）。
          <button
            type="button"
            class="ml-1 cursor-pointer border-none bg-transparent p-0 font-semibold text-[#0071E3] hover:underline"
            @click="importCustomerTemplate"
          >
            下载标准导入模板
          </button>
        </div>
        <Upload
          :file-list="importFileList"
          :before-upload="pickImportFile"
          :max-count="1"
          accept=".xlsx,.xls"
        >
          <button type="button" class="wb-ghost-btn">
            <IconifyIcon icon="lucide:file-spreadsheet" class="size-4" />
            <span>选择 Excel 文件</span>
          </button>
        </Upload>
        <label class="flex items-center gap-2 text-[12.5px] text-[#3A3A3C]">
          <input v-model="importUpdate" type="checkbox" />
          <span>遇到已存在的同名客户时覆盖更新（不勾选则自动跳过防重）</span>
        </label>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.wb-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  padding: 16px 20px 20px;
  overflow: auto;
  background: #f5f5f7;
}

.wb-top {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}

.wb-main-tabs {
  display: inline-flex;
  gap: 3px;
  padding: 3.5px;
  background: rgb(255 255 255 / 82%);
  backdrop-filter: blur(18px);
  border: 0.5px solid rgb(60 60 67 / 10%);
  border-radius: 12px;
  box-shadow: 0 1px 2px rgb(0 0 0 / 4%);
}

.wb-main-tab {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 32px;
  padding: 0 15px;
  font-size: 13px;
  font-weight: 600;
  color: #6e6e73;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 9px;
  transition: all 0.16s ease;
}

.wb-main-tab:hover {
  color: #1d1d1f;
}

.wb-main-tab.on {
  color: #1d1d1f;
  background: #fff;
  box-shadow:
    0 1px 4px rgb(0 0 0 / 8%),
    0 0 0 0.5px rgb(0 0 0 / 4%);
}

.wb-pipeline-sum {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 4px 12px;
  font-size: 12px;
  color: #6e6e73;
  background: rgb(255 255 255 / 80%);
  border: 0.5px solid rgb(60 60 67 / 10%);
  border-radius: 999px;
}

.wb-pipeline-sum strong {
  font-weight: 800;
  color: #0071e3;
  font-variant-numeric: tabular-nums;
}

.wb-triage-pill {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 34px;
  padding: 0 13px;
  font-size: 12.5px;
  font-weight: 600;
  color: #3a3a3c;
  cursor: pointer;
  background: #fff;
  border: 0.5px solid rgb(60 60 67 / 14%);
  border-radius: 999px;
  transition: all 0.16s ease;
}

.wb-triage-pill:hover {
  border-color: rgb(0 113 227 / 32%);
}

.wb-triage-pill.active {
  color: #9a4400;
  background: #fff7eb;
  border-color: rgb(255 149 0 / 40%);
}

.wb-pulse-dot {
  width: 7px;
  height: 7px;
  background: #ff9500;
  border-radius: 999px;
  box-shadow: 0 0 0 3px rgb(255 149 0 / 22%);
}

.wb-triage-count {
  padding: 0 6px;
  font-size: 11px;
  font-weight: 700;
  background: rgb(0 0 0 / 6%);
  border-radius: 999px;
  font-variant-numeric: tabular-nums;
}

.wb-icon-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: #3a3a3c;
  cursor: pointer;
  background: #fff;
  border: 0.5px solid rgb(60 60 67 / 14%);
  border-radius: 10px;
  transition: all 0.15s ease;
}

.wb-icon-pill:hover {
  background: #f2f2f7;
}

.wb-ghost-btn {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 34px;
  padding: 0 12px;
  font-size: 12.5px;
  font-weight: 600;
  color: #3a3a3c;
  cursor: pointer;
  background: #fff;
  border: 0.5px solid rgb(60 60 67 / 14%);
  border-radius: 10px;
  transition: all 0.15s ease;
}

.wb-ghost-btn:hover {
  background: #f2f2f7;
}

.wb-primary-btn {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 34px;
  padding: 0 14px;
  font-size: 12.5px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
  background: #0071e3;
  border: none;
  border-radius: 10px;
  box-shadow: 0 2px 6px rgb(0 113 227 / 24%);
  transition: all 0.15s ease;
}

.wb-primary-btn:hover {
  background: #0077ed;
}

/* 6 步漏斗条 */
.wb-funnel {
  display: grid;
  grid-template-columns: repeat(8, minmax(120px, 1fr));
  gap: 8px;
}

@media (max-width: 1360px) {
  .wb-funnel {
    grid-template-columns: repeat(4, minmax(140px, 1fr));
  }
}

.wb-stage-pill {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px 9px;
  overflow: hidden;
  text-align: left;
  cursor: pointer;
  background: rgb(255 255 255 / 90%);
  border: 1px solid rgb(60 60 67 / 8%);
  border-radius: 14px;
  box-shadow: 0 1px 2px rgb(0 0 0 / 3%);
  transition: all 0.16s ease;
}

.wb-stage-bar {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 2.5px;
  opacity: 0.72;
}

.wb-stage-pill.on .wb-stage-bar {
  height: 3px;
  opacity: 1;
}

.wb-stage-pill:hover {
  background: #fff;
  box-shadow: 0 4px 12px rgb(0 0 0 / 6%);
  transform: translateY(-1px);
}

.wb-stage-pill.on {
  background: #eef5ff;
  border-color: #0071e3;
}

.wb-stage-pill--archived.on {
  background: #ebebf0;
  border-color: #6e6e73;
}

.wb-stage-name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12.5px;
  font-weight: 700;
  color: #1d1d1f;
  white-space: nowrap;
}

.wb-stage-num {
  font-size: 13.5px;
  font-weight: 800;
  color: #1d1d1f;
  font-variant-numeric: tabular-nums;
}

.wb-stage-usd {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: #86868b;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

/* 筛选条 */
.wb-filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.wb-scope-seg {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background: rgb(118 118 128 / 11%);
  border-radius: 9px;
}

.wb-scope-btn {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 600;
  color: #6e6e73;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 7px;
  transition: all 0.15s ease;
}

.wb-scope-btn.on {
  color: #1d1d1f;
  background: #fff;
  box-shadow: 0 1px 2px rgb(0 0 0 / 8%);
}

.wb-scope-btn:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.wb-chip {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 30px;
  padding: 0 11px;
  font-size: 12px;
  font-weight: 600;
  color: #48484a;
  cursor: pointer;
  background: #fff;
  border: 0.5px solid rgb(60 60 67 / 12%);
  border-radius: 999px;
  transition: all 0.15s ease;
}

.wb-chip:hover {
  border-color: rgb(0 113 227 / 35%);
}

.wb-chip--red {
  color: #d70015;
  background: #ffeceb;
  border-color: rgb(255 59 48 / 35%);
}

.wb-chip--amber {
  color: #9a4400;
  background: #fff4e0;
  border-color: rgb(255 149 0 / 40%);
}

/* 看板视图 */
.wb-kanban-spin {
  flex: 1;
  min-height: 0;
}

.wb-kanban {
  display: grid;
  grid-template-columns: repeat(6, minmax(244px, 1fr));
  gap: 10px;
  align-items: stretch;
  min-height: 520px;
  padding-bottom: 8px;
  overflow-x: auto;
}

.wb-col {
  display: flex;
  flex-direction: column;
  min-height: 500px;
  max-height: calc(100vh - 246px);
  padding: 10px;
  background: rgb(235 235 240 / 62%);
  border: 1px solid rgb(60 60 67 / 7%);
  border-radius: 16px;
  transition: all 0.16s ease;
}

.wb-col--drop {
  background: #e8f2ff;
  border-color: #0071e3;
  box-shadow: inset 0 0 0 1px #0071e3;
}

.wb-col--dim {
  opacity: 0.42;
}

.wb-col-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 4px 8px;
}

.wb-col-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
}

.wb-col-count {
  padding: 0 6px;
  font-size: 11px;
  font-weight: 700;
  color: #6e6e73;
  background: rgb(255 255 255 / 84%);
  border-radius: 999px;
  font-variant-numeric: tabular-nums;
}

.wb-col-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  padding-right: 2px;
  overflow-y: auto;
}

.wb-col-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88px;
  font-size: 11.5px;
  color: #aeaeb2;
  border: 1px dashed rgb(60 60 67 / 14%);
  border-radius: 12px;
}

.wb-card {
  padding: 11px 12px;
  cursor: pointer;
  background: #fff;
  border: 0.5px solid rgb(60 60 67 / 9%);
  border-radius: 14px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 4%);
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease,
    border-color 0.15s ease;
}

.wb-card:hover {
  border-color: rgb(0 113 227 / 34%);
  box-shadow: 0 6px 18px rgb(0 0 0 / 7%);
  transform: translateY(-1px);
}

.wb-card--urgent {
  border-left: 3px solid #ff3b30;
}

.wb-card--dragging {
  opacity: 0.45;
}

.wb-card-avatar {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  border-radius: 9px;
}

.wb-star {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  color: #d1d1d6;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 6px;
}

.wb-star.on,
.wb-star:hover {
  color: #ff9500;
}

.wb-mini-tag {
  display: inline-flex;
  gap: 3px;
  align-items: center;
  padding: 1px 6px;
  font-size: 10.5px;
  color: #48484a;
  background: #f2f2f7;
  border-radius: 5px;
}

.wb-mini-tag--awake {
  font-weight: 600;
  color: #1e7e34;
  background: #eaf9ee;
}

.wb-mini-tag--owner {
  font-weight: 600;
  color: #5856d6;
  background: #ededfc;
}

.wb-mini-tag--blue {
  font-weight: 600;
  color: #0071e3;
  background: #e8f2ff;
}

.wb-mini-tag--warn {
  font-weight: 600;
  color: #c93400;
  background: #fff2ed;
}

.wb-card-deal {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 5px 8px;
  margin-top: 7px;
  font-size: 11.5px;
  background: #f8f9fb;
  border-radius: 8px;
}

.wb-health-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 7px;
  font-size: 10.5px;
  font-weight: 600;
  border-radius: 6px;
}

.wb-health-tag--red {
  color: #d70015;
  background: #ffeceb;
}

.wb-health-tag--amber {
  color: #9a4400;
  background: #fff4e0;
}

.wb-health-tag--blue {
  color: #0071e3;
  background: #e8f2ff;
}

.wb-health-tag--green {
  color: #1e7e34;
  background: #eaf9ee;
}

.wb-health-tag--gray {
  color: #6e6e73;
  background: #f2f2f7;
}

.wb-card-actions {
  display: flex;
  gap: 3px;
  align-items: center;
  opacity: 0.72;
  transition: opacity 0.15s ease;
}

.wb-card:hover .wb-card-actions {
  opacity: 1;
}

.wb-card-act {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: #6e6e73;
  cursor: pointer;
  background: #f5f5f7;
  border: none;
  border-radius: 6px;
}

.wb-card-act:hover {
  color: #0071e3;
  background: #e8f2ff;
}

/* 表格视图与批量栏 */
.wb-table-wrap {
  overflow: hidden;
  background: #fff;
  border: 0.5px solid rgb(60 60 67 / 10%);
  border-radius: 16px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 4%);
}

.wb-batch-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 16px;
  background: #f0f7ff;
  border-bottom: 0.5px solid rgb(0 113 227 / 18%);
}

/* 设置抽屉 */
.wb-set-drawer {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 0px);
  margin: -24px;
  background: #f5f5f7;
}

.wb-set-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 22px 14px;
  background: rgb(255 255 255 / 90%);
  backdrop-filter: blur(20px);
  border-bottom: 0.5px solid rgb(60 60 67 / 12%);
}

.wb-set-orb {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  color: #fff;
  background: linear-gradient(180deg, #3395ff 0%, #0071e3 100%);
  border-radius: 10px;
}

.wb-set-close {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  color: #6e6e73;
  cursor: pointer;
  background: rgb(118 118 128 / 10%);
  border: none;
  border-radius: 8px;
}

.wb-set-close:hover {
  color: #1d1d1f;
  background: rgb(118 118 128 / 18%);
}

.wb-set-body {
  flex: 1;
  min-height: 0;
  padding: 16px 20px 28px;
  overflow-y: auto;
}

/* 零客户引导卡 */
.wb-onboarding {
  padding: 22px 24px;
  background: #fff;
  border: 0.5px solid rgb(60 60 67 / 10%);
  border-radius: 18px;
}

.wb-ob-head {
  display: flex;
  gap: 12px;
  align-items: center;
}

.wb-ob-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  color: #fff;
  background: linear-gradient(180deg, #3395ff 0%, #0071e3 100%);
  border-radius: 12px;
}

.wb-ob-step {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  background: #f8f8fa;
  border-radius: 14px;
}

.wb-ob-step b {
  font-size: 13.5px;
  color: #1d1d1f;
}

.wb-ob-step p {
  margin: 0 0 8px;
  font-size: 12px;
  line-height: 1.55;
  color: #6e6e73;
}

.wb-modal-lbl {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #3a3a3c;
}

.wb-modal-lbl span {
  color: #ff3b30;
}
</style>
