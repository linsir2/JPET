import {
  bannerSlides,
  boardingStores,
  chatContacts,
  defaultUser,
  doctors,
  friendPosts,
  products
} from '@/buyer/mocks/db';
import http from '@/api/http';
import { clearAuthSession, readAuthSession, saveLoginSession } from '@/utils/authSession';
import { resolveAssetUrl } from '@/utils/assets';
import { listBuyerContacts, listThreadMessages, markThreadRead, sendThreadMessage, syncBuyerOrderThreads } from '@/utils/orderChat';
import { loginBuyer, registerBuyer } from '@/api/auth';
import type {
  BannerSlide,
  BoardingStore,
  BuyerOrder,
  CartItem,
  CartItemSelection,
  ChatContact,
  ChatMessage,
  CheckoutDraft,
  FriendPost,
  LoginPayload,
  Product,
  RegisterPayload,
  UserProfile,
  VetDoctor
} from '@/buyer/types/models';

const STORAGE_KEY_PREFIX = 'jpet-store:buyer';

const forceMock = import.meta.env.VITE_USE_MOCK_API === 'true';
const apiBase = import.meta.env.VITE_API_BASE_URL ?? '/api';

const useMockHome = forceMock;
const useMockCatalog = forceMock;
const useMockProfile = forceMock;
const useMockCart = true;
const useMockOrder = forceMock;
const useMockService = true;
const useMockChat = true;

function uid(prefix: string) {
  return `${prefix}-${Math.random().toString(36).slice(2, 10)}`;
}

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

function delay<T>(value: T, ms = 220): Promise<T> {
  return new Promise((resolve) => window.setTimeout(() => resolve(clone(value)), ms));
}

function readStorage<T>(key: string, fallback: T): T {
  const raw = window.localStorage.getItem(key);
  if (!raw) {
    return clone(fallback);
  }
  try {
    return JSON.parse(raw) as T;
  } catch {
    return clone(fallback);
  }
}

function writeStorage<T>(key: string, value: T) {
  window.localStorage.setItem(key, JSON.stringify(value));
}

function getViewerKey() {
  const session = readAuthSession();
  return session.user?.id ? String(session.user.id) : 'guest';
}

function scopedStorageKey(name: string) {
  return `${STORAGE_KEY_PREFIX}:${getViewerKey()}:${name}`;
}

function unwrapResponse<T>(response: any): T {
  return (response?.data?.data ?? response?.data) as T;
}

function sortSelectionEntries(selection: CartItemSelection) {
  return Object.entries(selection || {})
    .filter(([, value]) => Boolean(value))
    .sort(([left], [right]) => left.localeCompare(right, 'zh-Hans-CN'));
}

function serializeSelection(selection: CartItemSelection) {
  return JSON.stringify(Object.fromEntries(sortSelectionEntries(selection)));
}

function normalizeVariant(raw: any) {
  const attributesSource = raw?.attributes ?? raw?.attributeValues ?? raw?.attribute_values ?? {};
  const attributes = Object.fromEntries(
    Object.entries(attributesSource || {}).map(([key, value]) => [key, String(value ?? '').trim()])
  );
  return {
    skuId: raw?.skuId != null ? String(raw.skuId) : raw?.id != null ? String(raw.id) : undefined,
    attributes,
    price: Number(raw?.price ?? 0),
    stock: Number(raw?.stock ?? 0),
    image: resolveAssetUrl(String(raw?.image ?? raw?.imageUrl ?? raw?.image_url ?? '')),
  };
}

function buildOptionGroups(variants: Array<{ attributes: Record<string, string> }>, preferredOrder: string[]) {
  const groups = new Map<string, Set<string>>();
  preferredOrder.forEach((key) => groups.set(key, new Set()));

  variants.forEach((variant) => {
    Object.entries(variant.attributes || {}).forEach(([key, value]) => {
      if (!groups.has(key)) {
        groups.set(key, new Set());
      }
      if (value) {
        groups.get(key)!.add(value);
      }
    });
  });

  return Array.from(groups.entries())
    .map(([name, options]) => ({ name, options: Array.from(options) }))
    .filter((group) => group.options.length);
}

