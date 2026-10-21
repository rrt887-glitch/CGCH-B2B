import { requestClient } from '#/api/request';

export namespace ChannelAssistantApi {
  export interface Step {
    tool: string;
    args: string;
  }

  export interface Reply {
    chatId: number;
    messageId: number;
    content: string;
    steps: Step[];
    credits: number;
  }

  export interface Chat {
    id: number;
    title: string;
    updateTime?: string;
  }

  export interface Message {
    id: number;
    role: 'assistant' | 'user';
    content: string;
    steps: Step[];
    credits?: number;
    createTime?: string;
  }
}

export const askAssistant = (question: string, chatId?: number) =>
  requestClient.post<ChannelAssistantApi.Reply>('/channel/assistant/ask', { chatId, question }, { timeout: 180_000 });
export const getAssistantChats = () => requestClient.get<ChannelAssistantApi.Chat[]>('/channel/assistant/chats');
export const getAssistantMessages = (chatId: number) =>
  requestClient.get<ChannelAssistantApi.Message[]>(`/channel/assistant/messages?chatId=${chatId}`);
export const deleteAssistantChat = (chatId: number) => requestClient.delete(`/channel/assistant/chat/delete?chatId=${chatId}`);
