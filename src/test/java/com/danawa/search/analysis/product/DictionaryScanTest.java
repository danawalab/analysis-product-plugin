package com.danawa.search.analysis.product;

import com.danawa.search.analysis.dict.TagProbDictionary;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class DictionaryScanTest {

    @Test
    public void test1() throws IOException {
        //fastcat 플젝 C:\Projects\fastcat\analyzer-product\dictionary\binary\product.dict
        //fastcat 운영 C:\dev\product-fastcat.dict\product.dict
        // ES 운영 : C:\dev\elasticsearch-7.8.1\config\dict\product.dict
        // 컴파일 : C:\Projects\analysis-product\system.dict

        File file1 = new File("C:\\dev\\old\\product.dict");
//        File file1 = new File("C:\\dev\\elasticsearch-7.8.1\\config\\dict\\product.dict");
        File file2 = new File("C:\\dev\\new\\product.dict");
        TagProbDictionary dict1 = new TagProbDictionary(file1, true);
        TagProbDictionary dict2 = new TagProbDictionary(file2, true);

        BufferedWriter bw =  new BufferedWriter(new FileWriter("04.N.MIN.txt"));

        System.out.println("dict1: " + dict1.size());
        System.out.println("dict2: " + dict2.size());
        AtomicInteger cnt = new AtomicInteger();
        dict1.getUnmodifiableDictionary().keySet().forEach(k -> {
            if(dict2.find(k) != null) {
                //FIND!
//                System.out.println("FOUND[" + k + "]");
            } else {
                //NOT FOUND!
                try {
                    if(k.length() < 8) {
                        bw.write(k.toString());
                        bw.newLine();
                        cnt.getAndIncrement();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
               // System.out.println("CANNOT FIND [" + k + "] @ dict2");
            }
        });
        System.out.println("cnt : " + cnt);
        bw.close();

        System.out.println("done!");
    }
}
