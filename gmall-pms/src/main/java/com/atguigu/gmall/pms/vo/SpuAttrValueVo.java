package com.atguigu.gmall.pms.vo;

import com.atguigu.gmall.pms.entity.SpuAttrValueEntity;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.List;

public class SpuAttrValueVo extends SpuAttrValueEntity {

    private List<String> valueSelected;


    //使用的时候把 valueSelected 直接放到values里面 ,重写set方法就可以了,set方法的本质就是把接收到的value值放到我们定义的里面
    // 本质就是通过set方法执行即可,
    //注意这里面的private String attrValue 这里的是string  ,而传进来的是一个list集合
    //所以这里需要把集合变化为一个以逗号分隔的字符串
    public void setValueSelected(List<String> valueSelected) {
        //如果集合为空就不用给她赋值了
        if (CollectionUtils.isEmpty(valueSelected)){
            return;
        }
        //调用父类的的私有属性
        //所以这个程序的步骤就是把获取的setValueSelected 的 数据放到attrvalue里面,
        //奇怪的是为什么要这样放,前端就是这样传的吗?? 实际上是占个位置,使用了attr这个地方的位置

        this.setAttrValue(StringUtils.join(valueSelected, ","));
        //  stringUtils 这个是lang3包的方法, 就是以 ,分隔list 并汇成一个string
    }


}
