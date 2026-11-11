import type {
  BannerSlide,
  BoardingStore,
  ChatContact,
  FriendPost,
  Product,
  UserProfile,
  VetDoctor
} from '@/buyer/types/models';

export const defaultUser: UserProfile = {
  id: 'user-demo',
  username: '小橘喵家长',
  email: 'petlover@example.com',
  phone: '13812345678',
  intro: '喜欢给毛孩子挑选漂亮、舒服、实用的小物件，也愿意把好物分享给其他家长。',
  avatar: '/images/logo192.png',
  joinedAt: '2024-11-03',
  address: '上海市浦东新区爱宠大道 88 号 18 栋 602',
  preferences: ['猫咪用品', '智能喂养', '寄养服务']
};

export const bannerSlides: BannerSlide[] = [
  {
    id: 'banner-1',
    title: '温暖陪伴，舒适生活',
    subtitle: '给毛孩子一个轻盈、柔软、有安全感的日常空间',
    image: '/images/沙发小猫.jpg',
    accent: '#ff8fb1'
  },
  {
    id: 'banner-2',
    title: '轻松出行，安心看护',
    subtitle: '从寄养到出行装备，一站式准备更从容',
    image: '/images/商品-宠物航空箱1.jpg',
    accent: '#4da6ff'
  },
  {
    id: 'banner-3',
    title: '科学喂养，更懂毛孩子',
    subtitle: '热门口粮、玩具和日常护理用品都已备齐',
    image: '/images/商品-狗粮1.jpg',
    accent: '#ffd166'
  }
];

export const friendPosts: FriendPost[] = [
  {
    id: 'post-1',
    name: '铲屎官小王',
    avatar: '/images/logo192.png',
    message: '新买的猫爬架搭配窗边软垫，主子一下午都窝在上面晒太阳。',
    image: '/images/商品-豪华猫爬架.jpg'
  },
  {
    id: 'post-2',
    name: '狗狗的麻麻',
    avatar: '/images/logo192.png',
    message: '自动喂食机出门时特别省心，回家还能看到它准点开饭。',
    image: '/images/商品-宠物喂食机1.jpg'
  },
  {
    id: 'post-3',
    name: '喵主子守护者',
    avatar: '/images/logo192.png',
    message: '换了新饮水机以后喝水频率明显变高，清洗也比以前方便。',
    image: '/images/商品-宠物饮水机1.jpg'
  },
  {
    id: 'post-4',
    name: '遛狗搭子阿哲',
    avatar: '/images/logo192.png',
    message: '夜间反光牵引绳质感很好，手柄握着也舒服，推荐给大狗家庭。',
    image: '/images/商品-遛狗绳1.jpg'
  }
];

export const products: Product[] = [
  {
    id: 'prod-1',
    name: '自然谷物狗粮',
    category: '犬类主粮',
    subCategory: '主粮',
    price: 128,
    heat: 95,
    purchaseCount: 1320,
    rating: 4.9,
    stock: 88,
    description: '高蛋白配方，颗粒适口，适合成犬日常喂养。',
    image: '/images/商品-狗粮1.jpg',
    tags: ['热销', '高蛋白', '日常口粮'],
    sizes: [
      { label: '规格', value: '2kg', delta: 0 },
      { label: '规格', value: '5kg', delta: 86 }
    ]
  },
  {
    id: 'prod-2',
    name: '鲜肉冻干猫粮',
    category: '猫类主粮',
    subCategory: '主粮',
    price: 109,
    heat: 92,
    purchaseCount: 1168,
    rating: 4.8,
    stock: 63,
    description: '添加冻干鸡肉粒，适口性强，适合挑食猫咪。',
    image: '/images/商品-猫粮1.jpg',
    tags: ['挑食救星', '冻干添加', '猫咪主粮'],
    sizes: [
      { label: '规格', value: '1.5kg', delta: 0 },
      { label: '规格', value: '4kg', delta: 92 }
    ]
  },
  {
    id: 'prod-3',
    name: '云朵猫窝',
    category: '猫咪睡眠',
    subCategory: '窝垫',
    price: 86,
    heat: 88,
    purchaseCount: 864,
    rating: 4.7,
    stock: 41,
    description: '包裹感强，绒面柔软，适合秋冬季节保暖。',
    image: '/images/商品-猫窝1.jpg',
    tags: ['舒适', '保暖', '软萌'],
    specifications: [
      { label: '颜色', value: '奶油白', delta: 0 },
      { label: '颜色', value: '焦糖棕', delta: 8 }
    ]
  },
  {
    id: 'prod-4',
    name: '智能宠物饮水机',
    category: '智能家居',
    subCategory: '喂养设备',
    price: 169,
    heat: 97,
    purchaseCount: 1450,
    rating: 4.9,
    stock: 55,
    description: '循环活水设计，静音过滤，让饮水更安心。',
    image: '/images/商品-宠物饮水机1.jpg',
    tags: ['智能', '静音', '高回购']
  },
  {
    id: 'prod-5',
    name: '双碗防打翻狗盆',
    category: '餐具用品',
    subCategory: '食盆',
    price: 49,
    heat: 76,
    purchaseCount: 592,
    rating: 4.6,
    stock: 120,
    description: '底部稳固防滑，吃饭喝水一体化更整洁。',
    image: '/images/商品-狗盆1.jpg',
    tags: ['防滑', '易清洗', '双碗']
  },
  {
    id: 'prod-6',
    name: '宠物航空箱',
    category: '出行装备',
    subCategory: '外出箱',
    price: 238,
    heat: 83,
    purchaseCount: 486,
    rating: 4.8,
    stock: 27,
    description: '适合短途出游与寄养接送，透气安全，便于提拿。',
    image: '/images/商品-宠物航空箱1.jpg',
    tags: ['出行', '安全', '透气'],
    sizes: [
      { label: '尺寸', value: 'M', delta: 0 },
      { label: '尺寸', value: 'L', delta: 50 }
    ]
  },
  {
    id: 'prod-7',
    name: '云感逗猫棒',
    category: '玩具互动',
    subCategory: '玩具',
    price: 32,
    heat: 81,
    purchaseCount: 902,
    rating: 4.7,
    stock: 148,
    description: '羽毛与铃铛组合，适合高频互动消耗精力。',
    image: '/images/商品-逗猫棒1.jpg',
    tags: ['互动', '轻量', '高性价比']
  },
  {
    id: 'prod-8',
    name: '宠物喂食机',
    category: '智能家居',
    subCategory: '喂养设备',
    price: 268,
    heat: 94,
    purchaseCount: 1208,
    rating: 4.8,
    stock: 33,
    description: '支持定时定量，离家也能保证喂食节奏。',
    image: '/images/商品-宠物喂食机1.jpg',
    tags: ['定时', '远程', '智能喂养']
  }
];

