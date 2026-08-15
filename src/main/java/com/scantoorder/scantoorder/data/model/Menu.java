package com.scantoorder.scantoorder.data.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class Menu {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String menuId;
    private String menuName;

    private static Menu instance;

    public static Menu getInstance() {
        if (instance == null) {
            instance = new Menu();
        }
        return instance;
    }


}