function resolveSelectedPrice(product: Product, selection: CartItemSelection) {
  const variants = Array.isArray(product.variants) ? product.variants : [];
  if (!variants.length) {
    return product.price;
  }
  const normalizedSelection = Object.fromEntries(sortSelectionEntries(selection));
  const matched = variants.find((variant) => {
    const keys = Object.keys(variant.attributes || {});
    return keys.length > 0 && keys.every((key) => variant.attributes[key] === normalizedSelection[key]);
  });
  return matched?.price ?? product.price;
}

function normalizeProduct(raw: any): Product {
  const normalizeText = (value: unknown, fallback = '') => {
    const text = String(value ?? '').trim();
    return text || fallback;
  };
  const tags = Array.isArray(raw?.tags)
    ? raw.tags.map((item: unknown) => String(item ?? '').trim()).filter(Boolean)
    : [];
  const coreAttributes = Array.isArray(raw?.coreAttributes ?? raw?.core_attributes)
    ? (raw?.coreAttributes ?? raw?.core_attributes).map((item: unknown) => String(item))
    : [];
  const variants = Array.isArray(raw?.variants ?? raw?.skus)
    ? (raw?.variants ?? raw?.skus).map(normalizeVariant)
    : [];
  const optionGroups = buildOptionGroups(variants, coreAttributes);
  const sizes = Array.isArray(raw?.sizes)
    ? raw.sizes.map((item: any) => ({
        label: String(item?.label ?? item?.value ?? ''),
        value: String(item?.value ?? item?.label ?? ''),
        delta: Number(item?.delta ?? 0),
      }))
    : [];
  const specifications = Array.isArray(raw?.specifications)
    ? raw.specifications.map((item: any) => ({
        label: String(item?.label ?? item?.value ?? ''),
        value: String(item?.value ?? item?.label ?? ''),
        delta: Number(item?.delta ?? 0),
      }))
    : [];

  return {
    id: String(raw?.id ?? ''),
    name: normalizeText(raw?.name),
    category: normalizeText(raw?.category, tags[0] ?? '全部'),
    subCategory: normalizeText(raw?.subCategory ?? raw?.sub_category ?? raw?.category, tags[1] ?? tags[0] ?? '默认'),
    price: Number(raw?.price ?? 0),
    heat: Number(raw?.heat ?? raw?.purchaseCount ?? raw?.purchase_count ?? 0),
    purchaseCount: Number(raw?.purchaseCount ?? raw?.purchase_count ?? raw?.heat ?? 0),
    rating: Number(raw?.rating ?? 0),
    stock: Number(raw?.stock ?? 0),
    description: normalizeText(raw?.description),
    image: resolveAssetUrl(String(raw?.image ?? raw?.imageUrl ?? raw?.image_url ?? '')),
    tags,
    coreAttributes,
    optionGroups,
    variants,
    sizes,
    specifications,
  };
}

function normalizeBannerSlide(slide: BannerSlide): BannerSlide {
  return {
    ...slide,
    image: resolveAssetUrl(slide.image),
  };
}

function normalizeFriendPost(post: FriendPost): FriendPost {
  return {
    ...post,
    avatar: resolveAssetUrl(post.avatar),
    image: resolveAssetUrl(post.image),
  };
}

function normalizeProfile(profile: UserProfile): UserProfile {
  return {
    ...profile,
    avatar: resolveAssetUrl(profile.avatar),
  };
}

function normalizeCartItem(item: CartItem): CartItem {
  return {
    ...item,
    productImage: resolveAssetUrl(item.productImage),
  };
}

