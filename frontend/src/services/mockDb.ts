import { MenuItem, Order, ServiceCall, RestaurantTable, Seat, Worker, Review, ServiceCallType } from '@/types';

// Helper to load/save from localStorage
const STORAGE_KEY = 'scan_to_order_mock_db';

interface MockDBState {
  tables: RestaurantTable[];
  menuItems: MenuItem[];
  orders: Order[];
  serviceCalls: ServiceCall[];
  workers: Worker[];
  reviews: Review[];
  sessions: { id: string; tableId: string; seatIds: string[]; status: 'ACTIVE' | 'CLOSED' }[];
}

const DEFAULT_MENU: MenuItem[] = [
  {
    id: 'm1',
    name: 'Spicy Jollof Rice with Chicken',
    description: 'Rich, smoky party Jollof rice served with grilled peppered chicken and sweet plantains (dodo).',
    price: 4500,
    imageUrl: 'https://images.unsplash.com/photo-1604329760661-e71dc83f8f26?auto=format&fit=crop&q=80&w=400',
    categoryId: 'c1',
    categoryName: 'Mains',
    available: true,
  },
  {
    id: 'm2',
    name: 'Pounded Yam & Egusi Soup',
    description: 'Smooth, fluffy pounded yam served with rich Egusi (melon seed) soup cooked with assorted meat, fish, and spinach.',
    price: 5500,
    imageUrl: 'https://images.unsplash.com/photo-1534422298391-e4f8c172dddb?auto=format&fit=crop&q=80&w=400',
    categoryId: 'c1',
    categoryName: 'Mains',
    available: true,
  },
  {
    id: 'm3',
    name: 'Beef Suya (Platter)',
    description: 'Thinly sliced grilled beef skewers coated in spicy yaji spice, served with onions, tomatoes, and cabbage.',
    price: 3500,
    imageUrl: 'https://images.unsplash.com/photo-1544025162-d76694265947?auto=format&fit=crop&q=80&w=400',
    categoryId: 'c2',
    categoryName: 'Appetizers',
    available: true,
  },
  {
    id: 'm4',
    name: 'Crispy Spring Rolls & Samosas',
    description: 'A portion of 3 spring rolls and 3 samosas filled with seasoned minced beef and fresh vegetables.',
    price: 2000,
    imageUrl: 'https://images.unsplash.com/photo-1541696490-8744a5db022b?auto=format&fit=crop&q=80&w=400',
    categoryId: 'c2',
    categoryName: 'Appetizers',
    available: true,
  },
  {
    id: 'm5',
    name: 'Zobo Drink (Hibiscus Infusion)',
    description: 'Refreshing local hibiscus flower tea brewed with cloves, ginger, sweet pineapple, and citrus fruits.',
    price: 1200,
    imageUrl: 'https://images.unsplash.com/photo-1497534446932-c925b458314e?auto=format&fit=crop&q=80&w=400',
    categoryId: 'c3',
    categoryName: 'Drinks',
    available: true,
  },
  {
    id: 'm6',
    name: 'Classic Chapman',
    description: 'A signature Nigerian cocktail featuring a blend of Fanta, Sprite, Angostura bitters, blackcurrant, and fresh cucumber.',
    price: 1800,
    imageUrl: 'https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?auto=format&fit=crop&q=80&w=400',
    categoryId: 'c3',
    categoryName: 'Drinks',
    available: true,
  },
  {
    id: 'm7',
    name: 'Warm Puff Puff with Ice Cream',
    description: 'Freshly fried sweet yeast dough balls dusted with cinnamon, served with a scoop of vanilla bean ice cream.',
    price: 2500,
    imageUrl: 'https://images.unsplash.com/photo-1551024601-bec78aea704b?auto=format&fit=crop&q=80&w=400',
    categoryId: 'c4',
    categoryName: 'Desserts',
    available: true,
  }
];

const DEFAULT_TABLES: RestaurantTable[] = Array.from({ length: 8 }, (_, i) => {
  const tableId = `t${i + 1}`;
  const tableNumber = i + 1;
  const capacity = tableNumber % 3 === 0 ? 6 : 4;
  
  const seats: Seat[] = Array.from({ length: capacity }, (_, j) => ({
    id: `${tableId}-s${j + 1}`,
    tableId: tableId,
    seatNumber: j + 1,
    status: 'VACANT',
    claimedBySessionId: null
  }));

  return {
    id: tableId,
    tableNumber,
    capacity,
    qrCodeUrl: `https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=http://localhost:3000/table/${tableId}`,
    seats
  };
});

