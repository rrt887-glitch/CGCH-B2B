import type { DescriptionItemSchema } from '#/components/description';

import { h } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { erpPriceInputFormatter, formatDateTime } from '@vben/utils';

import { DictTag } from '#/components/dict-tag';

/** 详情头部的配置 */
export function useDetailSchema(): DescriptionItemSchema[] {
  return [
    {
      field: 'customerName',
      label: '客户名称',
    },
    {
      field: 'totalPrice',
      label: '订单金额（元）',
      render: (val) => erpPriceInputFormatter(val) as string,
    },
    {
      field: 'orderDate',
      label: '下单时间',
      render: (val) => formatDateTime(val) as string,
    },
    {
      field: 'totalReceivablePrice',
      label: '回款金额（元）',
      render: (val) => erpPriceInputFormatter(val) as string,
    },
    {
      field: 'ownerUserName',
      label: '负责人',
    },
  ];
}

/** 详情基本信息的配置 */
export function useDetailBaseSchema(): DescriptionItemSchema[] {
  return [
    {
      field: 'no',
      label: '订单编号',
    },
    {
      field: 'name',
      label: '订单名称',
    },
    {
      field: 'customerName',
      label: '客户名称',
    },
    {
      field: 'businessName',
      label: '商机名称',
    },
    {
      field: 'totalPrice',
      label: '订单金额（元）',
      render: (val) => erpPriceInputFormatter(val) as string,
    },
    {
      field: 'orderDate',
      label: '下单时间',
      render: (val) => formatDateTime(val) as string,
    },
    {
      field: 'startTime',
      label: '订单开始时间',
      render: (val) => formatDateTime(val) as string,
    },
    {
      field: 'endTime',
      label: '订单结束时间',
      render: (val) => formatDateTime(val) as string,
    },
    {
      field: 'signContactName',
      label: '客户签约人',
    },
    {
      field: 'signUserName',
      label: '公司签约人',
    },
    {
      field: 'remark',
      label: '备注',
    },
    {
      field: 'auditStatus',
      label: '订单状态',
      render: (val) =>
        h(DictTag, {
          type: DICT_TYPE.CRM_AUDIT_STATUS,
          value: val,
        }),
    },
  ];
}
