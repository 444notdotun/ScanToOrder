package com.scantoorder.scantoorder.data.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Category{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String categoryId;
    private String categoryName;
    @ManyToOne
    @JoinColumn
    private Menu menuId;

}
