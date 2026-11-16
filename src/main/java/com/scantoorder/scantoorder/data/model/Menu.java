package com.scantoorder.scantoorder.data.model;

import com.scantoorder.scantoorder.utils.CodeGenerator;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class Menu {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String menuId;
    private String menuName;
    private boolean isActive;
    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @PrePersist
    public void prePersist(){
        isActive=true;
        if (menuName == null) {
            menuName = CodeGenerator.generate(CodePrefix.MENU);
        }
    }
}
