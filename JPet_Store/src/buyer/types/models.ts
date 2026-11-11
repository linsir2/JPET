export interface UserProfile {
  id: string;
  username: string;
  email: string;
  phone: string;
  intro: string;
  avatar: string;
  joinedAt: string;
  address: string;
  preferences: string[];
}

export interface ProductPriceOption {
  label: string;
  value: string;
  delta: number;
}

export interface ProductVariant {
  skuId?: string;
  attributes: Record<string, string>;
  price: number;
  stock: number;
  image?: string;
}

export interface ProductOptionGroup {
  name: string;
  options: string[];
}

export interface Product {
  id: string;
  name: string;
  category: string;
  subCategory: string;
  price: number;
  heat: number;
  purchaseCount: number;
  rating: number;
  stock: number;
  description: string;
  image: string;
  tags: string[];
  coreAttributes?: string[];
  optionGroups?: ProductOptionGroup[];
  variants?: ProductVariant[];
  sizes?: ProductPriceOption[];
  specifications?: ProductPriceOption[];
}

export interface BannerSlide {
  id: string;
  title: string;
  subtitle: string;
  image: string;
  accent: string;
}

export interface FriendPost {
  id: string;
  name: string;
  avatar: string;
  message: string;
  image: string;
}

export interface CartItemSelection {
  [key: string]: string | undefined;
  size?: string;
  specification?: string;
}

export interface CartItem {
  id: string;
  productId: string;
  productName: string;
  productImage: string;
  price: number;
  quantity: number;
  selected: boolean;
  selection: CartItemSelection;
}

export interface CheckoutDraft {
  receiver: string;
  phone: string;
  address: string;
  paymentMethod: 'alipay' | 'wechat' | 'bank';
}

export interface BuyerOrderReview {
  rating: number;
  content: string;
  reviewerName?: string;
  createdAt?: string;
}

export interface BuyerOrder {
  id: string;
  orderNo: string;
  productId: string;
  name: string;
  image: string;
  amount: number;
  unitPrice: number;
  quantity: number;
  status: string;
  progress?: string;
  currentNode?: string;
  trackingNo?: string;
  merchantId?: string;
  merchantName?: string;
  paymentMethod?: string;
  receiverName?: string;
  receiverPhone?: string;
  address?: string;
  skuSummary?: string;
  skuAttributes?: Record<string, string>;
  createdAt?: string;
  completedAt?: string;
  canReview: boolean;
  review?: BuyerOrderReview | null;
}

export interface VetDoctor {
  id: string;
  name: string;
  title: string;
  specialty: string;
  online: boolean;
  avatar: string;
  responseTime: string;
  summary: string;
}

export interface BoardingStore {
  id: string;
  name: string;
  city: string;
  area: string;
  rating: number;
  reviews: number;
  cover: string;
  gallery: string[];
  priceText: string;
  services: string[];
  description: string;
  contact: string;
}

export interface ChatContact {
  id: string;
  name: string;
  avatar: string;
  lastMessage: string;
  online: boolean;
  unread: number;
  type: 'official' | 'store';
  orderId?: string;
  orderNo?: string;
  productName?: string;
  subtitle?: string;
}

export interface ChatMessage {
  id: string;
  sender: 'user' | 'agent';
  content: string;
  time: string;
}

export interface LoginPayload {
  username: string;
  password: string;
  captcha: string;
}

export interface RegisterPayload {
  username: string;
  phone: string;
  email: string;
  password: string;
  confirmPassword: string;
}