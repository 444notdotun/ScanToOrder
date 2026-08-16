package com.scantoorder.scantoorder.data.model;

import com.scantoorder.scantoorder.utils.CodeGenerator;
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
    private boolean isActive;
    @PrePersist
    public void prePersist(){
        isActive=true;
        categoryName = CodeGenerator.generate(CodePrefix.CATEGORY);
    }

}
