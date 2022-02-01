package com.atguigu.gmall.pms;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**********************************************************
 日期:2021-12-23
 作者:刘刚
 描 述:
 ***********************************************************/
public class test_time {
    public static void main(String[] args) {
        // TODO Auto-generated method stub
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");//Date指定格式：yyyy-MM-dd HH:mm:ss:SSS
        Date date = new Date();//创建一个date对象保存当前时间
        System.out.println(date);//默认的是当前的时间
        String dateStr = simpleDateFormat.format(date);//format()方法将Date转换成指定格式的String
        System.out.println(dateStr);//2018-08-24 15:37:47:033

        String string = "2018-8-24 12:50:20:545";
        Date date2 = null;//调用parse()方法时 注意 传入的格式必须符合simpleDateFormat对象的格式，即"yyyy-MM-dd HH:mm:ss:SSS" 否则会报错！！
        try {
            date2 = simpleDateFormat.parse(string);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        System.out.println(date2);//Fri Aug 24 12:50:20 CST 2018
    }
}
