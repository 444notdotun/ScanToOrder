package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.data.model.Menu;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.dtos.respond.MenuResponse;

public interface MenuService {
    MenuResponse generateMenu(String tenantId);

    // Every tenant has exactly one active menu. It is created the first time it is needed.
    Menu ensureActiveMenu(Tenant tenant);

    void clearCache(String tenantId);
}
