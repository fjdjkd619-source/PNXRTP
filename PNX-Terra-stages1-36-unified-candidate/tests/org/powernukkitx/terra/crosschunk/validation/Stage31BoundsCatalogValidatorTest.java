package org.powernukkitx.terra.crosschunk.validation;

import org.powernukkitx.terra.crosschunk.bounds.*;
import java.util.List;

public final class Stage31BoundsCatalogValidatorTest {
    public static void main(String[] args) {
        var good=new ScriptBoundsProfile("origen","tree",new Bounds3i(-2,-4,-2,2,8,2),new Bounds3i(-3,-4,-3,3,8,3),100,100,0,List.of("leaf"),"audited script closure");
        if(!BoundsCatalogValidator.validate(List.of(good)).valid()) throw new AssertionError("valid profile rejected");
        var duplicate=BoundsCatalogValidator.validate(List.of(good,good));
        if(duplicate.valid() || duplicate.errors().isEmpty()) throw new AssertionError("duplicate profile accepted");
        System.out.println("Stage31BoundsCatalogValidatorTest: PASS");
    }
}