function normalizeBuyerOrder(raw: any): BuyerOrder {
  return {
    id: String(raw?.id ?? ''),
    orderNo: String(raw?.orderNo ?? raw?.order_no ?? ''),
    productId: String(raw?.productId ?? raw?.product_id ?? ''),
    name: String(raw?.name ?? ''),
    image: resolveAssetUrl(String(raw?.image ?? raw?.imageUrl ?? raw?.image_url ?? '')),
    amount: Number(raw?.amount ?? 0),
    unitPrice: Number(raw?.unitPrice ?? raw?.unit_price ?? 0),
    quantity: Number(raw?.quantity ?? 0),
    status: String(raw?.status ?? ''),
    progress: String(raw?.progress ?? ''),
    currentNode: String(raw?.currentNode ?? raw?.current_node ?? ''),
    trackingNo: String(raw?.trackingNo ?? raw?.tracking_no ?? ''),
    merchantId: raw?.merchantId != null || raw?.merchant_id != null ? String(raw?.merchantId ?? raw?.merchant_id ?? '') : undefined,
    merchantName: String(raw?.merchantName ?? raw?.merchant_name ?? ''),
    paymentMethod: String(raw?.paymentMethod ?? raw?.payment_method ?? ''),
    receiverName: String(raw?.receiverName ?? raw?.receiver_name ?? ''),
    receiverPhone: String(raw?.receiverPhone ?? raw?.receiver_phone ?? ''),
    address: String(raw?.address ?? raw?.receiverAddress ?? raw?.receiver_address ?? ''),
    skuSummary: String(raw?.skuSummary ?? raw?.sku_summary ?? ''),
    skuAttributes: raw?.skuAttributes ?? raw?.sku_attributes ?? {},
    createdAt: String(raw?.createdAt ?? raw?.created_at ?? ''),
    completedAt: String(raw?.completedAt ?? raw?.completed_at ?? ''),
    canReview: Boolean(raw?.canReview ?? raw?.can_review),
    review: raw?.review ? {
      rating: Number(raw.review?.rating ?? 0),
      content: String(raw.review?.content ?? ''),
      reviewerName: String(raw.review?.reviewerName ?? raw.review?.reviewer_name ?? ''),
      createdAt: String(raw.review?.createdAt ?? raw.review?.created_at ?? ''),
    } : null,
  };
}

function normalizeDoctor(doctor: VetDoctor): VetDoctor {
  return {
    ...doctor,
    avatar: resolveAssetUrl(doctor.avatar),
  };
}

function normalizeBoardingStore(store: BoardingStore): BoardingStore {
  return {
    ...store,
    cover: resolveAssetUrl(store.cover),
    gallery: store.gallery.map((item) => resolveAssetUrl(item)),
  };
}

function normalizeChatContact(contact: ChatContact): ChatContact {
  return {
    ...contact,
    avatar: resolveAssetUrl(contact.avatar),
  };
}

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  const method = (init?.method || 'GET').toUpperCase();
  const config: Record<string, unknown> = {
    url: `${apiBase}${path}`,
    method,
  };

  if (headers.entries().next().done) {
    headers.set('Content-Type', 'application/json');
  }

  const headerEntries = Object.fromEntries(headers.entries());
  if (Object.keys(headerEntries).length > 0) {
    config.headers = headerEntries;
  }

  if (init?.body != null && method !== 'GET' && method !== 'HEAD') {
    config.data = typeof init.body === 'string' ? JSON.parse(init.body) : init.body;
  }

  return unwrapResponse<T>(await http.request(config));
}

function getProfileSnapshot(): UserProfile {
  const session = readAuthSession();
  const profileKey = scopedStorageKey('profile');
  const profile = readStorage(profileKey, {
    ...defaultUser,
    id: session.user?.id ? String(session.user.id) : defaultUser.id,
    username: session.user?.username || defaultUser.username,
    email: defaultUser.email,
  });
  if (!window.localStorage.getItem(profileKey)) {
    writeStorage(profileKey, profile);
  }
  return normalizeProfile(profile);
}

function getCartSnapshot(): CartItem[] {
  return readStorage<CartItem[]>(scopedStorageKey('cart'), []);
}

function getFavoriteSnapshot(): string[] {
  return readStorage<string[]>(scopedStorageKey('favorites'), []);
}

function getOrderSnapshot(): BuyerOrder[] {
  return readStorage<BuyerOrder[]>(scopedStorageKey('orders'), []).map((item) => normalizeBuyerOrder(item));
}