export const doctors: VetDoctor[] = [
  {
    id: 'doc-1',
    name: '林医生',
    title: '执业兽医师',
    specialty: '猫咪消化与应激管理',
    online: true,
    avatar: '/images/logo-小猫.png',
    responseTime: '平均 3 分钟回复',
    summary: '擅长猫咪软便、挑食、换粮适应以及环境应激问题。'
  },
  {
    id: 'doc-2',
    name: '周医生',
    title: '宠物营养顾问',
    specialty: '犬类体重管理',
    online: true,
    avatar: '/images/logo-小狗.png',
    responseTime: '平均 5 分钟回复',
    summary: '可协助制定增重、减重和术后恢复饮食建议。'
  },
  {
    id: 'doc-3',
    name: '夏医生',
    title: '护理咨询师',
    specialty: '皮肤护理与日常洗护',
    online: false,
    avatar: '/images/logo192.png',
    responseTime: '当前离线，留言后稍后答复',
    summary: '关注皮肤屏障、耳道清洁和季节性护理方案。'
  }
];

export const boardingStores: BoardingStore[] = [
  {
    id: 'store-1',
    name: '四季毛孩子寄养屋',
    city: '上海',
    area: '浦东新区',
    rating: 4.9,
    reviews: 318,
    cover: '/images/spring.jpg',
    gallery: ['/images/spring.jpg', '/images/summer.jpg', '/images/autumn.jpg'],
    priceText: '小型犬 / 猫咪 128 元 / 天起',
    services: ['单独房间', '每日遛狗', '视频回访', '专属喂养记录'],
    description: '整体环境明亮安静，适合需要规律作息和细致照看的毛孩子。',
    contact: '021-8888-1024'
  },
  {
    id: 'store-2',
    name: '暖窝宠物度假中心',
    city: '上海',
    area: '徐汇区',
    rating: 4.8,
    reviews: 241,
    cover: '/images/winter.jpg',
    gallery: ['/images/winter.jpg', '/images/背景1.jpg', '/images/背景2.jpg'],
    priceText: '中大型犬 168 元 / 天起',
    services: ['大活动区', '洗护服务', '接送服务', '24 小时巡护'],
    description: '更适合精力旺盛的狗狗，活动场地充足，支持短住和长住。',
    contact: '021-6666-3098'
  },
  {
    id: 'store-3',
    name: '晴日猫咪安住馆',
    city: '杭州',
    area: '滨江区',
    rating: 4.9,
    reviews: 156,
    cover: '/images/背景3.jpg',
    gallery: ['/images/背景3.jpg', '/images/草地小狗.jpg', '/images/沙发小猫.jpg'],
    priceText: '猫咪 118 元 / 天起',
    services: ['独立猫房', '空气净化', '安抚陪玩', '健康观察'],
    description: '偏安静治愈风格，适合对应激敏感的猫咪进行短期寄住。',
    contact: '0571-7777-1965'
  }
];

export const chatContacts: ChatContact[] = [
  {
    id: 'official',
    name: '官方客服',
    avatar: '🏢',
    lastMessage: '欢迎来到爱宠生活馆，有任何问题都可以问我。',
    online: true,
    unread: 1,
    type: 'official'
  },
  {
    id: 'store-1',
    name: '四季毛孩子客服',
    avatar: '🏠',
    lastMessage: '需要我帮你推荐适合猫咪的寄养房型吗？',
    online: true,
    unread: 0,
    type: 'store'
  },
  {
    id: 'store-2',
    name: '暖窝寄养前台',
    avatar: '🛎️',
    lastMessage: '大型犬可以安排单独活动时段。',
    online: false,
    unread: 0,
    type: 'store'
  }
];