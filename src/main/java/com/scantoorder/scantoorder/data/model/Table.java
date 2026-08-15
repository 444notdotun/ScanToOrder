package com.scantoorder.scantoorder.data.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;
@Entity
@Data
public class Table {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String tableId;
    @Column(unique = true)
    private String tableName;
}
