<script setup lang="ts">
import type { SystemUserProfileApi } from '#/api/system/user/profile';

import { computed, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';
import { formatDateTime } from '@vben/utils';

import { message } from 'ant-design-vue';

import { updateUserProfile } from '#/api/system/user/profile';
import { useUpload } from '#/components/upload/use-upload';

const props = defineProps<{
  profile?: SystemUserProfileApi.UserProfileRespVO;
}>();

const emit = defineEmits<{
  (e: 'success'): void;
}>();

const fileInput = ref<HTMLInputElement>();
const uploading = ref(false);

const avatar = computed(() => props.profile?.avatar || '');
const displayName = computed(
  () => props.profile?.nickname || props.profile?.username || '',
);
const monogram = computed(() =>
  (displayName.value.trim().slice(0, 1) || '用').toUpperCase(),
);

const rows = computed(() => {
  const p = props.profile;
  if (!p) return [];
  return [
    { label: '用户账号', value: p.username || '—' },
    { label: '所属部门', value: p.dept?.name || '—' },
    {
      label: '所属岗位',
      value: p.posts?.length ? p.posts.map((x) => x.name).join('、') : '—',
    },
    { label: '手机号码', value: p.mobile || '—' },
    { label: '用户邮箱', value: p.email || '—' },
    { label: '创建时间', value: p.createTime ? formatDateTime(p.createTime) : '—' },
    { label: '最近登录', value: p.loginDate ? formatDateTime(p.loginDate) : '—' },
  ];
});

function pickFile() {
  fileInput.value?.click();
}

async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  uploading.value = true;
  try {
    const { httpRequest } = useUpload();
    const url = await httpRequest(file);
    await updateUserProfile({ avatar: url });
    message.success('头像已更新');
    emit('success');
  } finally {
    uploading.value = false;
  }
}
</script>

<template>
  <div v-if="profile" class="ap-contact-card">
    <input
      ref="fileInput"
      type="file"
      accept="image/*"
      class="hidden"
      @change="onFileChange"
    />
    <div class="ap-contact-card__hero">
      <button
        type="button"
        class="ap-contact-avatar"
        :disabled="uploading"
        title="点击更换头像"
        @click="pickFile"
      >
        <img v-if="avatar" :src="avatar" :alt="displayName" />
        <span v-else class="ap-contact-avatar__char">{{ monogram }}</span>
        <span class="ap-contact-avatar__mask">
          <IconifyIcon icon="lucide:camera" class="size-4" />
        </span>
      </button>
      <div class="ap-contact-card__name">{{ displayName }}</div>
      <div v-if="profile.roles?.length" class="ap-contact-card__roles">
        <span
          v-for="r in profile.roles"
          :key="r.id"
          class="ap-contact-card__role"
          >{{ r.name }}</span
        >
      </div>
    </div>

    <div class="ap-contact-card__group">
      <div v-for="r in rows" :key="r.label" class="ap-contact-card__row">
        <span class="ap-contact-card__label">{{ r.label }}</span>
        <span class="ap-contact-card__value tabular-nums">{{ r.value }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ap-contact-card__hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-bottom: 20px;
  text-align: center;
}

.ap-contact-avatar {
  position: relative;
  display: grid;
  place-items: center;
  width: 88px;
  height: 88px;
  padding: 0;
  overflow: hidden;
  cursor: pointer;
  background: linear-gradient(180deg, #a4abb7 0%, #868e9c 100%);
  border: 0;
  border-radius: 50%;
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 35%),
    0 6px 18px rgb(0 0 0 / 10%);
  transition: transform var(--ap-dur) var(--ap-ease);
}

.ap-contact-avatar:hover {
  transform: scale(1.03);
}

.ap-contact-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ap-contact-avatar__char {
  font-size: 36px;
  font-weight: 600;
  line-height: 1;
  color: #fff;
  user-select: none;
}

.ap-contact-avatar__mask {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #fff;
  background: rgb(0 0 0 / 38%);
  opacity: 0;
  transition: opacity var(--ap-dur-fast);
}

.ap-contact-avatar:hover .ap-contact-avatar__mask {
  opacity: 1;
}

.ap-contact-card__name {
  margin-top: 12px;
  font-size: 18px;
  font-weight: 700;
  color: var(--ap-label);
}

.ap-contact-card__roles {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: center;
  margin-top: 8px;
}

.ap-contact-card__role {
  padding: 2px 9px;
  font-size: 11.5px;
  font-weight: 500;
  color: var(--ap-accent);
  background: var(--ap-accent-soft);
  border-radius: 999px;
}

.ap-contact-card__group {
  overflow: hidden;
  background: #f5f5f7;
  border-radius: 12px;
}

.ap-contact-card__row {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  min-height: 38px;
  padding: 7px 14px;
  font-size: 12.5px;
}

.ap-contact-card__row + .ap-contact-card__row {
  border-top: 0.5px solid rgb(0 0 0 / 6%);
}

.ap-contact-card__label {
  flex: none;
  color: var(--ap-label-3);
}

.ap-contact-card__value {
  overflow: hidden;
  font-weight: 500;
  color: var(--ap-label);
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
