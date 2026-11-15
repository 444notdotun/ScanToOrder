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
public class RestaurantTable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String tableId;
    @Column(unique = true)
    private String tableNumber;
    @Column(unique = true)
    private Integer displayNumber;
    private  int Capacity;
    private boolean isActive;

    @PrePersist
    public void prePersist(){
         isActive=true;
         tableNumber= CodeGenerator.generate(CodePrefix.TABLE);
     }
}
