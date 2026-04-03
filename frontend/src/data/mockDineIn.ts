import type { DineInMockData } from '../types/dinein'

export const dineInMockData: DineInMockData = {
  tables: [
    {
      id: 1,
      label: 'T1',
      seats: 4,
      zone: 'Main Hall',
      tableConfig: 'split_supported',
      occupancyMode: 'empty',
    },
    {
      id: 2,
      label: 'T2',
      seats: 4,
      zone: 'Main Hall',
      tableConfig: 'single_only',
      occupancyMode: 'full',
      fullOrder: {
        orderId: 'ORD-1002',
      },
    },
    {
      id: 3,
      label: 'T3',
      seats: 4,
      zone: 'Main Hall',
      tableConfig: 'split_supported',
      occupancyMode: 'split',
      splitOrders: {
        A: {
          orderId: 'ORD-1003',
        },
      },
    },
    {
      id: 4,
      label: 'T4',
      seats: 4,
      zone: 'Main Hall',
      tableConfig: 'split_supported',
      occupancyMode: 'split',
      splitOrders: {
        A: {
          orderId: 'ORD-1004',
        },
        B: {
          orderId: 'ORD-1005',
        },
      },
    },
    {
      id: 5,
      label: 'T5',
      seats: 4,
      zone: 'Patio',
      tableConfig: 'single_only',
      occupancyMode: 'full',
      alertMessage: 'Manager check requested',
      fullOrder: {
        orderId: 'ORD-1006',
      },
    },
    {
      id: 6,
      label: 'T6',
      seats: 4,
      zone: 'Patio',
      tableConfig: 'single_only',
      occupancyMode: 'empty',
    },
    {
      id: 7,
      label: 'T7',
      seats: 4,
      zone: 'Window',
      tableConfig: 'split_supported',
      occupancyMode: 'split',
      splitOrders: {
        B: {
          orderId: 'ORD-1007',
        },
      },
    },
    {
      id: 8,
      label: 'T8',
      seats: 4,
      zone: 'Window',
      tableConfig: 'split_supported',
      occupancyMode: 'full',
      fullOrder: {
        orderId: 'ORD-1008',
      },
    },
  ],
}
