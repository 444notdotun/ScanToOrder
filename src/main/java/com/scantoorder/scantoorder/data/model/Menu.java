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

    private static Menu instance;

    public static Menu getInstance() {
        if (instance == null) {
            instance = new Menu();
        }
        return instance;
    }
    @PrePersist
    public void prePersist(){
        isActive=true;
        menuName = CodeGenerator.generate(CodePrefix.MENU);
    }


}
