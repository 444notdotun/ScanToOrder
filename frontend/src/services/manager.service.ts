import { apiClient } from './api';
import { MenuItem, Order, RestaurantTable, Worker } from '@/types';
import { mockDb } from './mockDb';

export const managerService = {
  getAllOrders: (params?: Record<string, string>) => 
    apiClient.get<Order[]>('/orders', { params })
      .then(res => res as any)
      .catch(() => mockDb.getAllOrders()),

  // Menu CRUD
  getMenuAdmin: () => 
    apiClient.get<MenuItem[]>('/menu/admin')
      .then(res => res as any)
      .catch(() => mockDb.getMenuAdmin()),

  createMenuItem: (data: Partial<MenuItem>) => 
    apiClient.post('/menu/items', data)
      .then(res => res as any)
      .catch(() => mockDb.createMenuItem(data)),

  updateMenuItem: (id: string, data: Partial<MenuItem>) => 
    apiClient.put(`/menu/items/${id}`, data)
      .then(res => res as any)
      .catch(() => mockDb.updateMenuItem(id, data)),

  deleteMenuItem: (id: string) => 
    apiClient.delete(`/menu/items/${id}`)
      .then(res => res as any)
      .catch(() => mockDb.deleteMenuItem(id)),

  toggleMenuItemAvailability: (id: string) => 
    apiClient.patch(`/items/${id}/toggle`)
      .then(res => res as any)
      .catch(() => mockDb.toggleMenuItemAvailability(id)),

  // Table CRUD
  getTables: () => 
    apiClient.get<RestaurantTable[]>('/tables')
      .then(res => res as any)
      .catch(() => mockDb.getTables()),

  createTable: (data: { tableNumber: number; capacity: number }) => 
    apiClient.post<RestaurantTable>('/tables', data)
      .then(res => res as any)
      .catch(() => mockDb.createTable(data)),

  updateTable: (id: string, data: any) => 
    apiClient.put(`/tables/${id}`, data)
      .then(res => res as any)
      .catch(() => mockDb.updateTable(id, data)),

  deleteTable: (id: string) => 
    apiClient.delete(`/tables/${id}`)
      .then(res => res as any)
      .catch(() => mockDb.deleteTable(id)),

  // Worker CRUD
  getWorkers: () => 
    apiClient.get<Worker[]>('/workers')
      .then(res => res as any)
      .catch(() => mockDb.getWorkers()),

  createWorker: (data: { name: string; role: string; username: string }) => 
    apiClient.post<Worker>('/workers', data)
      .then(res => res as any)
      .catch(() => mockDb.createWorker(data)),

  updateWorker: (id: string, data: any) => 
    apiClient.put(`/workers/${id}`, data)
      .then(res => res as any)
      .catch(() => mockDb.updateWorker(id, data)),

  deleteWorker: (id: string) => 
    apiClient.delete(`/workers/${id}`)
      .then(res => res as any)
      .catch(() => mockDb.deleteWorker(id)),
};
