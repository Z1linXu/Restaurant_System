import type {
  ItemCustomizationDraft,
  LocalizedText,
  MenuCategory,
  MenuItem,
  OrderLineItem,
  OrderSession,
} from '../types/ordering'

export const menuCategories: MenuCategory[] = [
  { id: 'noodles', labelEn: 'Noodles', labelZh: '面类' },
  { id: 'sides', labelEn: 'Side Dishes', labelZh: '小菜' },
  { id: 'toppings', labelEn: 'Toppings', labelZh: '加料' },
  { id: 'drinks', labelEn: 'Drinks', labelZh: '饮料' },
]

const noodleSizes = [
  { id: 'regular', labelEn: 'Regular', labelZh: '标准份' },
  { id: 'large', labelEn: 'Large', labelZh: '大份', priceDelta: 2 },
]

const noodleTypes = [
  { id: 'erxi', labelEn: 'Erxi', labelZh: '二细' },
  { id: 'sanxi', labelEn: 'Sanxi', labelZh: '三细' },
  { id: 'wide', labelEn: 'Wide', labelZh: '宽面' },
  { id: 'leek', labelEn: 'Leek Leaf', labelZh: '韭叶' },
  { id: 'extra-wide', labelEn: 'Extra Wide', labelZh: '大宽' },
]

const spicyLevels = [
  { id: 'non-spicy', labelEn: 'Non-Spicy', labelZh: '不辣' },
  { id: 'mild', labelEn: 'Mild', labelZh: '少辣' },
  { id: 'regular', labelEn: 'Regular', labelZh: '正常' },
  { id: 'extra', labelEn: 'Extra', labelZh: '多辣' },
]

const comboEggs = [
  { id: 'fried-egg', labelEn: 'Fried Egg', labelZh: '煎蛋' },
  { id: 'tea-egg', labelEn: 'Tea Egg', labelZh: '卤蛋' },
]

const comboSides = [
  { id: 'tofu', labelEn: 'Spicy Sliced Tofu', labelZh: '凉拌干丝' },
  { id: 'cucumber', labelEn: 'Cucumber Salad', labelZh: '凉拌黄瓜' },
  { id: 'potato', labelEn: 'Shredded Potato', labelZh: '土豆丝' },
]

const noodleAddOns = [
  { id: 'extra-egg', labelEn: 'Extra Egg', labelZh: '加蛋', priceDelta: 1.5 },
  { id: 'extra-meat', labelEn: 'Extra Meat', labelZh: '加肉', priceDelta: 3 },
  { id: 'extra-veg', labelEn: 'Extra Veg', labelZh: '加菜', priceDelta: 1 },
  { id: 'extra-noodle', labelEn: 'Extra Noodle', labelZh: '加面', priceDelta: 2 },
]

const noodleRemoveOptions = [
  { id: 'no-onions', labelEn: 'No Onions', labelZh: '不要葱' },
  { id: 'no-cilantro', labelEn: 'No Cilantro', labelZh: '不香菜' },
]

export const menuItems: MenuItem[] = [
  {
    id: 'traditional-beef-noodle',
    categoryId: 'noodles',
    nameEn: 'Traditional Beef Noodle',
    nameZh: '经典牛肉面',
    descriptionEn: 'Clear beef broth, white radish, red chili oil, hand-pulled noodles.',
    descriptionZh: '经典牛肉面，一清二白三红四绿五黄。',
    price: 12.5,
    badge: { en: 'Traditional', zh: '经典' },
    customization: {
      sizes: { required: true, options: noodleSizes },
      noodleTypes,
      spicyLevels,
      combo: { upcharge: 4.5, eggs: comboEggs, sides: comboSides, sideRemoveOptions: [] },
      addOns: noodleAddOns,
      removeOptions: noodleRemoveOptions,
    },
  },
  {
    id: 'braised-beef-noodle',
    categoryId: 'noodles',
    nameEn: 'Braised Beef Noodle',
    nameZh: '红烧牛肉面',
    descriptionEn: 'Slow-cooked tender brisket in dark aromatic broth.',
    descriptionZh: '红烧牛肉面，汤底浓郁，牛肉软烂入味。',
    price: 14.8,
    badge: { en: 'Hearty', zh: '浓香' },
    customization: {
      sizes: { required: true, options: noodleSizes },
      noodleTypes,
      spicyLevels,
      combo: { upcharge: 4.5, eggs: comboEggs, sides: comboSides, sideRemoveOptions: [] },
      addOns: noodleAddOns,
      removeOptions: noodleRemoveOptions,
    },
  },
  {
    id: 'cold-sliced-beef',
    categoryId: 'sides',
    nameEn: 'Cold Sliced Beef',
    nameZh: '酱牛肉',
    descriptionEn: 'Lanzhou style spiced cold sliced beef.',
    descriptionZh: '兰州风味酱牛肉，冷盘即食。',
    price: 18,
  },
  {
    id: 'tea-egg',
    categoryId: 'sides',
    nameEn: 'Tea Egg',
    nameZh: '茶叶蛋',
    descriptionEn: 'Classic marbled savory egg simmered in tea and five-spice broth.',
    descriptionZh: '经典卤制茶叶蛋。',
    price: 1.5,
  },
  {
    id: 'lanzhou-pickled-cabbage',
    categoryId: 'sides',
    nameEn: 'Lanzhou Pickled Cabbage',
    nameZh: '兰州泡菜',
    descriptionEn: 'Crisp, tangy, and slightly spicy fermented cabbage.',
    descriptionZh: '爽脆微辣的兰州泡菜。',
    price: 3,
  },
  {
    id: 'fried-bread',
    categoryId: 'sides',
    nameEn: 'Fried Bread (Youbing)',
    nameZh: '油饼',
    descriptionEn: 'Freshly deep-fried golden crispy dough.',
    descriptionZh: '现炸金黄油饼。',
    price: 2.5,
  },
  {
    id: 'extra-beef',
    categoryId: 'toppings',
    nameEn: 'Extra Beef',
    nameZh: '加牛肉',
    descriptionEn: 'Tender braised beef add-on.',
    descriptionZh: '额外加牛肉。',
    price: 3,
  },
  {
    id: 'sour-plum-drink',
    categoryId: 'drinks',
    nameEn: 'Sour Plum Drink',
    nameZh: '酸梅汤',
    descriptionEn: 'House-brewed chilled sour plum drink.',
    descriptionZh: '自制冰镇酸梅汤。',
    price: 2.5,
  },
]

