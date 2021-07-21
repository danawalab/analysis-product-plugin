package com.danawa.search.analysis.product;

import com.danawa.search.analysis.dict.TagProbDictionary;
import org.junit.Test;

import java.io.File;

public class DictionaryScanTest {

    @Test
    public void test1() {
        //fastcat 플젝 C:\Projects\fastcat\analyzer-product\dictionary\binary\product.dict
        //fastcat 운영 C:\dev\product-fastcat.dict\product.dict
        // ES 운영 : C:\dev\elasticsearch-7.8.1\config\dict\product.dict
        // 컴파일 : C:\Projects\analysis-product\system.dict
        File file1 = new File("C:\\dev\\product-fastcat.dict");
//        File file1 = new File("C:\\dev\\elasticsearch-7.8.1\\config\\dict\\product.dict");
        File file2 = new File("C:\\Projects\\fastcat\\analyzer-product\\dictionary\\binary\\product.dict");
        TagProbDictionary dict1 = new TagProbDictionary(file1, true);
        TagProbDictionary dict2 = new TagProbDictionary(file2, true);
        System.out.println("dict1: " + dict1.size());
        System.out.println("dict2: " + dict2.size());
        dict1.getUnmodifiableDictionary().keySet().forEach(k -> {
            if(dict2.find(k) != null) {
                //FIND!
//                System.out.println("FOUND[" + k + "]");
            } else {
                //NOT FOUND!
                System.out.println("CANNOT FIND [" + k + "] @ dict2");
            }
        });

        System.out.println("done!");
    }
}