function getChatSnapshot() {
  const fallback: Record<string, ChatMessage[]> = {
    official: [
      {
        id: uid('msg'),
        sender: 'agent',
        content: '欢迎来到爱宠生活馆，有需要可以直接问我。',
        time: '09:20'
      }
    ],
    'store-1': [
      {
        id: uid('msg'),
        sender: 'agent',
        content: '寄养前记得把常用粮和习惯说明带上，我们会做好记录。',
        time: '10:12'
      }
    ],
    'store-2': [
      {
        id: uid('msg'),
        sender: 'agent',
        content: '如果是大型犬，可以提前和我们确认活动安排。',
        time: '11:05'
      }
    ]
  };

  const chatKey = scopedStorageKey('chats');
  const chats = readStorage<Record<string, ChatMessage[]>>(chatKey, fallback);
  if (!window.localStorage.getItem(chatKey)) {
    writeStorage(chatKey, chats);
  }
  return chats;
}

function formatNow() {
  const current = new Date();
  return `${String(current.getHours()).padStart(2, '0')}:${String(current.getMinutes()).padStart(2, '0')}`;
}

function chatbotReply(contactName: string, content: string) {
  if (content.includes('寄养')) {
    return `${contactName} 建议你提前告诉我们饮食习惯、作息和是否怕生，我们会安排更适合的照护方案。`;
  }
  if (content.includes('狗粮') || content.includes('猫粮')) {
    return `${contactName} 建议先从小规格试吃，再根据便便状态和食欲决定是否长期购买。`;
  }
  if (content.includes('运费') || content.includes('发货')) {
    return `${contactName} 目前大部分城市支持 48 小时内发出，具体以结算页说明为准。`;
  }
  return `${contactName} 已收到你的消息，我们会优先结合毛孩子的实际情况给出更准确的建议。`;
}

export const homeApi = {
  async getDashboard() {
    if (!useMockHome) {
      const featuredProducts = await catalogApi.listProducts({ sort: 'heat-desc' });
      return {
        banners: bannerSlides.map(normalizeBannerSlide),
        posts: friendPosts.map(normalizeFriendPost),
        featuredProducts: featuredProducts.slice(0, 6),
      };
    }

    return delay({
      banners: bannerSlides.map(normalizeBannerSlide),
      posts: friendPosts.map(normalizeFriendPost),
      featuredProducts: products.slice().sort((left, right) => right.heat - left.heat).slice(0, 6).map(normalizeProduct)
    });
  }
};

export const authApi = {
  async login(payload: LoginPayload) {
    if (!payload.username || !payload.password || payload.captcha.length !== 4) {
      throw new Error('请填写完整的登录信息，并输入 4 位验证码。');
    }

    if (!forceMock) {
      const session = await loginBuyer({
        identifier: payload.username,
        password: payload.password,
      });
      saveLoginSession(session);
      return delay(getProfileSnapshot(), 60);
    }

    const profile = normalizeProfile({ ...getProfileSnapshot(), username: payload.username });
    writeStorage(scopedStorageKey('profile'), profile);
    return delay(profile);
  },

  async register(payload: RegisterPayload) {
    if (payload.password !== payload.confirmPassword) {
      throw new Error('两次输入的密码不一致。');
    }

    if (!forceMock) {
      await registerBuyer({
        username: payload.username,
        email: payload.email,
        password: payload.password,
      });
      return delay(normalizeProfile({
        ...defaultUser,
        id: uid('user'),
        username: payload.username,
        email: payload.email,
        phone: payload.phone,
      }), 60);
    }

    const nextProfile: UserProfile = normalizeProfile({
      ...defaultUser,
      id: uid('user'),
      username: payload.username,
      email: payload.email,
      phone: payload.phone
    });
    writeStorage(scopedStorageKey('profile'), nextProfile);
    return delay(nextProfile);
  },

  async getSession() {
    if (!forceMock) {
      const session = readAuthSession();
      if (!session.user || (session.role !== 'normal_user' && session.role !== 'merchant_user')) {
        return null;
      }
      return delay(getProfileSnapshot(), 60);
    }

    const current = readStorage<UserProfile | null>(scopedStorageKey('profile'), null);
    return delay(current);
  },

  async logout() {
    if (!forceMock) {
      clearAuthSession();
      return;
    }

    return delay(undefined);
  }
};

