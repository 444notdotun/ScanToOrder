package com.scantoorder.scantoorder.data.model;

import com.scantoorder.scantoorder.utils.CodeGenerator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String categoryId;
    @Column(unique = true)
    private String categoryName;
    private boolean isActive;
    @PrePersist
    public void prePersist(){
        isActive=true;
    }



}
