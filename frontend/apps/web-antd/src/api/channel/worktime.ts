import { requestClient } from '#/api/request';

export namespace ChannelWorkTimeApi {
  export interface Day {
    /** 1=周一 … 7=周日 */
    day: number;
    enabled: boolean;
    /** 09:00-12:00,13:30-18:00 */
    ranges: string;
  }

  export interface Config {
    enabled: boolean;
    timezone: string;
    schedule: Day[];
    holidays: string[];
    extraWorkdays: string[];
    inHoursPolicy: 'ai' | 'draft' | 'human_first';
    takeoverMinutes: number;
    offHoursPolicy: 'ai' | 'draft';
  }

  export interface Status {
    enabled: boolean;
    timezone: string;
    localTime: string;
    inWorkTime: boolean;
    nextChange?: string;
    policy: string;
    waitingCount: number;
  }
}

export const getWorkTime = () => requestClient.get<ChannelWorkTimeApi.Config>('/channel/work-time/get');
export const saveWorkTime = (data: ChannelWorkTimeApi.Config) => requestClient.put('/channel/work-time/save', data);
export const getWorkTimeStatus = () => requestClient.get<ChannelWorkTimeApi.Status>('/channel/work-time/status');