export const catalogApi = {
  async listProducts(options?: {
    category?: string;
    search?: string;
    sort?: 'default' | 'price-asc' | 'price-desc' | 'name' | 'heat-desc' | 'purchase-desc';
  }) {
    if (!useMockCatalog) {
      const query = new URLSearchParams();
      if (options?.category && options.category !== '全部') {
        query.set('category', options.category);
      }
      if (options?.search) {
        query.set('search', options.search);
      }
      if (options?.sort) {
        query.set('sort', options.sort);
      }
      const result = await requestJson<any[]>(`/products?${query.toString()}`);
      return sortProducts(result.map(normalizeProduct), options?.sort);
    }

    let result = products.slice().map(normalizeProduct);
    if (options?.category && options.category !== '全部') {
      result = result.filter((product) => product.category === options.category);
    }
    if (options?.search) {
      const keyword = options.search.toLowerCase();
      result = result.filter((product) => {
        return (
          product.name.toLowerCase().includes(keyword) ||
          product.description.toLowerCase().includes(keyword) ||
          product.tags.some((tag) => tag.toLowerCase().includes(keyword))
        );
      });
    }

    switch (options?.sort) {
      case 'price-asc':
        result.sort((left, right) => left.price - right.price);
        break;
      case 'price-desc':
        result.sort((left, right) => right.price - left.price);
        break;
      case 'name':
        result.sort((left, right) => left.name.localeCompare(right.name, 'zh-Hans-CN'));
        break;
      case 'heat-desc':
        result.sort((left, right) => right.heat - left.heat);
        break;
      case 'purchase-desc':
        result.sort((left, right) => right.purchaseCount - left.purchaseCount);
        break;
      default:
        result.sort((left, right) => right.heat - left.heat);
        break;
    }

    return delay(result);
  },

  async getProduct(id: string) {
    if (!useMockCatalog) {
      return normalizeProduct(await requestJson<any>(`/products/${id}`));
    }
    const product = products.find((item) => item.id === id);
    if (!product) {
      throw new Error('未找到对应商品。');
    }
    return delay(product);
  },

  async getCategories() {
    if (!useMockCatalog) {
      const categories = await requestJson<string[]>('/products/categories');
      return ['全部', ...categories.filter((item) => item && item !== '全部')];
    }
    return delay(['全部', ...new Set(products.map((product) => product.category))]);
  },

  async getSuggestions(keyword: string) {
    if (!keyword.trim()) {
      return [];
    }

    if (!useMockCatalog) {
      return requestJson<string[]>(`/products/suggestions?keyword=${encodeURIComponent(keyword)}`);
    }

    const lowered = keyword.toLowerCase();
    const names = products
      .filter((product) => product.name.toLowerCase().includes(lowered))
      .map((product) => product.name)
      .slice(0, 6);
    return delay(names, 120);
  }
};