const DEFAULT_WORKERS: Worker[] = [
  { id: 'w1', name: 'Funmi Alao', role: 'WAITER', username: 'funmi' },
  { id: 'w2', name: 'Chinedu Okafor', role: 'WAITER', username: 'chinedu' },
  { id: 'w3', name: 'Chef Tunde', role: 'CHEF', username: 'tunde' },
  { id: 'w4', name: 'Manager Beatrice', role: 'MANAGER', username: 'beatrice' }
];

const loadState = (): MockDBState => {
  if (typeof window === 'undefined') {
    return { tables: DEFAULT_TABLES, menuItems: DEFAULT_MENU, orders: [], serviceCalls: [], workers: DEFAULT_WORKERS, reviews: [], sessions: [] };
  }
  const saved = localStorage.getItem(STORAGE_KEY);
  if (saved) {
    try {
      return JSON.parse(saved);
    } catch (e) {
      console.error("Failed to parse mock DB state:", e);
    }
  }
  
  const initialState: MockDBState = {
    tables: DEFAULT_TABLES,
    menuItems: DEFAULT_MENU,
    orders: [],
    serviceCalls: [],
    workers: DEFAULT_WORKERS,
    reviews: [],
    sessions: []
  };
  localStorage.setItem(STORAGE_KEY, JSON.stringify(initialState));
  return initialState;
};

const saveState = (state: MockDBState) => {
  if (typeof window !== 'undefined') {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }
};