function localized(en: string, zh: string): LocalizedText {
  return { en, zh }
}

function buildLineItem(
  id: string,
  menuItem: MenuItem,
  draft: ItemCustomizationDraft,
  unitPrice: number,
  lineSubtotal: number,
  summaryTags: LocalizedText[],
): OrderLineItem {
  return {
    id,
    menuItemId: menuItem.id,
    nameEn: menuItem.nameEn,
    nameZh: menuItem.nameZh,
    quantity: draft.quantity,
    unitPrice,
    lineSubtotal,
    selection: draft,
    summaryTags,
    notes: '',
  }
}

export const initialOrderSessions: OrderSession[] = [
  {
    orderId: 'ORD-1002',
    slotLabel: 'T2',
    tableLabel: 'T2',
    status: 'draft',
    isModifiedAfterSubmit: false,
    items: [
      buildLineItem(
        'item-1002-1',
        menuItems[0],
        {
          sizeId: 'regular',
          noodleTypeId: 'sanxi',
          spicyLevelId: 'regular',
          comboEnabled: false,
          comboSideRemoveIds: [],
          addOnQuantities: {},
          removeIds: ['no-cilantro'],
          quantity: 1,
        },
        12.5,
        12.5,
        [localized('Regular', '标准份'), localized('Sanxi', '三细'), localized('No Cilantro', '不香菜')],
      ),
      buildLineItem(
        'item-1002-2',
        menuItems[3],
        {
          comboEnabled: false,
          comboSideRemoveIds: [],
          addOnQuantities: {},
          removeIds: [],
          quantity: 2,
        },
        1.5,
        3,
        [],
      ),
    ],
  },
  {
    orderId: 'ORD-1003',
    slotLabel: 'T3-A',
    tableLabel: 'T3',
    status: 'draft',
    isModifiedAfterSubmit: false,
    items: [
      buildLineItem(
        'item-1003-1',
        menuItems[1],
        {
          sizeId: 'large',
          noodleTypeId: 'erxi',
          spicyLevelId: 'mild',
          comboEnabled: true,
          comboEggId: 'fried-egg',
          comboSideId: 'cucumber',
          comboSideRemoveIds: [],
          addOnQuantities: { 'extra-meat': 1, 'extra-noodle': 2 },
          removeIds: [],
          quantity: 1,
        },
        24.3,
        24.3,
        [
          localized('Large', '大份'),
          localized('Erxi', '二细'),
          localized('Combo', '套餐'),
          localized('Extra Meat', '加肉'),
        ],
      ),
    ],
  },
  {
    orderId: 'ORD-1005',
    slotLabel: 'T4-B',
    tableLabel: 'T4',
    status: 'submitted',
    isModifiedAfterSubmit: true,
    items: [
      buildLineItem(
        'item-1005-1',
        menuItems[2],
        {
          comboEnabled: false,
          comboSideRemoveIds: [],
          addOnQuantities: {},
          removeIds: [],
          quantity: 1,
        },
        18,
        18,
        [],
      ),
    ],
  },
]