export const cartApi = {
  async list() {
    if (!useMockCart) {
      return requestJson<CartItem[]>('/cart');
    }
    return delay(getCartSnapshot().map(normalizeCartItem));
  },

  async add(product: Product, quantity: number, selection: CartItemSelection) {
    if (!useMockCart) {
      return requestJson<CartItem>('/cart', {
        method: 'POST',
        body: JSON.stringify({ productId: product.id, quantity, selection })
      });
    }

    const cart = getCartSnapshot();
    const existed = cart.find((item) => {
      return item.productId === product.id && serializeSelection(item.selection) === serializeSelection(selection);
    });

    const selectedPrice = resolveSelectedPrice(product, selection);

    if (existed) {
      existed.quantity += quantity;
      existed.price = selectedPrice;
    } else {
      cart.push({
        id: uid('cart'),
        productId: product.id,
        productName: product.name,
        productImage: product.image,
        price: selectedPrice,
        quantity,
        selected: true,
        selection
      });
    }
    writeStorage(scopedStorageKey('cart'), cart);
    return delay(cart[cart.length - 1] ?? existed!);
  },

  async updateQuantity(id: string, quantity: number) {
    if (!useMockCart) {
      return requestJson<void>(`/cart/${id}`, {
        method: 'PATCH',
        body: JSON.stringify({ quantity })
      });
    }

    const cart = getCartSnapshot().map((item) => {
      return item.id === id ? { ...item, quantity: Math.max(1, quantity) } : item;
    });
    writeStorage(scopedStorageKey('cart'), cart);
    return delay(undefined);
  },

  async toggleSelection(id: string, selected: boolean) {
    if (!useMockCart) {
      return requestJson<void>(`/cart/${id}/selection`, {
        method: 'PATCH',
        body: JSON.stringify({ selected })
      });
    }

    const cart = getCartSnapshot().map((item) => {
      return item.id === id ? { ...item, selected } : item;
    });
    writeStorage(scopedStorageKey('cart'), cart);
    return delay(undefined);
  },

  async toggleAll(selected: boolean) {
    const cart = getCartSnapshot().map((item) => ({ ...item, selected }));
    writeStorage(scopedStorageKey('cart'), cart);
    return delay(undefined);
  },

  async remove(id: string) {
    if (!useMockCart) {
      return requestJson<void>(`/cart/${id}`, { method: 'DELETE' });
    }
    const nextCart = getCartSnapshot().filter((item) => item.id !== id);
    writeStorage(scopedStorageKey('cart'), nextCart);
    return delay(undefined);
  },

  async clear() {
    if (!useMockCart) {
      return requestJson<void>('/cart', { method: 'DELETE' });
    }
    writeStorage(scopedStorageKey('cart'), []);
    return delay(undefined);
  },

  async checkout(draft: CheckoutDraft) {
    if (!draft.receiver || !draft.phone || !draft.address) {
      throw new Error('请先完善收货信息。');
    }

    const selectedItems = getCartSnapshot().filter((item) => item.selected);
    if (!selectedItems.length) {
      throw new Error('请先勾选要结算的商品。');
    }

    if (!useMockOrder) {
      const result = await requestJson<{ orderNo: string }>('/orders/checkout', {
        method: 'POST',
        body: JSON.stringify({
          receiver: draft.receiver,
          phone: draft.phone,
          address: draft.address,
          payment_method: draft.paymentMethod,
          items: selectedItems.map((item) => ({
            product_id: item.productId,
            product_name: item.productName,
            quantity: item.quantity,
            price: item.price,
            selection: item.selection,
          })),
        })
      });
      writeStorage(scopedStorageKey('cart'), getCartSnapshot().filter((item) => !item.selected));
      return result;
    }

    const createdAt = new Date().toISOString().slice(0, 19).replace('T', ' ');
    const nextOrders = [
      ...selectedItems.map((item) => normalizeBuyerOrder({
        id: uid('order'),
        orderNo: `PS${Date.now()}${Math.random().toString(10).slice(2, 5)}`,
        productId: item.productId,
        name: item.productName,
        image: item.productImage,
        amount: item.price * item.quantity,
        unitPrice: item.price,
        quantity: item.quantity,
        status: 'pending',
        progress: '商家备货中',
        currentNode: '等待商家发货',
        paymentMethod: draft.paymentMethod,
        receiverName: draft.receiver,
        receiverPhone: draft.phone,
        address: draft.address,
        skuSummary: Object.entries(item.selection || {}).map(([key, value]) => `${key}=${value}`).join(' / ') || '默认规格',
        skuAttributes: item.selection,
        createdAt,
        canReview: false,
      })),
      ...getOrderSnapshot(),
    ];
    writeStorage(scopedStorageKey('orders'), nextOrders);
    writeStorage(scopedStorageKey('cart'), getCartSnapshot().filter((item) => !item.selected));
    return delay({ orderNo: `PS${Date.now()}` }, 300);
  }
};