export const mockDb = {
  // Seats & Sessions
  getSeatMap: (tableId: string) => {
    const state = loadState();
    const table = state.tables.find(t => t.id === tableId);
    if (!table) throw new Error("Table not found");
    return {
      id: table.id,
      tableNumber: table.tableNumber,
      capacity: table.capacity,
      seats: table.seats || []
    };
  },

  claimSeat: (seatId: string) => {
    const state = loadState();
    let seat: Seat | undefined;
    let foundTable: RestaurantTable | undefined;

    for (const table of state.tables) {
      const s = table.seats?.find(s => s.id === seatId);
      if (s) {
        seat = s;
        foundTable = table;
        break;
      }
    }

    if (!seat || !foundTable) throw new Error("Seat not found");
    if (seat.status === 'OCCUPIED') throw new Error("Seat is already occupied");

    // Check if table already has an active session, if not create one
    let activeSession = state.sessions.find(s => s.tableId === foundTable!.id && s.status === 'ACTIVE');
    if (!activeSession) {
      const newSessionId = `sess-${Date.now()}`;
      activeSession = {
        id: newSessionId,
        tableId: foundTable.id,
        seatIds: [],
        status: 'ACTIVE'
      };
      state.sessions.push(activeSession);
      foundTable.currentSessionId = newSessionId;
    }

    seat.status = 'OCCUPIED';
    seat.claimedBySessionId = activeSession.id;
    if (!activeSession.seatIds.includes(seat.id)) {
      activeSession.seatIds.push(seat.id);
    }

    saveState(state);

    return {
      token: `mock-token-${Date.now()}`,
      seatId: seat.id,
      tableId: foundTable.id,
      sessionId: activeSession.id
    };
  },

  // Menu
  getMenu: () => {
    const state = loadState();
    return state.menuItems.filter(item => item.available);
  },

  getMenuAdmin: () => {
    const state = loadState();
    return state.menuItems;
  },

  createMenuItem: (data: Partial<MenuItem>) => {
    const state = loadState();
    const newItem: MenuItem = {
      id: `m-${Date.now()}`,
      name: data.name || 'Unnamed Item',
      description: data.description || '',
      price: data.price || 0,
      imageUrl: data.imageUrl || 'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&q=80&w=400',
      categoryId: data.categoryId || 'c1',
      categoryName: data.categoryName || 'Mains',
      available: data.available !== undefined ? data.available : true
    };
    state.menuItems.push(newItem);
    saveState(state);
    return newItem;
  },

  updateMenuItem: (id: string, data: Partial<MenuItem>) => {
    const state = loadState();
    state.menuItems = state.menuItems.map(item => 
      item.id === id ? { ...item, ...data } : item
    );
    saveState(state);
    return state.menuItems.find(item => item.id === id);
  },

  deleteMenuItem: (id: string) => {
    const state = loadState();
    state.menuItems = state.menuItems.filter(item => item.id !== id);
    saveState(state);
    return true;
  },

  toggleMenuItemAvailability: (id: string) => {
    const state = loadState();
    state.menuItems = state.menuItems.map(item => 
      item.id === id ? { ...item, available: !item.available } : item
    );
    saveState(state);
    return state.menuItems.find(item => item.id === id);
  },

  // Orders
  createOrder: (payload: { seatId: string; items: { menuItemId: string; quantity: number; notes?: string }[] }) => {
    const state = loadState();
    
    // Find table and session info
    let foundSeat: Seat | undefined;
    let foundTable: RestaurantTable | undefined;
    for (const table of state.tables) {
      const s = table.seats?.find(s => s.id === payload.seatId);
      if (s) {
        foundSeat = s;
        foundTable = table;
        break;
      }
    }

    if (!foundSeat || !foundTable) throw new Error("Seat not found");
    const sessionId = foundSeat.claimedBySessionId || `sess-fallback-${Date.now()}`;

    // Map order items
    let totalAmount = 0;
    const items = payload.items.map((item, idx) => {
      const menu = state.menuItems.find(m => m.id === item.menuItemId);
      if (!menu) throw new Error(`Menu item ${item.menuItemId} not found`);
      const price = menu.price;
      const subtotal = price * item.quantity;
      totalAmount += subtotal;

      return {
        id: `oi-${Date.now()}-${idx}`,
        menuItemId: menu.id,
        name: menu.name,
        quantity: item.quantity,
        price: price,
        notes: item.notes,
        imageUrl: menu.imageUrl
      };
    });

    const newOrder: Order = {
      id: `ord-${Math.floor(1000 + Math.random() * 9000)}`,
      seatId: payload.seatId,
      seatNumber: foundSeat.seatNumber,
      tableId: foundTable.id,
      tableNumber: foundTable.tableNumber,
      sessionId: sessionId,
      status: 'PENDING_PAYMENT',
      totalAmount: totalAmount,
      items,
      createdAt: new Date().toISOString()
    };

    state.orders.push(newOrder);
    saveState(state);

    return {
      orderId: newOrder.id,
      totalAmount: newOrder.totalAmount
    };
  },

  payOrder: (id: string, paymentMethod: string) => {
    const state = loadState();
    const order = state.orders.find(o => o.id === id);
    if (!order) throw new Error("Order not found");

    order.status = 'PAID';
    order.updatedAt = new Date().toISOString();
    
    saveState(state);
    return {
      success: true,
      paymentUrl: undefined
    };
  },

  getOrderStatus: (id: string) => {
    const state = loadState();
    const order = state.orders.find(o => o.id === id);
    if (!order) throw new Error("Order not found");
    return order;
  },

  // Kitchen operations
  getKitchenOrders: () => {
    const state = loadState();
    // Kitchen sees PAID, PREPARING, and READY orders
    return state.orders.filter(o => ['PAID', 'PREPARING', 'READY'].includes(o.status));
  },

  updateOrderStatus: (id: string, status: 'PREPARING' | 'READY') => {
    const state = loadState();
    const order = state.orders.find(o => o.id === id);
    if (!order) throw new Error("Order not found");

    order.status = status;
    order.updatedAt = new Date().toISOString();
    saveState(state);
    return order;
  },

  // Waiter operations
  getReadyOrders: () => {
    const state = loadState();
    return state.orders.filter(o => o.status === 'READY');
  },

  markDelivered: (id: string) => {
    const state = loadState();
    const order = state.orders.find(o => o.id === id);
    if (!order) throw new Error("Order not found");

    order.status = 'DELIVERED';
    order.updatedAt = new Date().toISOString();
    saveState(state);
    return order;
  },

  // Service Calls
  createServiceCall: (payload: { seatId: string; tableId: string; requestType: ServiceCallType; note?: string }) => {
    const state = loadState();
    const table = state.tables.find(t => t.id === payload.tableId);
    const seat = table?.seats?.find(s => s.id === payload.seatId);

    const newCall: ServiceCall = {
      id: `sc-${Date.now()}`,
      seatId: payload.seatId,
      seatNumber: seat?.seatNumber || 0,
      tableId: payload.tableId,
      tableNumber: table?.tableNumber || 0,
      requestType: payload.requestType,
      status: 'PENDING',
      note: payload.note,
      createdAt: new Date().toISOString()
    };

    state.serviceCalls.push(newCall);
    saveState(state);
    return newCall;
  },

  getServiceCalls: () => {
    const state = loadState();
    return state.serviceCalls;
  },

  updateServiceCall: (id: string, payload: { status: 'IN_PROGRESS' | 'RESOLVED'; waiterId?: string }) => {
    const state = loadState();
    const call = state.serviceCalls.find(c => c.id === id);
    if (!call) throw new Error("Service call not found");

    call.status = payload.status === 'RESOLVED' ? 'RESOLVED' : 'IN_PROGRESS';
    if (payload.waiterId) {
      call.waiterId = payload.waiterId;
      const waiter = state.workers.find(w => w.id === payload.waiterId);
      if (waiter) call.waiterName = waiter.name;
    }
    
    // Auto remove resolved calls or keep them
    saveState(state);
    return call;
  },

  releaseSeat: (id: string) => {
    const state = loadState();
    
    // Find seat and vacate it
    for (const table of state.tables) {
      const seat = table.seats?.find(s => s.id === id);
      if (seat) {
        seat.status = 'VACANT';
        const session = state.sessions.find(s => s.id === seat.claimedBySessionId);
        if (session) {
          session.seatIds = session.seatIds.filter(sid => sid !== id);
        }
        seat.claimedBySessionId = null;
        break;
      }
    }
    
    saveState(state);
    return true;
  },

  closeSession: (id: string) => {
    const state = loadState();
    const session = state.sessions.find(s => s.id === id);
    if (session) {
      session.status = 'CLOSED';
      // Release all seats associated with session
      for (const table of state.tables) {
        if (table.id === session.tableId) {
          table.currentSessionId = undefined;
          table.seats = table.seats?.map(seat => {
            if (seat.claimedBySessionId === id) {
              return { ...seat, status: 'VACANT', claimedBySessionId: null };
            }
            return seat;
          });
        }
      }
    }
    saveState(state);
    return true;
  },

  // Manager functions
  getAllOrders: () => {
    const state = loadState();
    return state.orders;
  },

  getTables: () => {
    const state = loadState();
    return state.tables;
  },

  createTable: (data: { tableNumber: number; capacity: number }) => {
    const state = loadState();
    const tableId = `t-${Date.now()}`;
    const seats: Seat[] = Array.from({ length: data.capacity }, (_, j) => ({
      id: `${tableId}-s${j + 1}`,
      tableId: tableId,
      seatNumber: j + 1,
      status: 'VACANT',
      claimedBySessionId: null
    }));

    const newTable: RestaurantTable = {
      id: tableId,
      tableNumber: data.tableNumber,
      capacity: data.capacity,
      qrCodeUrl: `https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=http://localhost:3000/table/${tableId}`,
      seats
    };

    state.tables.push(newTable);
    saveState(state);
    return newTable;
  },

  updateTable: (id: string, data: any) => {
    const state = loadState();
    state.tables = state.tables.map(t => 
      t.id === id ? { ...t, ...data } : t
    );
    saveState(state);
    return state.tables.find(t => t.id === id);
  },

  deleteTable: (id: string) => {
    const state = loadState();
    state.tables = state.tables.filter(t => t.id !== id);
    saveState(state);
    return true;
  },

  getWorkers: () => {
    const state = loadState();
    return state.workers;
  },

  createWorker: (data: { name: string; role: string; username: string }) => {
    const state = loadState();
    const newWorker: Worker = {
      id: `w-${Date.now()}`,
      name: data.name,
      role: data.role as any,
      username: data.username
    };
    state.workers.push(newWorker);
    saveState(state);
    return newWorker;
  },

  updateWorker: (id: string, data: any) => {
    const state = loadState();
    state.workers = state.workers.map(w => 
      w.id === id ? { ...w, ...data } : w
    );
    saveState(state);
    return state.workers.find(w => w.id === id);
  },

  deleteWorker: (id: string) => {
    const state = loadState();
    state.workers = state.workers.filter(w => w.id !== id);
    saveState(state);
    return true;
  },

  submitReview: (payload: { sessionId: string; rating: number; reviewText: string }) => {
    const state = loadState();
    const newReview: Review = {
      id: `rev-${Date.now()}`,
      sessionId: payload.sessionId,
      rating: payload.rating,
      reviewText: payload.reviewText,
      createdAt: new Date().toISOString()
    };
    state.reviews.push(newReview);
    saveState(state);
    return newReview;
  }
};
