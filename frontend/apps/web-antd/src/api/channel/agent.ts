import { requestClient } from '#/api/request';

/** 智能体（v3）：配技能、转人工、固定开场白、学习真人销售、企业信息、资料文件 */
export namespace AgentApi {
  export interface Grade {
    level: number;
    name: string;
    desc: string;
  }
  export interface Skills {
    autoResearch: boolean;
    quote: boolean;
    followUp: boolean;
    followUpHours: number;
    followUpRounds: number;
    askFields: string[];
    askMore: boolean;
    autoGrade: boolean;
    grades: Grade[];
  }
  export interface Custom {
    text: string;
    type: string;
    enabled: boolean;
  }
  export interface Condition {
    name: string;
    field: 'deal' | 'level' | 'source';
    values: number[];
    enabled: boolean;
  }
  export interface Handoff {
    intentEnabled: boolean;
    presets: Record<string, boolean>;
    custom: Custom[];
    conditions: Condition[];
    takeoverMode: 'none' | 'off' | 'pause';
  }
  export interface Opening {
    enabled: boolean;
    greeting: string;
    fallback: string;
  }
  export interface Learn {
    enabled: boolean;
    days: number;
    goals: string[];
    repliedOnly: boolean;
    lastLearnTime?: string;
    lastSamples?: number;
  }
  export interface Company {
    name: string;
    industry: string;
    website: string;
    intro: string;
  }
  export interface Preset {
    key: string;
    group: string;
    text: string;
    type: string;
  }
  export interface Settings {
    skills: Skills;
    handoff: Handoff;
    opening: Opening;
    learn: Learn;
    company: Company;
    salesStyle: string;
    humanPauseHours: number;
    presets: Preset[];
    askFieldOptions: string[];
  }
  export interface KnowledgeFile {
    id: number;
    name: string;
    url: string;
    size: number;
    fileType: string;
    status: 'done' | 'failed' | 'learning';
    chunks: number;
    errorMsg?: string;
    createTime: string;
  }
}

export const getAgentSettings = () => requestClient.get<AgentApi.Settings>('/channel/ai/agent-settings');

/** 只保存传入的部分 */
export const saveAgentSettings = (data: Partial<Omit<AgentApi.Settings, 'askFieldOptions' | 'presets'>>) =>
  requestClient.put('/channel/ai/agent-settings', data);

export const learnNow = () =>
  requestClient.post<{ lastLearnTime: string; salesStyle: string; samples: number }>('/channel/ai/agent-settings/learn');

export const getKnowledgeFiles = () => requestClient.get<AgentApi.KnowledgeFile[]>('/channel/ai/knowledge-file/list');

export const uploadKnowledgeFile = (file: File) =>
  requestClient.upload<AgentApi.KnowledgeFile>('/channel/ai/knowledge-file/upload', { file }, { timeout: 180_000 });

export const relearnKnowledgeFile = (id: number) =>
  requestClient.post<AgentApi.KnowledgeFile>(`/channel/ai/knowledge-file/relearn?id=${id}`, undefined, { timeout: 180_000 });

export const deleteKnowledgeFile = (id: number) => requestClient.delete(`/channel/ai/knowledge-file/delete?id=${id}`);