export const orderApi = {
  async list(status = 'all') {
    if (!useMockOrder) {
      const query = status && status !== 'all' ? `?status=${encodeURIComponent(status)}` : '';
      const result = await requestJson<{ list: BuyerOrder[]; total: number; page: number; size: number }>(`/orders${query}`);
      return {
        ...result,
        list: Array.isArray(result?.list) ? result.list.map((item) => normalizeBuyerOrder(item)) : [],
      };
    }

    const all = getOrderSnapshot();
    const filtered = status && status !== 'all'
      ? all.filter((item) => item.status === status)
      : all;
    return delay({ list: filtered, total: filtered.length, page: 1, size: filtered.length || 10 }, 120);
  },

  async cancel(orderId: string) {
    if (!useMockOrder) {
      const order = await requestJson<BuyerOrder>(`/orders/${orderId}/cancel`, {
        method: 'PUT',
      });
      return normalizeBuyerOrder(order);
    }

    const next = getOrderSnapshot().map((item) => item.id === orderId
      ? normalizeBuyerOrder({
          ...item,
          status: 'cancelled',
          progress: '订单已取消',
          currentNode: '买家已取消订单',
          canReview: false,
        })
      : item);
    writeStorage(scopedStorageKey('orders'), next);
    const matched = next.find((item) => item.id === orderId);
    if (!matched) {
      throw new Error('订单不存在。');
    }
    return delay(matched, 120);
  },

  async confirm(orderId: string) {
    if (!useMockOrder) {
      const order = await requestJson<BuyerOrder>(`/orders/${orderId}/confirm`, {
        method: 'PUT',
      });
      return normalizeBuyerOrder(order);
    }

    const completedAt = new Date().toISOString().slice(0, 19).replace('T', ' ');
    const next = getOrderSnapshot().map((item) => item.id === orderId
      ? normalizeBuyerOrder({
          ...item,
          status: 'completed',
          progress: '订单已完成',
          currentNode: '买家已签收',
          completedAt,
          canReview: true,
        })
      : item);
    writeStorage(scopedStorageKey('orders'), next);
    const matched = next.find((item) => item.id === orderId);
    if (!matched) {
      throw new Error('订单不存在。');
    }
    return delay(matched, 120);
  },

  async updateAddress(orderId: string, address: string) {
    if (!useMockOrder) {
      const order = await requestJson<BuyerOrder>(`/orders/${orderId}/address`, {
        method: 'PUT',
        body: JSON.stringify({ address }),
      });
      return normalizeBuyerOrder(order);
    }

    const next = getOrderSnapshot().map((item) => item.id === orderId
      ? normalizeBuyerOrder({
          ...item,
          address,
        })
      : item);
    writeStorage(scopedStorageKey('orders'), next);
    const matched = next.find((item) => item.id === orderId);
    if (!matched) {
      throw new Error('订单不存在。');
    }
    return delay(matched, 120);
  },

  async requestReturn(orderId: string) {
    if (!useMockOrder) {
      const order = await requestJson<BuyerOrder>(`/orders/${orderId}/return`, {
        method: 'PUT',
      });
      return normalizeBuyerOrder(order);
    }

    const next = getOrderSnapshot().map((item) => item.id === orderId
      ? normalizeBuyerOrder({
          ...item,
          status: 'cancelled',
          progress: '退货申请中',
          currentNode: '等待商家处理退货申请',
          canReview: false,
        })
      : item);
    writeStorage(scopedStorageKey('orders'), next);
    const matched = next.find((item) => item.id === orderId);
    if (!matched) {
      throw new Error('订单不存在。');
    }
    return delay(matched, 120);
  },

  async submitReview(orderId: string, payload: { rating: number; content: string }) {
    if (!useMockOrder) {
      const order = await requestJson<BuyerOrder>(`/orders/${orderId}/review`, {
        method: 'POST',
        body: JSON.stringify(payload),
      });
      return normalizeBuyerOrder(order);
    }

    const next = getOrderSnapshot().map((item) => {
      if (item.id !== orderId) return item;
      return normalizeBuyerOrder({
        ...item,
        canReview: false,
        review: {
          rating: payload.rating,
          content: payload.content,
          reviewerName: getProfileSnapshot().username,
          createdAt: new Date().toISOString().slice(0, 19).replace('T', ' '),
        },
      });
    });
    writeStorage(scopedStorageKey('orders'), next);
    const matched = next.find((item) => item.id === orderId);
    if (!matched) {
      throw new Error('订单不存在。');
    }
    return delay(matched, 120);
  }
};

export const favoriteApi = {
  async listIds() {
    return delay(getFavoriteSnapshot(), 80);
  },

  async toggle(productId: string) {
    const current = getFavoriteSnapshot();
    const next = current.includes(productId)
      ? current.filter((id) => id !== productId)
      : [...current, productId];
    writeStorage(scopedStorageKey('favorites'), next);
    return delay(next, 80);
  },

  async set(productId: string, active: boolean) {
    const current = getFavoriteSnapshot();
    const exists = current.includes(productId);
    const next = active
      ? (exists ? current : [...current, productId])
      : current.filter((id) => id !== productId);
    writeStorage(scopedStorageKey('favorites'), next);
    return delay(next, 80);
  }
};

export const profileApi = {
  async getProfile() {
    if (!useMockProfile) {
      return requestJson<UserProfile>('/user/profile');
    }
    return delay(getProfileSnapshot());
  },

  async updateProfile(payload: Partial<UserProfile>) {
    if (!useMockProfile) {
      return requestJson<UserProfile>('/user/profile', {
        method: 'PUT',
        body: JSON.stringify(payload)
      });
    }

    const profileKey = scopedStorageKey('profile');
    const profile = {
      ...getProfileSnapshot(),
      ...payload
    };
    writeStorage(profileKey, profile);
    return delay(profile);
  }
};

export const serviceApi = {
  async listDoctors() {
    if (!useMockService) {
      return requestJson<VetDoctor[]>('/medical/doctors');
    }
    return delay(doctors.map(normalizeDoctor));
  },

  async askDoctor(doctorId: string, question: string) {
    if (!useMockService) {
      return requestJson<{ reply: string }>(`/medical/doctors/${doctorId}/ask`, {
        method: 'POST',
        body: JSON.stringify({ question })
      });
    }

    const doctor = doctors.find((item) => item.id === doctorId);
    if (!doctor) {
      throw new Error('医生不存在。');
    }
    const reply = question.includes('吐') || question.includes('拉')
      ? `${doctor.name} 建议先观察精神状态、饮水量和排便频次，若持续超过 12 小时建议尽快线下就诊。`
      : `${doctor.name} 建议你先记录饮食、作息和近期环境变化，这样能更快定位问题。`;
    return delay({ reply }, 260);
  },

  async listBoardingStores(city?: string) {
    if (!useMockService) {
      const query = city ? `?city=${encodeURIComponent(city)}` : '';
      return requestJson<BoardingStore[]>(`/boarding/stores${query}`);
    }

    const result = city && city !== '全部'
      ? boardingStores.filter((store) => store.city === city)
      : boardingStores;
    return delay(result.map(normalizeBoardingStore));
  },

  async reserveBoarding(storeId: string, payload: { petName: string; days: number; note: string }) {
    if (!payload.petName || payload.days < 1) {
      throw new Error('请填写宠物名字和寄养天数。');
    }

    if (!useMockService) {
      return requestJson<{ reservationNo: string }>(`/boarding/stores/${storeId}/reserve`, {
        method: 'POST',
        body: JSON.stringify(payload)
      });
    }

    return delay({ reservationNo: `BY${Date.now()}` }, 280);
  }
};

export const chatApi = {
  async listContacts() {
    const session = readAuthSession();
    const buyerUserId = session.user?.id ? String(session.user.id) : 'guest';
    const profile = getProfileSnapshot();
    const buyerName = session.user?.username || profile.username;
    const result = await orderApi.list('all');
    const orders = Array.isArray(result?.list) ? result.list : [];
    syncBuyerOrderThreads(orders, { buyerUserId, buyerName, buyerAvatar: profile.avatar });
    return delay(listBuyerContacts(buyerUserId).map(normalizeChatContact), 80);
  },

  async listMessages(contactId: string) {
    markThreadRead(contactId, 'buyer');
    return delay(listThreadMessages(contactId, 'buyer'), 60);
  },

  async sendMessage(contactId: string, content: string) {
    return delay(sendThreadMessage(contactId, 'buyer', content), 80);
  }
};

function sortProducts(
  list: Product[],
  sort?: 'default' | 'price-asc' | 'price-desc' | 'name' | 'heat-desc' | 'purchase-desc'
) {
  const result = list.slice();
  switch (sort) {
    case 'price-asc':
      result.sort((left, right) => left.price - right.price);
      break;
    case 'price-desc':
      result.sort((left, right) => right.price - left.price);
      break;
    case 'name':
      result.sort((left, right) => left.name.localeCompare(right.name, 'zh-Hans-CN'));
      break;
    case 'purchase-desc':
      result.sort((left, right) => right.purchaseCount - left.purchaseCount);
      break;
    case 'heat-desc':
    case 'default':
    default:
      result.sort((left, right) => right.heat - left.heat);
      break;
  }
  return result;
}